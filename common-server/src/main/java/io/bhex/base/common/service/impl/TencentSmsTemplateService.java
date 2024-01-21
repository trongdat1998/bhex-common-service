//package io.bhex.base.common.service.impl;
//
//import com.alibaba.fastjson.JSONArray;
//import com.alibaba.fastjson.JSONObject;
//import com.github.qcloudsms.SmsSenderUtil;
//import com.google.gson.JsonArray;
//import io.bhex.base.common.service.SmsTemplateService;
//import io.bhex.base.common.util.InternationalEnum;
//import io.bhex.base.common.util.MsgTypeEnum;
//import io.bhex.base.common.util.SendCloudSignUtil;
//import lombok.extern.slf4j.Slf4j;
//import okhttp3.*;
//import org.apache.http.NameValuePair;
//import org.springframework.stereotype.Component;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.Iterator;
//import java.util.List;
//import java.util.TreeMap;
//
//@Slf4j
//@Service
//public class TencentSmsTemplateService implements SmsTemplateService {
//
//    //smsTypeStr 短信内容类型，"0"表示验证码，"1"表示行业通知，"2"表示营销
//    //msgType 业务类型，"0"代表国内短信，"2"代表国际短信，默认国内短信
//
//
//    @Override
//    public String create(String templateName, String templateContent, String signName,
//                         MsgTypeEnum msgType, InternationalEnum internationalEnum,
//                         String apiKey, String secretKey) {
//
//        //https://yun.tim.qq.com/v5/tlssmssvr/add_template?sdkappid=xxxxx&random=xxxx
////        {
////            "remark": "xxxxx",
////                "international": 0,
////                "sig": "c13e54f047ed75e821e698730c72d030dc30e5b510b3f8a0fb6fb7605283d7df",
////                "text": "xxxxx",
////                "time": 1457336869,
////                "title": "xxxxx",
////                "type": 0
////        }
//
//        long random = SmsSenderUtil.getRandom();
//        long time = SmsSenderUtil.getCurrentTime();
//        String sig = SmsSenderUtil.calculateSignature(secretKey, random, time);
//
//        JSONObject jo = new JSONObject();
//        jo.put("remark", "");
//        jo.put("international", internationalEnum.name().equals(InternationalEnum.HOME.name()) ? 0 : 1);
//        jo.put("sig", sig);
//        jo.put("text", templateContent);
//        jo.put("time", time);
//        jo.put("title", templateName);
//        jo.put("type", 0);
//
//        try {
//            String requestUrl = "https://yun.tim.qq.com/v5/tlssmssvr/add_template?sdkappid=" + apiKey + "&random=" + random;
//            OkHttpClient okHttpClient = new OkHttpClient();
//            Response response = okHttpClient.newCall(new Request.Builder()
//                    .url(requestUrl)
//                    .post(RequestBody.create(MediaType.parse("application/json;charset=utf-8"), jo.toJSONString()))
//                    .build())
//                    .execute();
//            ResponseBody body = response.body();
//            String result = body.string();
//            log.info("result:{}", result);
//
//            JSONObject jsonObject = JSONObject.parseObject(result);
//            if (jsonObject != null && jsonObject.getInteger("result") != null && jsonObject.getInteger("result") == 0) {
//                return jsonObject.getJSONObject("data").getInteger("id") + "";
//            }
//
////            {
////                "result": 0,
////                    "errmsg": "",
////                    "data": {
////                "id": 123,
////                        "international": 0,
////                        "status": 1,
////                        "text": "xxxxx",
////                        "type": 0
////            }
////            }
//        } catch (Exception e) {
//            log.error("create template error : {}", templateName, e);
//        }
//
//        return "0";
//    }
//
//    @Override
//    public boolean verifyied(String templateId, String apiKey, String secretKey){
//
//        //https://yun.tim.qq.com/v5/tlssmssvr/add_template?sdkappid=xxxxx&random=xxxx
////
////        {
////            "sig": "c13e54f047ed75e821e698730c72d030dc30e5b510b3f8a0fb6fb7605283d7df",
////                "time": 1457336869,
////                "tpl_id": [123, 124]
////        }
//
//        long random = SmsSenderUtil.getRandom();
//        long time = SmsSenderUtil.getCurrentTime();
//        String sig = SmsSenderUtil.calculateSignature(secretKey, random, time);
//
//        JSONObject jo = new JSONObject();
//        jo.put("tpl_id", new int[]{Integer.parseInt(templateId)});
//        jo.put("sig", sig);
//        jo.put("time", time);
//
//        try {
//            String requestUrl = "https://yun.tim.qq.com/v5/tlssmssvr/get_template?sdkappid=" + apiKey + "&random=" + random;
//            OkHttpClient okHttpClient = new OkHttpClient();
//            Response response = okHttpClient.newCall(new Request.Builder()
//                    .url(requestUrl)
//                    .post(RequestBody.create(MediaType.parse("application/json;charset=utf-8"), jo.toJSONString()))
//                    .build())
//                    .execute();
//            ResponseBody body = response.body();
//            String result = body.string();
//            log.info("result:{}", result);
//
//            JSONObject jsonObject = JSONObject.parseObject(result);
//            if (jsonObject != null && jsonObject.getInteger("result") != null && jsonObject.getInteger("result") == 0) {
//                JSONArray array = jsonObject.getJSONArray("data");
//                if (array != null && !array.isEmpty()) {
//                    return array.getJSONObject(0).getInteger("status") == 0;
//                }
//            }
//
////            {
////                "result": 0,
////                    "errmsg": "",
////                    "total": 10,
////                    "count": 1,
////                    "data": [
////                {
////                    "id": 123,
////                        "international": 0,
////                        "reply": "xxxxx",
////                        "status": 0,
////                        "text": "xxxxx",
////                        "type": 0,
////                        "title": "xxxxx",
////                        "apply_time": "xxxxx",
////                        "reply_time": "xxxxx"
////                }
////    ]
////            }
//        } catch (Exception e) {
//            log.error("query template error : {}", templateId, e);
//        }
//
//        return false;
//    }
//
//    @Override
//    public boolean submitVerify(String templateId, String apiKey, String secretKey) {
//        return true;
//    }
//
//    public static void main(String[] args) {
//        new TencentSmsTemplateService().verifyied("218360", "1400101522", "60c47f8860ab245089f283bf7ebfc1ff");
//    }
//}
