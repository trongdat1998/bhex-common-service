package io.bhex.base.common.sender.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.SimpleSMSRequest;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Service
public class ZhuTongSender implements ISmsSender {
    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;
    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;

    private String defaultBaseUrl = "https://api.mix2.zthysms.com/";

    private String vpnBaseUrl = "https://zhutongsms.bhpc.cloud/";

    private String getSenderBaseUrl() {
        if (ChannelConstant.VPN_URL_VALID) {
            return vpnBaseUrl;
        }
        return defaultBaseUrl;
    }

    @Override
    public boolean send(Long orgId, SenderDTO senderDTO, SimpleSMSRequest request) throws IOException {
        SpInfo sp = senderDTO.getSpInfo();
        String mobile = request.getTelephone().getMobile();


        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        String merchantOrderId = dateFormat.format(new Date()) + "-" + orgId + "-"  + request.getBusinessType() + "-" + mobile;
        if (merchantOrderId.length() > 64) {
            //orderid限制在64以内
            merchantOrderId = merchantOrderId.substring(0, 63);
        }

        SendSmsParam sendParam = new SendSmsParam();
        sendParam.setUsername(sp.getAccessKeyId());
        Long tKey = System.currentTimeMillis()/1000;
        sendParam.setTKey(tKey);
        sendParam.setPassword(MD5Util.getMD5(MD5Util.getMD5(sp.getSecretKey()) + tKey));
        sendParam.setMobile(mobile);
        sendParam.setExtend(merchantOrderId);
        sendParam.setContent(senderDTO.getSignName() + senderDTO.getSendContent(request.getParamsList(), request.getReqParamMap()));
        String data = JsonUtil.defaultGson().toJson(sendParam);
        log.info("data:{}", data);

        String responseTxt = "";


        RequestBody requestBody =
                RequestBody.create(MediaType.parse("application/json;charset=utf-8;"), data);
        Request smsRequest = new
                Request.Builder()
                .url(getSenderBaseUrl() + "v2/sendSms")
                .post(requestBody).build();
        Response response = okHttpClient.newCall(smsRequest).execute();
        if (response.isSuccessful()) {
            String text = response.body().string();
            if (text != null) {
                responseTxt = text;
            }
        }

        String maskPhone = MaskUtil.maskMobile(mobile);
        log.info("sp:{} orgId:{} phone:{} content:{} result={}",
                sp.getId(), orgId, maskPhone,
                MaskUtil.maskValidateCode(sendParam.getContent()),
                responseTxt.replace(mobile, maskPhone));

        JsonObject jo = JsonUtil.defaultGson().fromJson(responseTxt, JsonElement.class).getAsJsonObject();
        Integer smsCount = jo.get("contNum").getAsInt();
        smsCount = smsCount == null ? 1 : smsCount;
        if (jo.get("code").getAsInt() == 200) {
            String sid = jo.get("msgId").getAsString();
            smsDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), sid, "request-200", jo.get("msg").getAsString());

        } else {
            log.error("send sms failed:{} {}", merchantOrderId, responseTxt);
            smsDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), senderDTO.getTempMessageId(), "request-failed",
                    jo.get("code").getAsInt() + "-" + jo.get("msg").getAsString());
        }


        PrometheusUtil.smsSendCounter(SendChannelEnum.ZHUTONG.name(), jo.get("code").getAsInt() == 200 ? 200 : 400);

        return true;
    }

    @Data
    private static class SendSmsParam {
        private String username;
        private String password;
        private Long tKey;
        private String mobile;
        private String content;
        private String extend;
    }
}
