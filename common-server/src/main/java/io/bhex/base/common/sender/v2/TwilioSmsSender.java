package io.bhex.base.common.sender.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.SimpleSMSRequest;
import io.bhex.base.common.Telephone;
import io.bhex.base.common.entity.SmsDeliveryRecordEntity;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.*;
import lombok.Cleanup;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TwilioSmsSender implements ISmsSender {

    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;

    @Override
    public boolean send(Long orgId, SenderDTO senderDTO, SimpleSMSRequest request) throws IOException {
        SpInfo spInfo = senderDTO.getSpInfo();
        String extraInfo = spInfo.getConfigInfo();

        JsonObject extraInfoJo = JsonUtil.defaultGson().fromJson(extraInfo, JsonElement.class).getAsJsonObject();

        OkHttpClient okHttpClient = new OkHttpClient().newBuilder()
                .authenticator(new Authenticator() {
                    @Nullable
                    @Override
                    public Request authenticate(Route route, Response response) throws IOException {
                        String credential = Credentials.basic(spInfo.getAccessKeyId(), spInfo.getSecretKey());
                        return response.request().newBuilder()
                                .header("Authorization", credential)
                                .build();
                    }
                })
                .addInterceptor(OkHttpPrometheusInterceptor.getInstance())
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
        Telephone telephone = request.getTelephone();
        TreeMap<String, String> treeMap = new TreeMap<>();
        treeMap.put("Body", "["+senderDTO.getOriginSignName() + "] " + senderDTO.getSendContent(request.getParamsList(), request.getReqParamMap()));
        treeMap.put("StatusCallback", extraInfoJo.get("StatusCallback").getAsString());
        treeMap.put("To", "+" + telephone.getNationCode() + "" + telephone.getMobile());
        treeMap.put("MessagingServiceSid", extraInfoJo.get("MessagingServiceSid").getAsString());
        FormBody.Builder requestBuilder = new FormBody.Builder();
        Iterator<String> iterator = treeMap.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            requestBuilder.add(key, treeMap.get(key));
        }
        @Cleanup
        Response response = okHttpClient.newCall(new Request.Builder()
                .url(String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json", spInfo.getAccessKeyId()))
                .post(requestBuilder.build())
                .build())
                .execute();
        ResponseBody body = response.body();
        String result = body.string();
        JsonObject jo = JsonUtil.defaultGson().fromJson(result, JsonElement.class).getAsJsonObject();
        //log.info("jo:{}", result);
        log.info("message:{}, phone:{}, content:{}, status:{}", GsonObjectUtil.getAsString(jo,"sid"),
                MaskUtil.maskMobile(treeMap.get("To")), MaskUtil.maskValidateCode(treeMap.get("Body")), GsonObjectUtil.getAsString(jo,"status"));

        smsDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), GsonObjectUtil.getAsString(jo,"sid"),
                "request-200", GsonObjectUtil.getAsString(jo,"status"));

        MessageData messageData = new MessageData();
        messageData.setUser(spInfo.getAccessKeyId());
        messageData.setPassword(spInfo.getSecretKey());
        messageData.setSid(GsonObjectUtil.getAsString(jo,"sid"));
        messages.put(messageData, 0);
        fetchMessage(messageData);
//        JsonObject priceJo = fetchMessage(spInfo.getAccessKeyId(), spInfo.getSecretKey(), GsonObjectUtil.getAsString(jo,"sid"));
//        if (priceJo != null) {
//            smsDeliveryRecordService.updateTempMessageIdExtra(senderDTO.getTempMessageId(), GsonObjectUtil.getAsString(jo,"sid"),
//                    "request-200",  GsonObjectUtil.getAsString(jo,"status"), 1,
//                    GsonObjectUtil.getAsBigDecimal(priceJo, "price").abs().stripTrailingZeros().toPlainString(), GsonObjectUtil.getAsString(priceJo, "price_unit"));
//        } else {
//            smsDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), GsonObjectUtil.getAsString(jo,"sid"),
//                    "request-200", GsonObjectUtil.getAsString(jo,"status"));
//        }
        return false;
    }

  //{"body": "[HBTC] Dear BHEXTEST user: Your verification code is 69535, this verification code is valid for 10 minutes, please verify this is your own operation.",
    // "num_segments": "1", "direction": "outbound-api", "from": "+19045496634", "date_updated": "Mon, 22 Jun 2020 06:05:37 +0000",
    // "price": "-0.00675", "error_message": null, "uri": "/2010-04-01/Accounts/AC589cc3d622f88c5bdb6576777259b9be/Messages/SMac47e60289724c63a3f81921a7bd3a97.json",
    // "account_sid": "AC589cc3d622f88c5bdb6576777259b9be", "num_media": "0", "to": "+15014380798", "date_created": "Mon, 22 Jun 2020 06:05:36 +0000",
    // "status": "delivered", "sid": "SMac47e60289724c63a3f81921a7bd3a97", "date_sent": "Mon, 22 Jun 2020 06:05:37 +0000",
    // "messaging_service_sid": "MG2a6fcb808b31824dff834e87b29c0d40", "error_code": null, "price_unit": "USD", "api_version": "2010-04-01",
    // "subresource_uris": {"media": "/2010-04-01/Accounts/AC589cc3d622f88c5bdb6576777259b9be/Messages/SMac47e60289724c63a3f81921a7bd3a97/Media.json",
    // "feedback": "/2010-04-01/Accounts/AC589cc3d622f88c5bdb6576777259b9be/Messages/SMac47e60289724c63a3f81921a7bd3a97/Feedback.json"}}


    //https://api.twilio.com/2010-04-01/Accounts/AC589cc3d622f88c5bdb6576777259b9be/Messages/SMac47e60289724c63a3f81921a7bd3a97.json
    @Async
    public SmsDeliveryRecordEntity fetchMessage(MessageData messageData) {
        int execTime = messages.getOrDefault(messageData, 0) ;
        try {
            OkHttpClient okHttpClient = new OkHttpClient().newBuilder()
                    .authenticator(new Authenticator() {
                        @Nullable
                        @Override
                        public Request authenticate(Route route, Response response) throws IOException {
                            String credential = Credentials.basic(messageData.getUser(), messageData.getPassword());
                            return response.request().newBuilder()
                                    .header("Authorization", credential)
                                    .build();
                        }
                    })
                    .addInterceptor(OkHttpPrometheusInterceptor.getInstance())
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .build();
            @Cleanup
            Response response = okHttpClient.newCall(new Request.Builder()
                    .url(String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Messages/%s.json", messageData.getUser(), messageData.getSid()))
                    .get()
                    .build())
                    .execute();
            ResponseBody body = response.body();
            String result = body.string();
            log.info("time:{} result:{}", execTime, MaskUtil.maskValidateCode(result));
            JsonObject priceJo = JsonUtil.defaultGson().fromJson(result, JsonElement.class).getAsJsonObject();
            BigDecimal price = GsonObjectUtil.getAsBigDecimal(priceJo, "price").abs();

            if (price.compareTo(BigDecimal.ZERO) > 0) {
                smsDeliveryRecordService.updateFee(messageData.getSid(), price.toPlainString(), GsonObjectUtil.getAsString(priceJo, "price_unit"));
                messages.remove(messageData);
            } else {
                messages.put(messageData, execTime + 1);
            }
            if (execTime > 4) {
                messages.remove(messageData);
            }
            SmsDeliveryRecordEntity recordEntity = new SmsDeliveryRecordEntity();
            recordEntity.setPrice(price);
            recordEntity.setPriceUnit(GsonObjectUtil.getAsString(priceJo, "price_unit"));
            recordEntity.setCountNum(GsonObjectUtil.getAsInt(priceJo, "num_segments", 1));
            return recordEntity;
        } catch (Exception e) {
            messages.put(messageData, execTime + 1);
            log.error("fetch message error", e);
        }
        return null;
    }

    @Scheduled(cron = "0/3 * * * * ?")
    public void syncPriceData() {
        messages.forEach((m, counter) -> {
            if (counter > 0) {
                fetchMessage(m);
            }
        });
    }

    private static ConcurrentHashMap<MessageData, Integer> messages = new ConcurrentHashMap<>();
    @Data
    public static class MessageData {
        private String user;
        private String password;
        private String sid;
    }
}
