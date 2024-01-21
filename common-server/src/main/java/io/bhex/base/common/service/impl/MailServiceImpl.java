///*
// ************************************
// * @项目名称: bhex-common-service
// * @文件名称: MailServiceImpl
// * @Date 2018/08/14
// * @Author will.zhao@bhex.io
// * @Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
// * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
// **************************************
// */
//package io.bhex.base.common.service.impl;
//
//import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
//import com.google.common.cache.Cache;
//import com.google.common.cache.CacheBuilder;
//import io.bhex.base.common.MailReply;
//import io.bhex.base.common.MailRequest;
//import io.bhex.base.common.SendMailRequest;
//import io.bhex.base.common.config.AwsEmailProperties;
//import io.bhex.base.common.config.ContainerConfig;
//import io.bhex.base.common.config.EmailConfig;
//import io.bhex.base.common.sender.AwsEmailSender;
//import io.bhex.base.common.sender.EmailSender;
//import io.bhex.base.common.sender.SendCloudEmailSender;
//import io.bhex.base.common.service.MailService;
//import io.bhex.base.common.service.RouterConfigService;
//import io.bhex.base.common.util.ChannelConstant;
//import io.bhex.base.common.util.Combo2;
//import io.bhex.base.quote.FXRate;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//import org.omg.CORBA.PRIVATE_MEMBER;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.Resource;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.concurrent.TimeUnit;
//
//@Slf4j
//@Service
//@Deprecated
//public class MailServiceImpl implements MailService {
//
//    @Resource
//    private EmailSender sender;
//    @Resource
//    private AwsEmailSender awsEmailSender;
//    @Resource
//    private SendCloudEmailSender sendCloudEmailSender;
//    @Resource
//    private RouterConfigService routerConfigService;
//    @Resource
//    private ServiceProviderService serviceProviderService;
//
//    private static final Map<Long, String> contentMap = new HashMap<>();
//
//   // private static final Map<String, SendMailRequest.Channel> sendChannelMap = new HashMap<>();
//
//    static {
//        contentMap.put(200000L, "尊敬的用户您好：您的验证码为%s，验证码有效期10分钟，请确认是您本人操作。");
//        contentMap.put(200001L, "Your code is %s, valid for 10 minutes, please confirm it is your action.");
//
////        sendChannelMap.put("163.com", SendMailRequest.Channel.SEND_CLOUD);
////        sendChannelMap.put("126.com", SendMailRequest.Channel.SEND_CLOUD);
////        sendChannelMap.put("yeah.net", SendMailRequest.Channel.SEND_CLOUD);
////        sendChannelMap.put("gmail.com", SendMailRequest.Channel.AWS);
////        sendChannelMap.put("icloud.com", SendMailRequest.Channel.AWS);
//    }
//
//    private static final String subject = "BHex邮件消息";
//
//
//
//    @Override
//    public MailReply send(MailRequest request) {
//        long templateId = request.getTemplateId();
//        String[] params = request.getParamsList().toArray(new String[request.getParamsList().size()]);
//
//        String to = request.getMail();
//        String content = getMailContent(templateId, params);
//        String sign = request.getSign();
//        boolean success = sender.send(request.getOrgId(), to, request.getNickname(),
//            StringUtils.isBlank(sign) ? MAIL_SIGN_DEFAULT : sign, subject, content, true);
//        return MailReply.newBuilder().setSuccess(success).build();
//    }
//
//    private String getMailContent(long templateId, String[] params) {
//        if (contentMap.get(templateId) == null) {
//            return "";
//        }
//        return String.format(contentMap.get(templateId), params);
//    }
//
//    @Override
//    public MailReply send(SendMailRequest request) {
//        String to = request.getMail();
//        String content = request.getContent();
//        String sign = request.getSign();
//        String subject = request.getSubject();
//        Long orgId = request.getOrgId();
//        boolean success;
//        SendMailRequest.Channel sendChannel;
//
//        boolean forbidAws = routerConfigService.forbidChannel(orgId, false, ChannelConstant.AWS);
//        boolean forbidSendCloud = routerConfigService.forbidChannel(orgId, false, ChannelConstant.SEND_CLOUD);
//        if (forbidAws && forbidSendCloud) {
//            log.error("ALERT: there is not any channels for send email!!!");
//            return MailReply.newBuilder().setSuccess(false).build();
//        }
//
//        if (request.getMessageTypeValue() == SendMailRequest.MessageType.MARKET_VALUE) {
//            //营销邮件只能走aws
//            sendChannel = SendMailRequest.Channel.AWS;
//        } else if (forbidAws) {
//            //AWS禁用走sendcloud
//            sendChannel = SendMailRequest.Channel.SEND_CLOUD;
//        } else if (forbidSendCloud) {
//            sendChannel = SendMailRequest.Channel.AWS;
//        } else {
//            int retryMod = request.getRetryTimes()%2;
//            //根据邮箱后缀匹配发送渠道
//            SendMailRequest.Channel prioritySendChannel = routerConfigService.getPriorityChannelForEmail(orgId, to);
//            if (retryMod == 0 && prioritySendChannel != null) {
//                sendChannel = prioritySendChannel;
//            }  else {
//                sendChannel = request.getChannel();
//                //retry hard code  0用自己传过来的渠道 1用备用通道
//                if (retryMod == 1 && sendChannel.getNumber() == SendMailRequest.Channel.SEND_CLOUD_VALUE) {
//                    sendChannel = SendMailRequest.Channel.AWS;
//                } else if (retryMod == 1 && sendChannel.getNumber() == SendMailRequest.Channel.AWS_VALUE) {
//                    sendChannel = SendMailRequest.Channel.SEND_CLOUD;
//                }
//            }
//        }
//
//        Combo2<EmailConfig, AmazonSimpleEmailService> combo2 = serviceProviderService.getAwsEmailClient(orgId);
////        if (orgId == 21L && !sign.toLowerCase().contains("bhex")) {
////            //hard code ,orgid=21为平台发送的邮件
////            //平台调用的是common-server.bluehelix，bhop邮件只能使用aws发送
////            success = awsEmailSender.send(combo2, orgId, sign, to, subject, content, request.getNickname(), request.getBizType());
////        } else
//
//        if (sendChannel.getNumber() == SendMailRequest.Channel.SEND_CLOUD_VALUE) {
//            success = sendCloudEmailSender.send(orgId, sign, to, subject, content, request.getBizType());
//        } else {
//            //默认用aws发送
//            success = awsEmailSender.send(combo2, orgId, sign, to, subject, content, request.getNickname(), request.getBizType());
//        }
//        return MailReply.newBuilder().setSuccess(success).build();
//    }
//
//
//
////    public MailReply send(SendMailRequest request) {
////        String to = request.getMail();
////        String content = request.getContent();
////        String sign = request.getSign();
////        String subject = request.getSubject();
////        boolean success = sender.send(to, request.getNickname(), sign, subject, content, true);
////        return MailReply.newBuilder().setSuccess(success).build();
////    }
//}
