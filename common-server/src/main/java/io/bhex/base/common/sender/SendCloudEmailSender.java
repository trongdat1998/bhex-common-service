//package io.bhex.base.common.sender;
//
//import com.alibaba.fastjson.JSON;
//import io.bhex.base.common.config.SendCloudEmailProperties;
//import io.bhex.base.common.service.EmailDeliveryRecordService;
//import io.bhex.base.common.util.ChannelConstant;
//import io.bhex.base.common.util.MaskUtil;
//import io.bhex.base.common.util.PrometheusUtil;
//import lombok.Data;
//import lombok.extern.slf4j.Slf4j;
//import okhttp3.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.PostConstruct;
//import java.util.List;
//
//
//
//@Slf4j
//@Service
//@Deprecated
//public class SendCloudEmailSender {
//
//    @Autowired
//    private SendCloudEmailProperties emailProperties;
//
//    @Autowired
//    private EmailDeliveryRecordService emailDeliveryRecordService;
//
//    @PostConstruct
//    public void init() {
//    }
//
//    public boolean send(Long orgId, String sign, String to,   String subject, String content, String bizType) {
//
//
//        FormBody.Builder requestBuilder = new FormBody.Builder();
//        requestBuilder.add("apiUser", emailProperties.getApiUser());
//        requestBuilder.add("apiKey", emailProperties.getApiKey());
//        requestBuilder.add("from", emailProperties.getUsername());
//        requestBuilder.add("fromName", sign);
//        requestBuilder.add("to", to);
//        requestBuilder.add("subject", subject);
//        requestBuilder.add("html", content);
//
//        try {
//            OkHttpClient okHttpClient = new OkHttpClient();
//            Response response = okHttpClient.newCall(new Request.Builder()
//                    .url(emailProperties.getSendApiUrl())
//                    .post(requestBuilder.build())
//                    .build())
//                    .execute();
//
//            ResponseBody body = response.body();
//            String result = body.string();
//            String maskEmail = MaskUtil.maskEmail(to);
//            log.info("succ sendCloudMail orgId:{} to:{} content:{} result:{}", orgId, maskEmail,
//                    MaskUtil.maskValidateCode(content), result.replace(to, maskEmail));
//
//            SendEmailResult sendEmailResult = JSON.parseObject(result, SendEmailResult.class);
//            //log.info("email:{}  result={}", to, sendEmailResult);
//            if (sendEmailResult.isResult() == false) {
//                log.error("ALERT sendcloud error:{}", result);
//            }
//
//            String sid = sendEmailResult.getInfo().getEmailIdList().get(0);
//            emailDeliveryRecordService.insertSendCloudEmailDeliveryRecord(orgId, sid, to,
//                    sendEmailResult.getStatusCode()+"",
//                    sendEmailResult.getMessage(), bizType,"");
//            PrometheusUtil.emailSendCounter(ChannelConstant.SEND_CLOUD, sendEmailResult.result ? 200 : 400);
//
//        }
//        catch (Exception e) {
//            log.error("fail send cloud simple mail {}", MaskUtil.maskEmail(to), e);
//            return false;
//        }
//
//        return false;
//    }
//
//    @Data
//    private static class SendEmailResult{
//        private String message;
//        private boolean result;
//        private int statusCode;
//        private SendEmailResultInfo info;
//    }
//
//    @Data
//    private static class SendEmailResultInfo{
//        private List<String> emailIdList;
//    }
//
//}
