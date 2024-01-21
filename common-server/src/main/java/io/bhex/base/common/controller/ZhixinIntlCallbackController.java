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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
public class ZhixinIntlCallbackController {


    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;


    @RequestMapping(value = "/notice/callback/zhixinintl/sms_callback")
    public String smsCallback(String sms_status) throws Exception {
        String body = URLDecoder.decode(sms_status, "UTF-8");
        JsonArray array = new JsonParser().parse(body).getAsJsonArray();
        //JSONArray array = JSONArray.parseArray(body);

        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < array.size(); i++) {
                JsonObject jo = array.get(i).getAsJsonObject();
                boolean suc = GsonObjectUtil.getAsString(jo,"report_status").equalsIgnoreCase("SUCCESS");
                PrometheusUtil.smsDeliveryCounter(SendChannelEnum.ZHIXIN_INTL.name(), suc  ? 200 : 400);

                long deliveriedAt = DateTime.parse(GsonObjectUtil.getAsString(jo,"user_receive_time"),
                        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")).toDate().getTime() - 8*3600*1000;
                long createdAt = DateTime.parse(GsonObjectUtil.getAsString(jo,"uid").substring(0,15),
                        DateTimeFormat.forPattern("yyyyMMdd-HHmmss")).toDate().getTime();
                long processMs = deliveriedAt - createdAt;

                String deliverStatus = "SUCCESS";
                if (!suc) {
                    deliverStatus = GsonObjectUtil.getAsString(jo,"error_msg") + "" +GsonObjectUtil.getAsString(jo,"error_detail");
                    log.warn("ALERT {}", jo.toString());
                } else if (processMs > 60_000) {
                    deliverStatus = "TimeOut ProcessInSecond:" + processMs/1000;
                    log.warn("WARN process overtime : {} {}", jo.get("uid").getAsString(), deliverStatus);
                }

                smsDeliveryRecordService.updateDeliveryStatus(GsonObjectUtil.getAsString(jo,"sid"),
                        deliverStatus, deliveriedAt, GsonObjectUtil.getAsString(jo,"error_detail"), 1);

            }
        });

        return "SUCCESS";
    }
}
