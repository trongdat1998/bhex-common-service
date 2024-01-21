package io.bhex.base.common.sender.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.SimpleSMSRequest;
import io.bhex.base.common.Telephone;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.*;
import lombok.Cleanup;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;

/**
 * @Description:
 * @Date: 2019/1/28 下午6:18
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
@Slf4j
@Service
public class SendCloudSmsSenderV2  implements ISmsSender{
    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;
    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;

    private String defaultBaseUrl = "https://www.sendcloud.net/";

    private String vpnBaseUrl = "https://sendcloudsms.bhpc.cloud/";
    private static boolean VPN_URL_VALID = false;
    private String getSenderBaseUrl() {
        if (VPN_URL_VALID) {
            return vpnBaseUrl;
        }
        return defaultBaseUrl;
    }

    /**
     * 发送全变量短信，相当于无模板短信
     */
    public boolean sendGeneralSms(Long orgId, SenderDTO senderDTO, SimpleSMSRequest request)  throws IOException{
        Telephone telephone = request.getTelephone();
        String content = senderDTO.getSendContent(request.getParamsList(), request.getReqParamMap());

        SpInfo spInfo = senderDTO.getSpInfo();
        String extraInfo = spInfo.getConfigInfo();
        JsonObject jo = JsonUtil.defaultGson().fromJson(extraInfo, JsonElement.class).getAsJsonObject();

        Map<String, String> vars = new HashMap<>();
        vars.put("content", content);

        String generalTemplateId = request.getTelephone().getNationCode().equals("86")
                ? GsonObjectUtil.getAsString(jo,"general_template_id") : GsonObjectUtil.getAsString(jo,"international_general_template_id");

        return sendSms(orgId, senderDTO, generalTemplateId, vars,
                telephone.getMobile(), senderDTO.getScenario(), request);
    }
    /**
     *
     * @param orgId
     * @param templateId
     * @param vars 模板参数
     * @param phone
     * @return
     */
    private boolean sendSms(Long orgId, SenderDTO senderDTO, String templateId,
                             Map<String, String> vars, String phone, String bizType,
                              SimpleSMSRequest request) throws IOException {
        Telephone telephone = request.getTelephone();
        SpInfo spInfo = senderDTO.getSpInfo();
        TreeMap<String, String> treeMap = new TreeMap<>();
        treeMap.put("smsUser", spInfo.getAccessKeyId());
        treeMap.put("templateId", templateId);
        treeMap.put("msgType", telephone.getNationCode().equals("86") ? "0" : "2"); //0表示短信, 1表示彩信,2表示国际短信， 默认值为0
        treeMap.put("phone", telephone.getNationCode().equals("86") ? phone : "00" + telephone.getNationCode() + telephone.getMobile());
        treeMap.put("timestamp", System.currentTimeMillis()+"");
        String varsJson = JsonUtil.defaultGson().toJson(vars);
        treeMap.put("vars", varsJson);
        
        String signature = SendCloudSignUtil.md5Signature(treeMap, spInfo.getSecretKey());
        treeMap.put("signature", signature);
        Iterator<String> iterator = treeMap.keySet().iterator();

        FormBody.Builder requestBuilder = new FormBody.Builder();
        while (iterator.hasNext()) {
            String key = iterator.next();
            requestBuilder.add(key, treeMap.get(key));
        }


        //try {
            Response response = okHttpClient.newCall(new Request.Builder()
                    .url(getSenderBaseUrl() + "smsapi/send")
                    .post(requestBuilder.build())
                    .build())
                    .execute();
            ResponseBody body = response.body();
            String result = body.string();

            SendSmsResult sendSmsResult = JsonUtil.defaultGson().fromJson(result, SendSmsResult.class);//JSON.parseObject(result, SendSmsResult.class);
            String maskPhone = MaskUtil.maskMobile(telephone.getNationCode(), phone);
            log.info("sp:{} orgId:{} phone:{} tmplId:{} content:{} result={}",spInfo.getId(), orgId, maskPhone, templateId,
                    MaskUtil.maskValidateCode(varsJson),
                    result.replace(phone, maskPhone));
            if (sendSmsResult.result == false) {
                log.error("ALERT sendsms from sendcloud failed. {} {}", maskPhone, result);

            }
            String sid = sendSmsResult.getInfo().getSmsIds().get(0);

            smsDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), sid,
                    sendSmsResult.getStatusCode() == 200 ? "request-200" : sendSmsResult.getMessage(), sendSmsResult.getMessage());
            PrometheusUtil.smsSendCounter(ChannelConstant.SEND_CLOUD, sendSmsResult.result ? 200 : 400);

//        }
//        catch (Exception e) {
//            log.error("fail send cloud simple mail {}", 1, e);
//            return false;
//        }
//
        return true;
    }

    @Override
    public boolean send(Long orgId, SenderDTO senderDTO, SimpleSMSRequest request)  throws IOException{
        if (senderDTO.isWholeSend()) {
            return sendGeneralSms(orgId, senderDTO, request);
        }
        List<String> params = request.getParamsList();
        Telephone telephone = request.getTelephone();
        Map<String, String> vars = new HashMap<>();
        if (!CollectionUtils.isEmpty(params)) {
            for(int i=0; i < params.size(); i++){
                vars.put((i+1) + "", params.get(i));
            }
        }
        if (senderDTO.getOriginTemplateContent().contains("~broker~")) {
            vars.put("broker", senderDTO.getOrgName());
        }

        return sendSms(orgId, senderDTO,  senderDTO.getTargetTmplId(), vars,
                telephone.getMobile(), senderDTO.getScenario(), request);

    }

    public  boolean sendVoice(long orgId, String phone,String code, String smsUser, String secretKey, String bizType){

        TreeMap<String, String> treeMap = new TreeMap<>();
        treeMap.put("smsUser", smsUser);
        treeMap.put("phone", phone);
        treeMap.put("code", code);
        treeMap.put("timestamp", System.currentTimeMillis()+"");

        String signature = SendCloudSignUtil.md5Signature(treeMap, secretKey);
        treeMap.put("signature", signature);
        Iterator<String> iterator = treeMap.keySet().iterator();

        FormBody.Builder requestBuilder = new FormBody.Builder();
        while (iterator.hasNext()) {
            String key = iterator.next();
            requestBuilder.add(key, treeMap.get(key));
        }

        try {
            OkHttpClient okHttpClient = new OkHttpClient();
            Response response = okHttpClient.newCall(new Request.Builder()
                    .url("https://www.sendcloud.net/smsapi/sendVoice")
                    .post(requestBuilder.build())
                    .build())
                    .execute();
            ResponseBody body = response.body();
            String result = body.string();
            log.info("sendVoice res:{}", result);
            SendSmsResult sendSmsResult = JsonUtil.defaultGson().fromJson(result, SendSmsResult.class);
            String maskPhone = MaskUtil.maskMobile(phone);
            log.info("orgId:{} phone:{} result={}",orgId, maskPhone, result.replace(phone, maskPhone));
            if (sendSmsResult.result == false) {
                log.error("ALERT sendVoice from sendcloud failed. {} {}", phone, result);
            }
            String sid = sendSmsResult.getInfo().getSmsIds().get(0);
            smsDeliveryRecordService.insertSmsDeliveryRecord(orgId, SendChannelEnum.SEND_CLOUD, sid, "86", phone,
                    sendSmsResult.getStatusCode()+"",
                    sendSmsResult.getMessage(), bizType, "", 0, "", 0L);

            PrometheusUtil.smsSendCounter(ChannelConstant.SEND_CLOUD, sendSmsResult.result ? 200 : 400);
        }
        catch (Exception e) {
            log.error("fail send cloud simple mail {}", 1, e);
            return false;
        }

        return true;
    }

    @Resource(name = "detectOkHttpClient")
    private OkHttpClient detectOkHttpClient;

    //@Scheduled(cron = "0/30 * * * * ?")
    public void detectWebUrl() {
        try {
            Request smsRequest = new
                    Request.Builder()
                    .url("https://sendcloudsms.bhex.io/smsapi")
                    .get().build();
            @Cleanup
            Response response = detectOkHttpClient.newCall(smsRequest).execute();
            VPN_URL_VALID = true;

        } catch (Exception e) {
            VPN_URL_VALID = false;
        }
    }

    @Data
    private static class SendSmsResult{
        private String message;
        private boolean result;
        private int statusCode;
        private SendSmsResultInfo info;
    }

    @Data
    private static class SendSmsResultInfo{
        private int successCount;
        private List<String> smsIds;
    }

}
