//package io.bhex.base.common.sender;
//
//import com.amazonaws.auth.AWSCredentials;
//import com.amazonaws.auth.BasicAWSCredentials;
//import com.amazonaws.regions.Regions;
//import com.amazonaws.services.sns.AmazonSNSClient;
//import com.amazonaws.services.sns.model.MessageAttributeValue;
//import com.amazonaws.services.sns.model.PublishRequest;
//import com.amazonaws.services.sns.model.PublishResult;
//import io.bhex.base.common.config.AwsSmsProperties;
//import io.bhex.base.common.config.ContainerConfig;
//import io.bhex.base.common.service.SmsDeliveryRecordService;
//import io.bhex.base.common.util.ChannelConstant;
//import io.bhex.base.common.util.MaskUtil;
//import io.bhex.base.common.util.PrometheusUtil;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//import org.springframework.beans.BeanUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.PostConstruct;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.atomic.AtomicInteger;
//
///**
// * @Description:
// * @Date: 2018/9/29 上午10:07
// * @Author: liwei
// * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
// */
//
//@Slf4j
//@Component
//@Deprecated
//public class AwsSmsSender {
//
//    @Autowired
//    private AwsSmsProperties awsSmsProperties;
//    @Autowired
//    private SmsDeliveryRecordService smsDeliveryRecordService;
//
//    private AtomicInteger counter = new AtomicInteger(0);
//
//    private static List<AwsSmsProperties.SmsConfig> smsConfigs = new ArrayList<>();
//
//    private int clientSize = 0;
//    private static List<AmazonSNSClient> snsClients = new ArrayList<>();
//    private Regions[] regions = new Regions[]{
//            Regions.US_EAST_1,
//            Regions.US_WEST_2,
//            Regions.AP_NORTHEAST_1,
//            Regions.AP_SOUTHEAST_1,
//            Regions.AP_SOUTHEAST_2,
//            Regions.EU_WEST_1
//    };
//
//
//    @PostConstruct
//    public void initSnsClient(){
//        String container = ContainerConfig.getContainer();
//        List<AwsSmsProperties.SmsConfig> configs = awsSmsProperties.getConfigs(container);
//        log.info("container:{} configs:{}", container, configs.size());
//        clientSize = configs.size()*regions.length;
//        for(AwsSmsProperties.SmsConfig config : configs){
//            AWSCredentials awsCredentials = new BasicAWSCredentials(config.getAccessKeyId(), config.getSecretKey());
//            for(Regions region : regions){
//
//                AmazonSNSClient snsClient = new AmazonSNSClient(awsCredentials).withRegion(region);
//                snsClients.add(snsClient);
//                log.info("account:{} region:{}", config.getAccount(), region.getDescription());
//
//                AwsSmsProperties.SmsConfig configWithRegion = new AwsSmsProperties.SmsConfig();
//                BeanUtils.copyProperties(config, configWithRegion);
//                configWithRegion.setRegionName(region.getDescription());
//                smsConfigs.add(configWithRegion);
//            }
//        }
//    }
//
//
//
//    public boolean send(Long orgId, String areaCode, String telephone, String sign, String content, String bizType) {
//        try {
//            String message = sign + " " + content;
//            message = StringUtils.rightPad(message, 70);//右边补空格，防止aws在短信内容后面缀上 [aws]
//            sendSMSMessage(orgId, message, areaCode, telephone, bizType);
//            return true;
//        } catch (Exception e) {
//            log.error("sendSms error", e);
//        }
//        return false;
//    }
//
//    private void sendSMSMessage(Long orgId, String message,
//                                      String areaCode, String telephone, String bizType) {
//
//        Map<String, MessageAttributeValue> smsAttributes = new HashMap<>();
//        smsAttributes.put("AWS.SNS.SMS.SMSType", new MessageAttributeValue()
//                .withStringValue("Transactional") //Sets the type to Transactional.
//                .withDataType("String"));
//
//        String phoneNumber = "+" + areaCode + telephone;
//        int index = counter.getAndIncrement()%clientSize;
//        AmazonSNSClient snsClient =  snsClients.get(index);
//        PublishResult result = snsClient
//                .publish(new PublishRequest()
//                .withMessage(message)
//                .withPhoneNumber(phoneNumber)
//                .withMessageAttributes(smsAttributes));
//
//        AwsSmsProperties.SmsConfig config = smsConfigs.get(index);
//        log.info("sendSms account:{} region:{} orgId:{} {}, content:{} {}",config.getAccount(), config.getRegionName(),
//                orgId, MaskUtil.maskMobile(areaCode, telephone), MaskUtil.maskValidateCode(message), result);
//        smsDeliveryRecordService.insertAwsSmsDeliveryRecord(orgId, result.getMessageId(), areaCode,
//                telephone, config.getRegionName(),bizType,"");
//
//        PrometheusUtil.smsSendCounter(ChannelConstant.AWS, 200);
//        PrometheusUtil.smsDeliveryCounter(ChannelConstant.AWS, 200);
//    }
//
//}
