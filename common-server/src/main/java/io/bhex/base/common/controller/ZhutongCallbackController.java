package io.bhex.base.common.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.GsonObjectUtil;
import io.bhex.base.common.util.PrometheusUtil;
import io.bhex.base.common.util.SendChannelEnum;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
public class ZhutongCallbackController {


    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;


    @RequestMapping(value = "/notice/callback/zhutong/sms_callback")
    public String smsCallback(@RequestBody String body){
        //JSONArray array = JSONArray.parseArray(body);
        JsonArray array = new JsonParser().parse(body).getAsJsonArray();
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < array.size(); i++) {
                JsonObject jo = array.get(i).getAsJsonObject();
                boolean suc = GsonObjectUtil.getAsString(jo,"code").equalsIgnoreCase("DELIVRD");
                PrometheusUtil.smsSendCounter(SendChannelEnum.ZHUTONG.name(), suc  ? 200 : 400);

                long deliveriedAt = DateTime.parse(GsonObjectUtil.getAsString(jo,"reportTime"),
                        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")).toDate().getTime() - 8*3600*1000;
                long createdAt = DateTime.parse(GsonObjectUtil.getAsString(jo,"extend").substring(0,15),
                        DateTimeFormat.forPattern("yyyyMMdd-HHmmss")).toDate().getTime();
                long processMs = deliveriedAt - createdAt;

                String deliverStatus = "SUCCESS";
                if (!suc) {
                    deliverStatus = GsonObjectUtil.getAsString(jo,"code");
                    log.warn("ALERT {}", jo.toString());
                } else if (processMs > 60_000) {
                    deliverStatus = "TimeOut ProcessInSecond:" + processMs/1000;
                    log.warn("WARN process overtime : {} {}", jo.get("extend").getAsString(), deliverStatus);
                } else {
                    log.info("{} {}", GsonObjectUtil.getAsString(jo,"extend"), GsonObjectUtil.getAsString(jo,"reportTime"));
                }

                smsDeliveryRecordService.updateDeliveryStatus(GsonObjectUtil.getAsString(jo,"msgId"),
                        deliverStatus, deliveriedAt, GsonObjectUtil.getAsString(jo,"code"));

            }
        });

        return "OK";
    }

    //https://www.bit-e.com/notice/callback/zhutong_intl/sms_callback
    @RequestMapping(value = "/notice/callback/zhutong_intl/sms_callback")
    public String intlSmsCallback(@RequestParam String param){
//202003062038053642255,1,2017309831,1,20200306203805;
        //201612190800030411734,15136707387,black,20161219080940
        CompletableFuture.runAsync(() -> {
            String[] lines = param.split(";");
            for (String line : lines) {
                String[] l = line.split(",");
                String msgId = l[0];
                String status = l[3];

                long deliveriedAt = DateTime.parse(l[4],
                        DateTimeFormat.forPattern("yyyyMMddHHmmss")).toDate().getTime() - 8*3600*1000;
                smsDeliveryRecordService.updateDeliveryStatus(msgId, status.equals("1") ? "SUCCESS" : status,
                        deliveriedAt, status);


            }
        });

        return "0";
    }

    public static void main(String[] args) {
        String lp = "201612190800030411734,15136707387,black,20161219080940;";
        String[] lines = lp.split(";");
        for (String line : lines) {
            System.out.println(line);
            String[] l = line.split(",");
            String msgId = l[0];
            String status = l[2];
            long deliveriedAt = DateTime.parse(l[3],
                    DateTimeFormat.forPattern("yyyyMMddHHmmss")).toDate().getTime() - 8*3600*1000;

            System.out.println(msgId+"=====");
        }
    }
}
