package io.bhex.base.common.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.GsonObjectUtil;
import io.bhex.base.common.util.JsonUtil;
import io.bhex.base.common.util.PrometheusUtil;
import io.bhex.base.common.util.SendChannelEnum;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
public class ZhixinCallbackController {


    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;


    //[{"statusCode":"0000","code":"B000000","msg":"成功","reportTime":"2020-06-09 18:33:42","orderId":467800561643712628,"mobile":"13829778407","smsNum":1,"isBilling":true,"merchantOrderId":"20200609-103330-360-7119-FINISH_MSG_TO_SELLER-13829778407","spno":""},{"statusCode":"0000","code":"B000000","msg":"成功","reportTime":"2020-06-09 18:33:42","orderId":467800554974769192,"mobile":"18016530719","smsNum":1,"isBilling":true,"merchantOrderId":"20200609-103328-736-7070-WITHDRAW_SUCCESS_WITH_DETAIL-180165307","spno":""}]] with root cause
    //java.lang.IllegalStateException: Not a JSON Object: [{"statusCode":"0000","code":"B000000","msg":"成功","reportTime":"2020-06-09 18:33:42","orderId":467800561643712628,"mobile":"13829778407","smsNum":1,"isBilling":true,"merchantOrderId":"20200609-103330-360-7119-FINISH_MSG_TO_SELLER-13829778407","spno":""},{"statusCode":"0000","code":"B000000","msg":"成功","reportTime":"2020-06-09 18:33:42","orderId":467800554974769192,"mobile":"18016530719","smsNum":1,"isBilling":true,"merchantOrderId":"20200609-103328-736-7070-WITHDRAW_SUCCESS_WITH_DETAIL-180165307","spno":""}]

    @RequestMapping(value = "/notice/callback/zhixin/sms_callback")
    public String smsCallback(@RequestBody String body){
        //JSONArray array = JSONArray.parseArray(body);
        JsonArray array = new JsonParser().parse(body).getAsJsonArray();
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < array.size(); i++) {
                JsonObject jo = array.get(i).getAsJsonObject();
                boolean suc = GsonObjectUtil.getAsString(jo,"code").equalsIgnoreCase("B000000")
                        && GsonObjectUtil.getAsString(jo,"statusCode").equals("0000");
                PrometheusUtil.smsDeliveryCounter(SendChannelEnum.ZHIXIN.name(), suc  ? 200 : 400);

                long deliveriedAt = DateTime.parse(GsonObjectUtil.getAsString(jo,"reportTime"),
                        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")).toDate().getTime() - 8*3600*1000;
                long createdAt = DateTime.parse(GsonObjectUtil.getAsString(jo,"merchantOrderId").substring(0,15),
                        DateTimeFormat.forPattern("yyyyMMdd-HHmmss")).toDate().getTime();
                long processMs = deliveriedAt - createdAt;

                String deliverStatus = "SUCCESS";
                if (!suc) {
                    deliverStatus = GsonObjectUtil.getAsString(jo,"code");
                    log.warn("ALERT {}", jo.toString());
                } else if (processMs > 60_000) {
                    deliverStatus = "TimeOut ProcessInSecond:" + processMs/1000;
                    log.warn("WARN process overtime : {} {}", GsonObjectUtil.getAsString(jo,"merchantOrderId"), deliverStatus);
                } else {
                    log.info("{} {}", GsonObjectUtil.getAsString(jo,"merchantOrderId"), GsonObjectUtil.getAsString(jo,"reportTime"));
                }
                boolean isBilling = GsonObjectUtil.getAsBoolean(jo, "isBilling");

                smsDeliveryRecordService.updateDeliveryStatus(GsonObjectUtil.getAsString(jo,"orderId"),
                        deliverStatus, deliveriedAt, GsonObjectUtil.getAsString(jo,"msg"), isBilling ? GsonObjectUtil.getAsInt(jo, "smsNum", 1) : 0);

            }
        });

        return "OK";
    }
}
