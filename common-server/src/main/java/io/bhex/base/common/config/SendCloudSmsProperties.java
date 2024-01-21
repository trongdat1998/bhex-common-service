package io.bhex.base.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * @Description:
 * @Date: 2018/12/2 下午5:30
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
@Data
@ConfigurationProperties(prefix = "sendcloud-sms")
public class SendCloudSmsProperties {
    private String apiUser;
    private String apiKey;
    private String sendApiUrl;
    private String webhookAppKey;
    private Integer generalTemplateId;
}
