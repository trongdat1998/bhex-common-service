package io.bhex.base.common.config;

import lombok.Data;

@Data
public class EmailConfig{
    private String fromAddress;
    private String accessKeyId;
    private String secretKey;
    private String regionName;
    private Boolean defaultConfig;
    private String account;
    private String container;
}
