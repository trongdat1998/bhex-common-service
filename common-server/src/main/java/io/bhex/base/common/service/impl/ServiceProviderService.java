package io.bhex.base.common.service.impl;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.github.qcloudsms.SmsSingleSender;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.config.AwsEmailProperties;
import io.bhex.base.common.config.EmailConfig;
import io.bhex.base.common.config.SendCloudSmsProperties;
import io.bhex.base.common.entity.SpAccountInfo;
import io.bhex.base.common.mapper.SpAccountInfoMapper;
import io.bhex.base.common.util.ChannelConstant;
import io.bhex.base.common.util.Combo2;
import io.bhex.base.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Deprecated
public class ServiceProviderService {
    @Autowired
    private SpAccountInfoMapper spAccountInfoMapper;

//    private static Map<Long, SmsSingleSender> tencentSmsSenderMap = new HashMap<>();

    private static Map<Long, SendCloudSmsProperties> sendCloudSmsMap = new HashMap<>();

//    public SmsSingleSender getTencentSmsSender(long orgId) {
//        if (tencentSmsSenderMap.containsKey(orgId)) {
//            return tencentSmsSenderMap.get(orgId);
//        } else {
//            //如果券商没有配置就走公用的
//            return tencentSmsSenderMap.get(0);
//        }
//    }

    public SendCloudSmsProperties getSendCloudSmsProperties(long orgId) {
        return sendCloudSmsMap.get(orgId);
    }


//    @PostConstruct
//    public void initTencentSmsSender(){
//        List<SpAccountInfo> accountInfos = spAccountInfoMapper
//                .querySpInfos(ChannelConstant.TENCENT, ChannelConstant.SpType.SMS.toString());
//
//        Map<Long, SmsSingleSender> tmp = new HashMap<>();
//
//        for (SpAccountInfo spAccount : accountInfos) {
//            log.info("info:{}", spAccount);
//            long orgId = spAccount.getOrgId();
//            if (spAccount.getEnable() == 0) {
//                log.info("{} {} spAccountId:{} not available", spAccount.getOrgId(), spAccount.getOrgAlias(), spAccount.getId());
//                continue;
//            } else {
//                tmp.putIfAbsent(orgId,
//                        new SmsSingleSender(Integer.parseInt(spAccount.getAccessKeyId()), spAccount.getSecretKey()));
//            }
//            tencentSmsSenderMap = tmp;
//        }
//        log.info("tc:{}", tencentSmsSenderMap);
//    }


    @PostConstruct
    public void initSendCloudSmsSender() {
        List<SpAccountInfo> accountInfos = spAccountInfoMapper
                .querySpInfos(ChannelConstant.SEND_CLOUD, ChannelConstant.SpType.SMS.toString());

        Map<Long, SendCloudSmsProperties> tmp = new HashMap<>();

        for (SpAccountInfo spAccount : accountInfos) {
            log.info("info:{}", spAccount);
            long orgId = spAccount.getOrgId();
            if (spAccount.getEnable() == 0) {
                log.info("{} {} spAccountId:{} not available", spAccount.getOrgId(), spAccount.getOrgAlias(), spAccount.getId());
                continue;
            } else {
                String extraInfo = spAccount.getExtraInfo();
                if(StringUtils.isEmpty(extraInfo)){
                    log.error("spAccountId:{} config extraInfo error", spAccount.getId());
                    continue;
                }

                SendCloudSmsProperties item = new SendCloudSmsProperties();
                item.setApiUser(spAccount.getAccessKeyId());
                item.setApiKey(spAccount.getSecretKey());
                item.setSendApiUrl(spAccount.getRequestUrl());
                JsonObject jo = JsonUtil.defaultGson().fromJson(extraInfo, JsonElement.class).getAsJsonObject();
                item.setGeneralTemplateId(jo.get("general_template_id").getAsInt());
                item.setWebhookAppKey(jo.get("webhook_app_key").getAsString());
                tmp.putIfAbsent(orgId, item);
            }
            sendCloudSmsMap = tmp;
        }
        log.info("sendCloudSmsMap:{}", sendCloudSmsMap);
    }

    private Map<Long, Combo2<EmailConfig, AmazonSimpleEmailService>> awsEmailMap = new HashMap<>();

    public Combo2<EmailConfig, AmazonSimpleEmailService> getAwsEmailClient(long orgId) {
        if (awsEmailMap.containsKey(orgId)) {
            return awsEmailMap.get(orgId);
        }
        return awsEmailMap.get(0L);
    }

    @PostConstruct
    public void initAwsEmailSender() {
        List<SpAccountInfo> accountInfos = spAccountInfoMapper
                .querySpInfos(ChannelConstant.AWS, ChannelConstant.SpType.EMAIL.toString());

        Map<Long, Combo2<EmailConfig, AmazonSimpleEmailService>> tmp = new HashMap<>();

        for (SpAccountInfo spAccount : accountInfos) {
            log.info("info:{}", spAccount);
            long orgId = spAccount.getOrgId();
            if (spAccount.getEnable() == 0) {
                log.info("{} {} spAccountId:{} not available", spAccount.getOrgId(), spAccount.getOrgAlias(), spAccount.getId());
                continue;
            } else {
                String extraInfo = spAccount.getExtraInfo();
                if(StringUtils.isEmpty(extraInfo)){
                    log.error("spAccountId:{} config extraInfo error", spAccount.getId());
                    continue;
                }

                EmailConfig item = new EmailConfig();
                item.setAccessKeyId(spAccount.getAccessKeyId());
                item.setSecretKey(spAccount.getSecretKey());

                JsonObject jo = JsonUtil.defaultGson().fromJson(extraInfo, JsonElement.class).getAsJsonObject();
                item.setRegionName(jo.get("region_name").getAsString());
                item.setFromAddress(jo.get("from_address").getAsString());
                item.setAccount(jo.get("aws_account").getAsString());

                AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                        .withRegion(Regions.fromName(item.getRegionName()))
                        .withCredentials(new AWSCredentialsProvider(){
                            @Override
                            public AWSCredentials getCredentials() {
                                return new AWSCredentials() {
                                    @Override
                                    public String getAWSAccessKeyId() {
                                        return item.getAccessKeyId();
                                    }

                                    @Override
                                    public String getAWSSecretKey() {
                                        return item.getSecretKey();
                                    }
                                };
                            }

                            @Override
                            public void refresh() {

                            }
                        })
                        .build();

                tmp.putIfAbsent(orgId, new Combo2<>(item, client));
            }
            awsEmailMap = tmp;
        }
        log.info("awsEmailMap:{}", awsEmailMap);
    }

}
