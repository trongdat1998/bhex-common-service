package io.bhex.base.common.sender.v2;

import com.google.protobuf.TextFormat;
import io.bhex.base.common.SimpleMailRequest;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.EmailDeliveryRecordService;
import io.bhex.base.common.util.*;
import lombok.Cleanup;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Text;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
public class SendCloudEmailSenderV2 implements IEmailSender{

    private static boolean VPN_URL_VALID = false;

    @Autowired
    private EmailDeliveryRecordService emailDeliveryRecordService;
    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;

    private String defaultBaseUrl = "https://api.sendcloud.net/";

    private String vpnBaseUrl = "https://sendcloudemail.bhpc.cloud/";

    private String getSenderBaseUrl() {
        if (VPN_URL_VALID) {
            return vpnBaseUrl;
        }
        return defaultBaseUrl;
    }


//    private static Map<String, Integer> labelMap = new HashMap<>();
//    static {
//        labelMap.put("register_code", 6762397);
//        labelMap.put("login_success_notice", 6762396);
//        labelMap.put("contract", 6762395);
//        labelMap.put("broker_notice", 6762393);
//        labelMap.put("bh_notice", 6762391);
//        labelMap.put("admin", 6762381);
//        labelMap.put("code", 6762378);
//        labelMap.put("otc", 6762361);
//    }

    @Override
    public boolean send(Long orgId, SenderDTO senderDTO, SimpleMailRequest request) throws IOException{
        String to = request.getMail();
        String content = senderDTO.getEmailSendContent(request.getParamsList(), request.getReqParamMap());

        SpInfo spInfo = senderDTO.getSpInfo();

        String extraInfo = spInfo.getConfigInfo();

        Map<String, String> jo = JsonUtil.defaultGson().fromJson(extraInfo, Map.class);

        FormBody.Builder requestBuilder = new FormBody.Builder();
        requestBuilder.add("apiUser", spInfo.getAccessKeyId());
        requestBuilder.add("apiKey", spInfo.getSecretKey());
        requestBuilder.add("from", jo.getOrDefault("username", ""));
        requestBuilder.add("fromName", senderDTO.getSignName());
        requestBuilder.add("to", to);
        requestBuilder.add("subject", senderDTO.getEmailSubject());
        requestBuilder.add("html", content);


        try {
            Response response = okHttpClient.newCall(new Request.Builder()
                    .url(getSenderBaseUrl() + "apiv2/mail/send")
                    .post(requestBuilder.build())
                    .build())
                    .execute();

            ResponseBody body = response.body();
            String result = body.string();
            if (!response.isSuccessful()) {
                log.error("ALERT sendcloud error:{}", result);
                return false;
            }
            String maskEmail = MaskUtil.maskEmail(to);
            log.info("sendCloudMail lang:{} sp:{} orgId:{} to:{} content:{} result:{}", request.getLanguage(), spInfo.getId(), orgId, maskEmail,
                    MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)), result.replace(to, maskEmail));

            SendEmailResult sendEmailResult = JsonUtil.defaultGson().fromJson(result, SendEmailResult.class);
            //log.info("email:{}  result={}", to, sendEmailResult);
            if (sendEmailResult.isResult() == false) {
                log.error("ALERT sendcloud error:{}", result);
            }

            String sid = sendEmailResult.getInfo().getEmailIdList().get(0);

            emailDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), sid, "request-200", sendEmailResult.getMessage());

            PrometheusUtil.emailSendCounter(ChannelConstant.SEND_CLOUD, sendEmailResult.result ? 200 : 400);
            response.close();
        } catch (IOException e) {
            log.info("fail sendCloudMail sp:{} orgId:{} to:{} bizType:{}",
                    spInfo.getId(), orgId, MaskUtil.maskEmail(to), request.getBusinessType(), e);
            throw new IOException(e);
        }

        return false;
    }

    @Resource(name = "detectOkHttpClient")
    private OkHttpClient detectOkHttpClient;

    //@Scheduled(cron = "0/30 * * * * ?")
    public void detectWebUrl() {
        try {
            Request smsRequest = new
                    Request.Builder()
                    .url("https://sendcloudemail.bhex.io/")
                    .get().build();
            @Cleanup
            Response response = detectOkHttpClient.newCall(smsRequest).execute();
            if (response.isSuccessful()) {
                VPN_URL_VALID = true;
            }
        } catch (Exception e) {
            VPN_URL_VALID = false;
        }
    }

    @Data
    private static class SendEmailResult{
        private String message;
        private boolean result;
        private int statusCode;
        private SendEmailResultInfo info;
    }

    @Data
    private static class SendEmailResultInfo{
        private List<String> emailIdList;
    }

}
