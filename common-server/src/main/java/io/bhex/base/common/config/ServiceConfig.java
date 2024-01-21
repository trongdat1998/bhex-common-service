/*
 ************************************
 * @项目名称: bhex-common-service
 * @文件名称: ServiceConfig
 * @Date 2018/08/14
 * @Author will.zhao@bhex.io
 * @Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
 **************************************
 */
package io.bhex.base.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Slf4j
@Order(value = 1)
public class ServiceConfig {

//    @Bean
//    public SmsSender smsSender() {
//        return new SmsSender();
//    }

    @Bean
    public AwsEmailProperties awsEmailProperties() {
        return new AwsEmailProperties();
    }

    @Bean
    public AwsSmsProperties awsSmsProperties() {
        return new AwsSmsProperties();
    }

    @Bean
    public SendCloudEmailProperties sendCloudEmailProperties() {
        return new SendCloudEmailProperties();
    }

    @Bean
    public SendCloudSmsProperties sendCloudSmsProperties() {
        return new SendCloudSmsProperties();
    }

}
