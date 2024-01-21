//package io.bhex.base.common.sender;
//
//import com.alibaba.fastjson.JSON;
//import com.alibaba.fastjson.JSONObject;
//import io.bhex.base.common.config.SendCloudSmsProperties;
//import io.bhex.base.common.service.SmsDeliveryRecordService;
//import io.bhex.base.common.util.*;
//import lombok.Data;
//import lombok.extern.slf4j.Slf4j;
//import okhttp3.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.util.*;
//
///**
// * @Description:
// * @Date: 2019/1/28 下午6:18
// * @Author: liwei
// * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
// */
//@Slf4j
//@Service
//@Deprecated
//public class SendCloudSmsSender {
//
//    @Autowired
//    private SmsDeliveryRecordService smsDeliveryRecordService;
//
//    /**
//     * 发送全变量短信，相当于无模板短信
//     * @param sendCloudSmsProperties
//     * @param orgId
//     * @param phone
//     * @param content
//     * @return
//     */
//    public boolean sendGeneralSms(SendCloudSmsProperties sendCloudSmsProperties, Long orgId, String phone, String content, String bizType){
//        Map<String, String> vars = new HashMap<>();
//        vars.put("content", content);
//        return sendCnSms(sendCloudSmsProperties, orgId, sendCloudSmsProperties.getGeneralTemplateId()+"", vars, phone, bizType);
//    }
//    /**
//     *
//     * @param orgId
//     * @param templateId
//     * @param vars 模板参数
//     * @param phone
//     * @return
//     */
//    private boolean sendCnSms(SendCloudSmsProperties sendCloudSmsProperties, Long orgId, String templateId,
//                             Map<String, String> vars, String phone, String bizType){
//        TreeMap<String, String> treeMap = new TreeMap<>();
//        treeMap.put("smsUser", sendCloudSmsProperties.getApiUser());
//        treeMap.put("templateId", templateId);
//        treeMap.put("msgType","0");
//        treeMap.put("phone", phone);
//        treeMap.put("timestamp", System.currentTimeMillis()+"");
//        String varsJson = JSONObject.toJSONString(vars);
//        treeMap.put("vars", varsJson);
//
//        String signature = SendCloudSignUtil.md5Signature(treeMap, sendCloudSmsProperties.getApiKey());
//        treeMap.put("signature", signature);
//        Iterator<String> iterator = treeMap.keySet().iterator();
//
//        FormBody.Builder requestBuilder = new FormBody.Builder();
//        while (iterator.hasNext()) {
//            String key = iterator.next();
//            requestBuilder.add(key, treeMap.get(key));
//        }
//
//
//        try {
//            OkHttpClient okHttpClient = new OkHttpClient();
//            Response response = okHttpClient.newCall(new Request.Builder()
//                    .url(sendCloudSmsProperties.getSendApiUrl())
//                    .post(requestBuilder.build())
//                    .build())
//                    .execute();
//            ResponseBody body = response.body();
//            String result = body.string();
//
//            SendSmsResult sendSmsResult = JSON.parseObject(result, SendSmsResult.class);
//            String maskPhone = MaskUtil.maskMobile(phone);
//            log.info("orgId:{} phone:{} tmplId:{} content:{} result={}",orgId, maskPhone, templateId,
//                    MaskUtil.maskValidateCode(varsJson),
//                    result.replace(phone, maskPhone));
//            if (sendSmsResult.result == false) {
//                log.error("ALERT sendsms from sendcloud failed. {} {}", phone, result);
//            }
//            String sid = sendSmsResult.getInfo().getSmsIds().get(0);
//            smsDeliveryRecordService.insertSmsDeliveryRecord(orgId, SendChannelEnum.SEND_CLOUD, sid, "86", phone,
//                    sendSmsResult.getStatusCode()+"",
//                    sendSmsResult.getMessage(),bizType,"");
//
//            PrometheusUtil.smsSendCounter(ChannelConstant.SEND_CLOUD, sendSmsResult.result ? 200 : 400);
//
//        }
//        catch (Exception e) {
//            log.error("fail send cloud simple mail {}", 1, e);
//            return false;
//        }
//
//        return true;
//    }
//
//    public boolean sendCnSms(SendCloudSmsProperties sendCloudSmsProperties, Long orgId, String templateId,
//                             String[] tmplParams, String phone, String bizType){
//        Map<String, String> vars = new HashMap<>();
//        for(int i=0; i < tmplParams.length; i++){
//            vars.put((i+1) + "", tmplParams[i]);
//        }
//        return sendCnSms(sendCloudSmsProperties, orgId, templateId, vars, phone,bizType);
//
//    }
//
//    public  boolean sendVoice(long orgId, String phone,String code, String smsUser, String secretKey, String bizType){
//
//        TreeMap<String, String> treeMap = new TreeMap<>();
//        treeMap.put("smsUser", smsUser);
//        treeMap.put("phone", phone);
//        treeMap.put("code", code);
//        treeMap.put("timestamp", System.currentTimeMillis()+"");
//
//        String signature = SendCloudSignUtil.md5Signature(treeMap, secretKey);
//        treeMap.put("signature", signature);
//        Iterator<String> iterator = treeMap.keySet().iterator();
//
//        FormBody.Builder requestBuilder = new FormBody.Builder();
//        while (iterator.hasNext()) {
//            String key = iterator.next();
//            requestBuilder.add(key, treeMap.get(key));
//        }
//
//        try {
//            OkHttpClient okHttpClient = new OkHttpClient();
//            Response response = okHttpClient.newCall(new Request.Builder()
//                    .url("https://www.sendcloud.net/smsapi/sendVoice")
//                    .post(requestBuilder.build())
//                    .build())
//                    .execute();
//            ResponseBody body = response.body();
//            String result = body.string();
//            log.info("sendVoice res:{}", result);
//            SendSmsResult sendSmsResult = JSON.parseObject(result, SendSmsResult.class);
//            String maskPhone = MaskUtil.maskMobile(phone);
//            log.info("orgId:{} phone:{} result={}",orgId, maskPhone, result.replace(phone, maskPhone));
//            if (sendSmsResult.result == false) {
//                log.error("ALERT sendVoice from sendcloud failed. {} {}", phone, result);
//            }
//            String sid = sendSmsResult.getInfo().getSmsIds().get(0);
//            smsDeliveryRecordService.insertSmsDeliveryRecord(orgId, SendChannelEnum.SEND_CLOUD, sid, "86", phone,
//                    sendSmsResult.getStatusCode()+"",
//                    sendSmsResult.getMessage(), bizType,"");
//
//            PrometheusUtil.smsSendCounter(ChannelConstant.SEND_CLOUD, sendSmsResult.result ? 200 : 400);
//        }
//        catch (Exception e) {
//            log.error("fail send cloud simple mail {}", 1, e);
//            return false;
//        }
//
//        return true;
//    }
//
//    @Data
//    private static class SendSmsResult{
//        private String message;
//        private boolean result;
//        private int statusCode;
//        private SendSmsResultInfo info;
//    }
//
//    @Data
//    private static class SendSmsResultInfo{
//        private int successCount;
//        private List<String> smsIds;
//    }
//
//    public static void main(String[] args) {
//        new SendCloudSmsSender().sendVoice(6001L, "15010252133","905326","bhex_broker","HVrI0T0mBZqh7PjsW4WPkHY4DJw0hogY","");
//    }
//}
