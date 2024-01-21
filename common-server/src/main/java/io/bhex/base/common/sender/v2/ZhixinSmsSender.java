package io.bhex.base.common.sender.v2;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.SimpleSMSRequest;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.*;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ZhixinSmsSender implements ISmsSender{

    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;


    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;

    private static boolean VPN_URL_VALID = false;
    private final String defaultBaseUrl = "https://api.mozisms.com/";
    private final String vpnBaseUrl = "https://zhixinsms.bhpc.cloud/";
    private String getSenderBaseUrl() {
        if (VPN_URL_VALID) {
            return vpnBaseUrl;
        }
        return defaultBaseUrl;
    }

    @Resource(name = "detectOkHttpClient")
    private OkHttpClient detectOkHttpClient;

    //@Scheduled(cron = "0/30 * * * * ?")
    public void detectWebUrl() {
        try {
            Request smsRequest = new
                    Request.Builder()
                    .url("https://zhixinsms.bhpc.cloud/sms/send/content")
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


    @Override
    public boolean send(Long orgId, SenderDTO senderDTO, SimpleSMSRequest request) throws IOException{
        SpInfo sp = senderDTO.getSpInfo();
        String mobile = request.getTelephone().getMobile();
        String content = senderDTO.getSendContent(request.getParamsList(), request.getReqParamMap());

        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS");
        String merchantOrderId = dateFormat.format(new Date()) + "-" + orgId + "-"  + request.getBusinessType() + "-" + mobile;
        if (merchantOrderId.length() > 64) {
            //orderid限制在64以内
            merchantOrderId = merchantOrderId.substring(0, 63);
        }

        String result = sendContent(merchantOrderId, mobile, senderDTO.getOriginSignName(), content, sp);
        if (StringUtils.isEmpty(result)) {
            log.error("ALERT sendsms from zhixin failed. {} {} {}", merchantOrderId, mobile, result);
            return false;
        }
        String maskPhone = MaskUtil.maskMobile(mobile);
        log.info("sp:{} orgId:{} phone:{} content:{} result={}",
                sp.getId(), orgId, maskPhone,
                MaskUtil.maskValidateCode(content),
                result.replace(mobile, maskPhone));
//{"status":1,"result":[{"mobile":"15810252165","code":"B000000","msg":"成功","orderId":334080578544484359}]}
        JsonElement element = JsonUtil.defaultGson().fromJson(result, JsonElement.class);
        JsonObject jo = element.getAsJsonObject();

        //JSONObject jo = JSON.parseObject(result);
        if (jo.get("status").getAsInt() == 1) {
            JsonArray array = jo.get("result").getAsJsonArray();
            JsonObject obj = array.get(0).getAsJsonObject();
            String status = GsonObjectUtil.getAsString(obj, "code").equalsIgnoreCase("B000000")
                    ? "request-200" : GsonObjectUtil.getAsString(obj,"msg");
            String message = GsonObjectUtil.getAsString(obj, "msg");
            String sid = GsonObjectUtil.getAsString(obj,"orderId");

            smsDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), sid, status, message);


        } else {
            log.error("send sms failed:{} {}", merchantOrderId, result);

            smsDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), senderDTO.getTempMessageId(),
                    "request-failed", GsonObjectUtil.getAsString(jo,"code") + "-" + GsonObjectUtil.getAsString(jo,"msg"));
        }


        PrometheusUtil.smsSendCounter(SendChannelEnum.ZHIXIN.name(), jo.get("status").getAsInt() == 1 ? 200 : 400);

        return true;
    }

    /**
     * 短信发送
     */
    private String sendContent(String merchantOrderId, String mobile, String sign, String content ,SpInfo sp) throws IOException{
        Map<String, Object> param = new HashMap<>();
        param.put("sign", sign);
        param.put("content", content);
        param.put("mobiles", mobile);
        param.put("merchantOrderId", merchantOrderId);
        return postSms(sp, param);
    }

    /**
     * 发送短信 *
     * @return
     */
    private String postSms(SpInfo sp, Map<String, Object> param) throws IOException{
        String data = JsonUtil.defaultGson().toJson(param);
        String responseTxt = "";

        JsonObject jo = JsonUtil.defaultGson().fromJson(sp.getConfigInfo(), JsonElement.class).getAsJsonObject();

        Headers head = getPostHead(buildSign(data, sp.getSecretKey()),
                sp.getAccessKeyId(), jo.get("merchantVersion").getAsString());
        RequestBody requestBody =
                RequestBody.create(MediaType.parse("application/json;charset=utf-8;"), data);
        Request request = new
                Request.Builder()
                .url(getSenderBaseUrl() + "sms/send/content")
                .headers(head).post(requestBody).build();
        Response response = okHttpClient.newCall(request).execute();
        if (response.isSuccessful()) {
            String text = response.body().string();
            if (text != null) {
                responseTxt = text;
            }
        }

        return responseTxt;
    }
    /**
     * 构建请求头 *
     * @param sign 动态签名 * @return
     */
    public Headers getPostHead(String sign, String merchantId, String merchantVersion) {
        return new Headers.Builder().add("Accept", "application/json;charset=utf-8;")
                .add("Content-Type", "application/json;charset=utf-8;")
                .add("xsms_merchant_id", merchantId)
                .add("xsms_merchant_version", merchantVersion)
                .add("xsms_sign", sign)
                .build();
    }


    /**
     * 签名⽣生成示例例
     * @param data 请求body数据内容
     * @param apikey 对应版本的apikey
     * @return 签名
     */
    private String buildSign(String data, String apikey) throws
            UnsupportedEncodingException {
        return Hex.encodeHexString(Hashing.sha1().hashBytes((data +
                apikey).getBytes("UTF-8")).asBytes());
    }

}
