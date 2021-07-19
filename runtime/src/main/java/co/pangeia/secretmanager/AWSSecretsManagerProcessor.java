package co.pangeia.secretmanager;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

@ApplicationScoped
public class AWSSecretsManagerProcessor {

    @Inject
    AWSSecretsManagerReader reader;

    @Produces
    @AWSSecretsManager("")
    String getStringValue(InjectionPoint ip) throws Exception {
        final String secretId = ip.getAnnotated().getAnnotation(AWSSecretsManager.class).value();
        return reader.getSecret(secretId);
    }

}