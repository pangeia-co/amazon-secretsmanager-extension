package co.pangeia.amazon.secretsmanager.extension.deployment;

import co.pangeia.secretmanager.*;
import io.quarkus.amazon.common.deployment.*;
import io.quarkus.amazon.common.runtime.*;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClientBuilder;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;

import java.util.List;
import java.util.Optional;

class AmazonSecretsManagerExtensionProcessor extends AbstractAmazonServiceProcessor {

    SecretsManagerBuildTimeConfig buildTimeConfig;

    private static final String FEATURE = "amazon-secretsmanager-extension";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @Override
    protected String configName() {
        return "secretsmanager";
    }

    @Override
    protected DotName syncClientName() {
        return DotName.createSimple(SecretsManagerClient.class.getName());
    }

    @Override
    protected DotName asyncClientName() {
        return DotName.createSimple(SecretsManagerAsyncClient.class.getName());
    }

    @Override
    protected String builtinInterceptorsPath() {
        return "software/amazon/awssdk/services/secretsmanager/execution.interceptors";
    }

    @BuildStep
    AdditionalBeanBuildItem producer() {
        return AdditionalBeanBuildItem.unremovableOf(SecretsManagerClientProducer.class);
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> buildProducer) {
        buildProducer.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(AWSSecretsManagerProcessor.class)
                .addBeanClass(AWSSecretsManagerReader.class)
                .build());
    }

    @BuildStep
    void setup(BeanRegistrationPhaseBuildItem beanRegistrationPhase,
               BuildProducer<ExtensionSslNativeSupportBuildItem> extensionSslNativeSupport,
               BuildProducer<FeatureBuildItem> feature,
               BuildProducer<AmazonClientInterceptorsPathBuildItem> interceptors,
               BuildProducer<AmazonClientBuildItem> clientProducer) {

        setupExtension(beanRegistrationPhase, extensionSslNativeSupport, feature, interceptors, clientProducer,
                buildTimeConfig.sdk, buildTimeConfig.syncClient);
    }

    @BuildStep(onlyIf = AmazonHttpClients.IsAmazonApacheHttpServicePresent.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void setupApacheSyncTransport(List<AmazonClientBuildItem> amazonClients, SecretsManagerRecorder recorder,
                                  AmazonClientApacheTransportRecorder transportRecorder,
                                  SecretsManagerConfig runtimeConfig, BuildProducer<AmazonClientSyncTransportBuildItem> syncTransports) {

        createApacheSyncTransportBuilder(amazonClients,
                transportRecorder,
                buildTimeConfig.syncClient,
                recorder.getSyncConfig(runtimeConfig),
                syncTransports);
    }

    @BuildStep(onlyIf = AmazonHttpClients.IsAmazonUrlConnectionHttpServicePresent.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void setupUrlConnectionSyncTransport(List<AmazonClientBuildItem> amazonClients, SecretsManagerRecorder recorder,
                                         AmazonClientUrlConnectionTransportRecorder transportRecorder,
                                         SecretsManagerConfig runtimeConfig, BuildProducer<AmazonClientSyncTransportBuildItem> syncTransports) {

        createUrlConnectionSyncTransportBuilder(amazonClients,
                transportRecorder,
                buildTimeConfig.syncClient,
                recorder.getSyncConfig(runtimeConfig),
                syncTransports);
    }

    @BuildStep(onlyIf = AmazonHttpClients.IsAmazonNettyHttpServicePresent.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void setupNettyAsyncTransport(List<AmazonClientBuildItem> amazonClients, SecretsManagerRecorder recorder,
                                  AmazonClientNettyTransportRecorder transportRecorder,
                                  SecretsManagerConfig runtimeConfig, BuildProducer<AmazonClientAsyncTransportBuildItem> asyncTransports) {

        createNettyAsyncTransportBuilder(amazonClients,
                transportRecorder,
                recorder.getAsyncConfig(runtimeConfig),
                asyncTransports);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void createClientBuilders(SecretsManagerRecorder recorder,
                              AmazonClientRecorder commonRecorder,
                              SecretsManagerConfig runtimeConfig,
                              List<AmazonClientSyncTransportBuildItem> syncTransports,
                              List<AmazonClientAsyncTransportBuildItem> asyncTransports,
                              BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

        createClientBuilders(commonRecorder,
                recorder.getAwsConfig(runtimeConfig),
                recorder.getSdkConfig(runtimeConfig),
                buildTimeConfig.sdk,
                syncTransports,
                asyncTransports,
                SecretsManagerClientBuilder.class,
                (syncTransport) -> recorder.createSyncBuilder(runtimeConfig, syncTransport),
                SecretsManagerAsyncClientBuilder.class,
                (asyncTransport) -> recorder.createAsyncBuilder(runtimeConfig, asyncTransport),
                syntheticBeans);
    }

    @Override
    protected void setupExtension(BeanRegistrationPhaseBuildItem beanRegistrationPhase, BuildProducer<ExtensionSslNativeSupportBuildItem> extensionSslNativeSupport, BuildProducer<FeatureBuildItem> feature, BuildProducer<AmazonClientInterceptorsPathBuildItem> interceptors, BuildProducer<AmazonClientBuildItem> clientProducer, SdkBuildTimeConfig buildTimeSdkConfig, SyncHttpClientBuildTimeConfig buildTimeSyncConfig) {

        feature.produce(feature());

        extensionSslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(FEATURE));
        interceptors.produce(new AmazonClientInterceptorsPathBuildItem(this.builtinInterceptorsPath()));

        Optional<DotName> syncClassName = Optional.empty();
        Optional<DotName> asyncClassName = Optional.empty();

        for (InjectionPointInfo o : beanRegistrationPhase.getContext().get(BuildExtension.Key.INJECTION_POINTS)) {
            Type injectedType = getInjectedType(o);
            if (this.syncClientName().equals(injectedType.name())) {
                syncClassName = Optional.of(this.syncClientName());
            }

            if (this.asyncClientName().equals(injectedType.name())) {
                asyncClassName = Optional.of(this.asyncClientName());
            }
        }

        if (syncClassName.isPresent() || asyncClassName.isPresent()) {
            clientProducer.produce(new AmazonClientBuildItem(syncClassName, asyncClassName, this.configName(), buildTimeSdkConfig, buildTimeSyncConfig));
        }

    }

    private Type getInjectedType(InjectionPointInfo injectionPoint) {
        Type requiredType = injectionPoint.getRequiredType();
        Type injectedType = requiredType;
        if (DotNames.INSTANCE.equals(requiredType.name()) && requiredType instanceof ParameterizedType) {
            injectedType = requiredType.asParameterizedType().arguments().get(0);
        }

        return injectedType;
    }

    @Override
    protected Feature amazonServiceClientName() {
        return null;
    }

}
