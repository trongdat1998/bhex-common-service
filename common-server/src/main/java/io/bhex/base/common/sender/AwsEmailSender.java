//package io.bhex.base.common.sender;
//
//import com.amazonaws.auth.AWSCredentials;
//import com.amazonaws.auth.AWSCredentialsProvider;
//import com.amazonaws.regions.Regions;
//import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
//import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
//import com.amazonaws.services.simpleemail.model.*;
//import io.bhex.base.common.config.AwsEmailProperties;
//import io.bhex.base.common.config.ContainerConfig;
//import io.bhex.base.common.config.EmailConfig;
//import io.bhex.base.common.service.EmailDeliveryRecordService;
//import io.bhex.base.common.util.ChannelConstant;
//import io.bhex.base.common.util.Combo2;
//import io.bhex.base.common.util.MaskUtil;
//import io.bhex.base.common.util.PrometheusUtil;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.PostConstruct;
//import javax.mail.internet.MimeUtility;
//
//@Slf4j
//@Service
//@Deprecated
//public class AwsEmailSender {
//
////    private static AmazonSimpleEmailService client;
////    private static EmailConfig emailConfig;
////    @Autowired
////    private AwsEmailProperties awsEmailProperties;
//    @Autowired
//    private EmailDeliveryRecordService emailDeliveryRecordService;
//
////    @PostConstruct
////    public void init(){
////        String container = ContainerConfig.getContainer();
////        emailConfig = awsEmailProperties.getDefaultConfig(container);
////        log.info("container:{} from:{} region:{}", container, emailConfig.getFromAddress(), emailConfig.getRegionName());
////        client = AmazonSimpleEmailServiceClientBuilder.standard()
////                        .withRegion(Regions.fromName(emailConfig.getRegionName()))
////                        .withCredentials(new AWSCredentialsProvider(){
////                            @Override
////                            public AWSCredentials getCredentials() {
////                                return new AWSCredentials() {
////                                    @Override
////                                    public String getAWSAccessKeyId() {
////                                        return emailConfig.getAccessKeyId();
////                                    }
////
////                                    @Override
////                                    public String getAWSSecretKey() {
////                                        return emailConfig.getSecretKey();
////                                    }
////                                };
////                            }
////
////                            @Override
////                            public void refresh() {
////
////                            }
////                        })
////                        .build();
////    }
//
//    public boolean send(Combo2<EmailConfig, AmazonSimpleEmailService> combo2, Long orgId, String sign, String to, String subject, String content, String nickname, String bizType) {
//        try{
//            if(StringUtils.isNoneEmpty(sign)){
//                sign = MimeUtility.encodeText(sign, "utf-8", "Q");
//            }
//            String from = sign + "<" + combo2.getV1().getFromAddress() + ">";
//            SendEmailRequest request = new SendEmailRequest()
//                    .withDestination(
//                            new Destination().withToAddresses(to))
//                    .withMessage(new Message()
//                            .withBody(new Body()
//                                    .withHtml(new Content()
//                                            .withCharset("UTF-8").withData(content)))
//                            .withSubject(new Content()
//                                    .withCharset("UTF-8").withData(subject)))
//                    .withSource(from)
//                    // Comment or remove the next line if you are not using a
//                    // configuration set
//                    //.withConfigurationSetName("BrokerAdmin")
//                    ;
//            SendEmailResult result = combo2.getV2().sendEmail(request);
//            log.info("succ sendAwsMail orgId:{} to:{} content:{} result:{}", orgId, MaskUtil.maskEmail(to),
//                    MaskUtil.maskValidateCode(content), result);
//
//            String sid = result.getMessageId();
//            emailDeliveryRecordService.insertAwsEmailDeliveryRecord(orgId, sid, to, bizType, "");
//
//            PrometheusUtil.emailSendCounter(ChannelConstant.AWS, 200);
//            PrometheusUtil.emailDeliveryCounter(ChannelConstant.AWS, 200);
//
//        } catch (Exception e) {
//            log.warn("fail send simple mail {} {} content:{}", orgId, MaskUtil.maskEmail(to),
//                    MaskUtil.maskValidateCode(content), e);
//            return false;
//        }
//        return false;
//    }
//
//}
