package io.bhex.base.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "aws-emails")
public class AwsEmailProperties {

    private List<EmailConfig> emailConfigs;

    public EmailConfig getDefaultConfig(String container){
        return emailConfigs.stream()
                .filter(c->c.getDefaultConfig())
                .filter(c->c.getContainer().toUpperCase().equals(container.toUpperCase()))
                .findFirst()
                .get();
    }
}
