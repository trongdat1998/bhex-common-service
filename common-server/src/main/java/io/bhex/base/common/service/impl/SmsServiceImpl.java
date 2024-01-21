///*
// ************************************
// * @项目名称: bhex-common-service
// * @文件名称: SmsServiceImpl
// * @Date 2018/08/14
// * @Author will.zhao@bhex.io
// * @Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
// * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
// **************************************
// */
//package io.bhex.base.common.service.impl;
//
//import io.bhex.base.common.*;
//import io.bhex.base.common.config.SendCloudSmsProperties;
//import io.bhex.base.common.entity.SmsTmplMapping;
//import io.bhex.base.common.mapper.SmsTmplMappingMapper;
//import io.bhex.base.common.sender.AwsSmsSender;
//import io.bhex.base.common.sender.SendCloudSmsSender;
//import io.bhex.base.common.sender.SmsSender;
//import io.bhex.base.common.service.RouterConfigService;
//import io.bhex.base.common.service.SmsService;
//import io.bhex.base.common.util.ChannelConstant;
//import io.bhex.base.common.util.MaskUtil;
//import io.bhex.base.env.BhexEnv;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//import org.springframework.util.CollectionUtils;
//
//import javax.annotation.Resource;
//import java.text.MessageFormat;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//@Slf4j
//@Service
//@Deprecated
//public class SmsServiceImpl implements SmsService {
//
//    @Resource
//    private SmsSender smsSender;
//
//    @Resource
//    private AwsSmsSender awsSmsSender;
//
//
//    @Resource
//    private SendCloudSmsSender sendCloudSmsSender;
//
//    @Resource
//    private RouterConfigService routerConfigService;
//
//    @Resource
//    private ServiceProviderService serviceProviderService;
//
//
//
//    private static Map<String,SmsTmplMapping> tmplMappingMap = new HashMap<>();
//
//    private static Map<String, String> bhopValidateCodeMap = new HashMap<>();
//
//    //备选通道 暂时只支持bhex通过tencent发送的渠道
//    //重发规则: 0-从tencent发送
//    //         1-sendcloud
//    //         2-aws
//    @Override
//    public SmsReply send(SmsRequest request) {
//        int retryMod = request.getRetryTimes()%3;
//        String sendChannel = null;
//
//        Telephone telephone = request.getTelephone();
//        int templateId = (int)request.getTemplateId();
//        String sign = request.getSign();
//        String[] params = request.getParamsList().toArray(new String[request.getParamsList().size()]);
//        SmsTmplMapping mapping = getBackupChannel(request.getOrgId(), ChannelConstant.TENCENT, request.getTemplateId() + "");
//
//        boolean forbidAws = routerConfigService.forbidChannel(request.getOrgId(), true, ChannelConstant.AWS);
//        boolean forbidSendCloud = routerConfigService.forbidChannel(request.getOrgId(), true, ChannelConstant.SEND_CLOUD);
//        boolean forbidTencent = routerConfigService.forbidChannel(request.getOrgId(), true, ChannelConstant.TENCENT);
//        if (!forbidTencent) {
//            if (retryMod == 0) {
//                sendChannel = ChannelConstant.TENCENT;
//            } else if (mapping != null && retryMod == 1){
//                if (telephone.getNationCode().equals("86")) {
//                    sendChannel = mapping.getTargetChannel();
//                } else {
//                    sendChannel = ChannelConstant.AWS;
//                }
//            } else if (mapping != null && retryMod == 2) {
//                sendChannel = ChannelConstant.AWS;
//            }
//        } else {
//            if (mapping != null) {
//                if (telephone.getNationCode().equals("86") && mapping.getTargetChannel().equals(ChannelConstant.SEND_CLOUD) && !forbidSendCloud) {
//                    sendChannel = ChannelConstant.SEND_CLOUD;
//                } else if (!forbidAws){
//                    sendChannel = ChannelConstant.AWS;
//                }
//            }
//        }
//
//        if (sendChannel == null){
//            log.error("ALERT: there is not any channels for send sms!!! request:{}", request);
//            return SmsReply.newBuilder().setSuccess(false).build();
//        }
//
//        boolean success;
//        if(sendChannel.equals(ChannelConstant.AWS)) {
//            String[] awsParams = new String[3];
//            awsParams[0] = "";
//            if( params != null && params.length > 0){
//                for (int i = 0; i < params.length; i++){
//                    awsParams[i+1] = params[i];
//                }
//            }
//            String content = MessageFormat.format("{0}" + mapping.getTmplContent(),  awsParams);
//            success = awsSmsSender.send(request.getOrgId(), telephone.getNationCode(), telephone.getMobile(),
//                    "【" + sign + "】", content, request.getBizType());
//        } else if (sendChannel.equals(ChannelConstant.SEND_CLOUD)) {
//            SendCloudSmsProperties properties = serviceProviderService.getSendCloudSmsProperties(request.getOrgId());
//            success = sendCloudSmsSender.sendCnSms(properties, request.getOrgId(), mapping.getTargetTmplId(), params, telephone.getMobile(), request.getBizType());
//        } else {
//            success = smsSender.send(request.getOrgId(), telephone.getNationCode(), telephone.getMobile(),
//                    StringUtils.isBlank(sign) ? SMS_SIGN_DEFAULT : sign, templateId, params, request.getBizType());
//        }
//
//
//        return SmsReply.newBuilder().setSuccess(success).build();
//    }
//
//    @Override
//    public SmsReply sendSms(SendSmsRequest request) {
//        Telephone telephone = request.getTelephone();
//        String sign = request.getSign();
//        long orgId = request.getOrgId();
//
//        boolean forbidAws = routerConfigService.forbidChannel(orgId, true, ChannelConstant.AWS);
//        boolean forbidSendCloud = routerConfigService.forbidChannel(orgId, true, ChannelConstant.SEND_CLOUD);
//
//        BhexEnv bhexEnv = new BhexEnv();
//        boolean success = false;
//        if (request.getMessageTypeValue() == SendSmsRequest.MessageType.MARKET_VALUE) {
//            //营销 只能走aws
//            success = awsSmsSender.send(request.getOrgId(), telephone.getNationCode(), telephone.getMobile(),
//                    sign, request.getContent(), request.getBizType());
//        } else if ((bhexEnv.isBHEX() || bhexEnv.isUS()) && !bhexEnv.isBhop()) {
//            //bhex环境 国内走sendcloud 国外走aws
//            SendCloudSmsProperties properties = serviceProviderService.getSendCloudSmsProperties(orgId);
//            if (properties != null && telephone.getNationCode().equals("86") && !forbidSendCloud && sign.toUpperCase().contains("BHEX")) {
//                success = sendCloudSmsSender.sendGeneralSms(properties, orgId, telephone.getMobile(), request.getContent(), request.getBizType());
//            } else if (!forbidAws) {
//                success = awsSmsSender.send(orgId, telephone.getNationCode(), telephone.getMobile(),
//                        sign, request.getContent(), request.getBizType());
//            }
//        } else {
//            // bhop 环境，如果券商自己申请了sendcloud发sc，否则发aws
//            SendCloudSmsProperties properties = serviceProviderService.getSendCloudSmsProperties(orgId);
//            if (!forbidSendCloud && properties != null && telephone.getNationCode().equals("86")) {
//                String templateId = getTargetValidateCodeTmplId(orgId, ChannelConstant.SEND_CLOUD, request.getContent());
//                String code = getValidateCode(request.getContent());
//                if (templateId != null && code != null) {
//                    //验证码发送 并且对应渠道设置了模板
//                    success = sendCloudSmsSender.sendCnSms(properties, orgId, templateId, new String[]{code}, telephone.getMobile(), request.getBizType());
//
//                } else {
//                    success = sendCloudSmsSender.sendGeneralSms(properties, request.getOrgId(), telephone.getMobile(), request.getContent(), request.getBizType());
//                }
//            } else if (!forbidAws) {
//                success = awsSmsSender.send(request.getOrgId(), telephone.getNationCode(), telephone.getMobile(),
//                        sign, request.getContent(), request.getBizType());
//            } else {
//                log.error("ALERT: there is not any channels for send sms!!!");
//            }
//        }
//
//        return SmsReply.newBuilder().setSuccess(success).build();
//    }
//
//    @Scheduled(initialDelay = 10_000, fixedRate=3600_000)
//    private void loadSmsTmplMapping(){
////        List<SmsTmplMapping> list = smsTmplMappingMapper.selectAll();
////        if(CollectionUtils.isEmpty(list)){
////            return;
////        }
////        list.stream().forEach(m -> {
////            if (m.getStatus() == 1) {
////                tmplMappingMap.put(m.getOrgId() + "-" + m.getSourceChannel() + "-" + m.getSourceTmplId(), m);
////                //tmplMappingMap.put(m.getSourceChannel() + "-" + m.getSourceTmplId(), m);
////
////                String key = m.getOrgId() + "-" + m.getTargetChannel() + "-" + m.getTmplContent()
////                        .replaceAll(" ","").replaceAll("\\{1\\}", "");
////                bhopValidateCodeMap.put(key,  m.getTargetTmplId());
////            }
////        });
// //       log.info("bhopValidateCodeMap:{}", bhopValidateCodeMap);
//    }
//
//    /**
//     * 只有bhex的短信验证码有备份
//     * @param orgId
//     * @param channel
//     * @param tmplId
//     * @return
//     */
//    private SmsTmplMapping getBackupChannel(Long orgId, String channel, String tmplId) {
//
//        String key = orgId + "-" + channel + "-" + tmplId;
//        //String key = channel + "-" + tmplId;
//
//        //String limitKey = orgId + "-" + channel + "-" + tmplId;
//        //String key = channel + "-" + tmplId;
//
//        return tmplMappingMap.get(key);
//    }
//
//    private String getTargetValidateCodeTmplId(Long orgId, String channel, String message) {
//        String key = orgId + "-" + channel + "-" + message.replaceAll(" ","").replaceAll("\\d{6}", "");
//        return bhopValidateCodeMap.get(key);
//    }
//
//    private String getValidateCode(String smsBody) {
//        Pattern pattern = Pattern.compile("\\d{6}");
//        Matcher matcher = pattern.matcher(smsBody);
//        if (matcher.find()) {
//            return matcher.group();
//        }
//        return null;
//    }
//}
