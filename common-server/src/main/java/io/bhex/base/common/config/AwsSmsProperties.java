package io.bhex.base.common.config;

import lombok.Data;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.stream.Collectors;


@ConfigurationProperties(prefix = "aws-sms")
public class AwsSmsProperties {

    @Setter
    private List<SmsConfig> smsConfigs;

    public List<SmsConfig> getConfigs(String container){
        return smsConfigs.stream()
                .filter(c->c.getContainer().toUpperCase().equals(container.toUpperCase()))
                .collect(Collectors.toList());
    }

    @Data
    public static class SmsConfig{
        private String accessKeyId;
        private String secretKey;
        private String regionName;
        private String container;
        private String account;
    }
}
