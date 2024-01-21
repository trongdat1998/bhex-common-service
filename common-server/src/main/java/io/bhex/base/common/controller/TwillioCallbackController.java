package io.bhex.base.common.controller;

import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.twilio.rest.api.v2010.account.Message;
import io.bhex.base.common.service.EmailDeliveryRecordService;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.GsonObjectUtil;
import io.bhex.base.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
public class TwillioCallbackController {

//    queued
//            failed
//    sent
//            delivered
//    undelivered

    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;
    @Autowired
    private EmailDeliveryRecordService emailDeliveryRecordService;

    @ResponseBody
    @RequestMapping(value = "/notice/callback/twilio/smscallback")
    public String smsCallback(HttpServletRequest request){

        String sid = request.getParameter("MessageSid");

        Message.Status status = Message.Status.forValue(request.getParameter("SmsStatus"));
        if (status == Message.Status.FAILED || status == Message.Status.DELIVERED || status == Message.Status.UNDELIVERED) {
            smsDeliveryRecordService.updateDeliveryStatus(sid,
                    status == Message.Status.DELIVERED ? "SUCCESS" : status.name(), System.currentTimeMillis(), status.name());
        }

        if (status == Message.Status.FAILED || status == Message.Status.UNDELIVERED) {
            log.warn("ALERT sid:{} to:{} status:{}", sid, request.getParameter("To"), status.name());
        } else {
            log.info("sid:{} to:{} status:{}", sid, request.getParameter("To"), status.name());
        }

        return "OK";
    }

    //[
    // {"email":"remesa@amyalysonfans.com",
    // "event":"open",
    // "ip":"54.238.100.151",
    // "sg_content_type":"html",
    // "sg_event_id":"zVYr-i7TTuWGWkKaLPGLTw",
    // "sg_message_id":"MnVwPLWQSzem6MsqxHAOTg.filter0107p3las1-10303-5DCA67D9-45.0",
    // "timestamp":1573545955,
    // "useragent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.87 Safari/537.36"}]

    @ResponseBody
    @RequestMapping(value = "/notice/callback/sendgrid/{source}")
    public String sendgridEmailCallback( @RequestBody String body, @PathVariable String source){
        JsonArray jsonArray = new JsonParser().parse(body).getAsJsonArray();
        //JSONArray jsonArray = JSON.parseArray(body);
        for (int i = 0 ; i< jsonArray.size(); i++) {
            JsonObject jo = jsonArray.get(i).getAsJsonObject();
            //JSONObject jo = jsonArray.getJSONObject(i);
            String event = GsonObjectUtil.getAsString(jo,"event");
            String sid = GsonObjectUtil.getAsString(jo,"sg_message_id").split("\\.filter")[0];
            String response = GsonObjectUtil.getAsString(jo,"response")  + GsonObjectUtil.getAsString(jo,"reason");
            if (event.equalsIgnoreCase("delivered")) {
                emailDeliveryRecordService.updateDeliveryStatus(sid, "SUCCESS", System.currentTimeMillis(), response);
            } else if (event.equalsIgnoreCase("deferred")
                    || event.equalsIgnoreCase("bounce")
                    || event.equalsIgnoreCase("spamreport")
                    || event.equalsIgnoreCase("unsubscribe")
                    || event.equalsIgnoreCase("group_unsubscribe")
                    || event.equalsIgnoreCase("group_resubscribe")) {
                emailDeliveryRecordService.updateDeliveryStatus(sid, event, System.currentTimeMillis(), response);
            }

            log.info("event:{} sid:{} repsonse:{}", event, sid, response);
        }

        return "OK";
    }


    @ResponseBody
    @RequestMapping(value = "/notice/callback/xxxx/{source}")
    public String sendgridEmailCallback( @RequestBody String body){
        log.info(body);
        return "OK";
    }
}
