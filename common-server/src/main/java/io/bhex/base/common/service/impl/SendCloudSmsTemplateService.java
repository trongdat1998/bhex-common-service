package io.bhex.base.common.service.impl;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.service.SmsTemplateService;
import io.bhex.base.common.util.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.TreeMap;

@Slf4j
@Service
public class SendCloudSmsTemplateService implements SmsTemplateService {




    //smsTypeStr 短信内容类型，"0"表示验证码，"1"表示行业通知，"2"表示营销
    //msgType 业务类型，"0"代表国内短信，"2"代表国际短信，默认国内短信
    @Override
    public String create(String templateName, String templateContent, String signName,
                         MsgTypeEnum msgType, InternationalEnum internationalEnum,
                         String apiKey, String secretKey){

        TreeMap<String, String> treeMap = new TreeMap<>();
        treeMap.put("smsUser", apiKey);
        treeMap.put("templateName", templateName);
        treeMap.put("templateText", NoticeRenderUtil.render2SendCloudTmpl(templateContent));
        treeMap.put("signName", signName);
        treeMap.put("smsTypeStr", msgType.name().equals(MsgTypeEnum.VERIFY_CODE.name()) ? "0" : "1");
        treeMap.put("msgType",internationalEnum.name().equals(InternationalEnum.INTERNATIONAL.name()) ? "2" : "0");

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
                    .url("https://www.sendcloud.net/smsapi/addsms")
                    .post(requestBuilder.build())
                    .build())
                    .execute();
            ResponseBody body = response.body();
            String result = body.string();
            log.info("create template res:{}", result);
            JsonObject jo = JsonUtil.defaultGson().fromJson(result, JsonElement.class).getAsJsonObject();
            if (jo != null && jo.get("statusCode") != null && jo.get("statusCode").getAsInt() == 200) {
                return jo.getAsJsonObject("info").get("templateId").getAsString();
            }
        }
        catch (Exception e) {
            log.error("fail send cloud simple mail {}", 1, e);
        }

        return "0";
    }

    @Override
    public boolean submitVerify(String templateId, String apiKey, String secretKey){

        TreeMap<String, String> treeMap = new TreeMap<>();
        treeMap.put("smsUser", apiKey);
        treeMap.put("templateIdStr", templateId);

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
                    .url("https://www.sendcloud.net/smsapi/submitsms")
                    .post(requestBuilder.build())
                    .build())
                    .execute();
            ResponseBody body = response.body();
            String result = body.string();
            log.info("submitTemplateVerify res:{}", result);
            JsonObject jo = JsonUtil.defaultGson().fromJson(result, JsonElement.class).getAsJsonObject();
            if (jo != null && jo.get("statusCode") != null && jo.get("statusCode").getAsInt() == 200) {
                return true;
            }
        }
        catch (Exception e) {
            log.error("fail send cloud simple mail {}", 1, e);
        }

        return false;
    }

    @Override
    public boolean verifyied(String templateId, String apiKey, String secretKey){

        TreeMap<String, String> treeMap = new TreeMap<>();
        treeMap.put("smsUser", apiKey);
        treeMap.put("templateIdStr", templateId);

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
                    .url("https://www.sendcloud.net/smsapi/get")
                    .post(requestBuilder.build())
                    .build())
                    .execute();
            ResponseBody body = response.body();
            String result = body.string();
            log.info("queryTemplate tmpid:{} res:{}", templateId, result);

            JsonObject jo = JsonUtil.defaultGson().fromJson(result, JsonElement.class).getAsJsonObject();
            if (jo != null && jo.get("statusCode") != null && jo.get("statusCode").getAsInt() == 200) {
                return jo.getAsJsonObject("info").get("isVerify").getAsString().equals("审核通过");
            }

//            "info": {
//                "templateId": 00,
//                        "templateName": "验证码",
//                        "msgType": "国内短信",
//                        "smsType": "验证码",
//                        "templateContent": "【爱发信】短息api模板",
//                        "isVerify": "审核通过",
//                        "createTime": "2015-03-23",
//                        "updateTime": "2017-03-29"
//            },
        }
        catch (Exception e) {
            log.error("fail send cloud simple mail {}", 1, e);
        }
        return false;
    }




    public static void main(String[] args) {
//        createTemplate("注册验证码-国内-英文","Your BHEX sign up code is %1%, do not disclose the code to others.","BHEX", 0,1,
//                "http://www.sendcloud.net/smsapi/addsms","bhex_broker","HVrI0T0mBZqh7PjsW4WPkHY4DJw0hogY");


//        submitTemplateVerify("23899",
//                "http://www.sendcloud.net/smsapi/submitsms","bhex_broker","HVrI0T0mBZqh7PjsW4WPkHY4DJw0hogY");

        new SendCloudSmsTemplateService().verifyied("23899", "bhex_broker","HVrI0T0mBZqh7PjsW4WPkHY4DJw0hogY");

      //  send("24545", "http://www.sendcloud.net/smsapi/send", "bhex_broker","HVrI0T0mBZqh7PjsW4WPkHY4DJw0hogY", "15810252165");
        //       sendVoice("15810252165","435456","http://www.sendcloud.net/smsapi/sendVoice","bhex_broker","HVrI0T0mBZqh7PjsW4WPkHY4DJw0hogY");
    }
}