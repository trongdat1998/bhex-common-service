package io.bhex.base.common.controller;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.service.EmailDeliveryRecordService;
import io.bhex.base.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class MailGunCallbackController {

    @Autowired
    private EmailDeliveryRecordService emailDeliveryRecordService;



    //{"signature": {"timestamp": "1573611553",
    // "token": "643aa3f7af958d600468e583a7ffd17ee07e3a12a9af092a01",
    // "signature": "a50afe269b3d986700b9219008b4604aa9a3338033c3f8bc0d7d14c677b532e0"},
    // "event-data": {"severity": "permanent", "tags": ["my_tag_1", "my_tag_2"],
    // "timestamp": 1521233195.375624,
    // "storage": {"url": "https://se.api.mailgun.net/v3/domains/sandboxd2bff44e72964702a4db1512384d6e9c.mailgun.org/messages/message_key", "key": "message_key"},
    // "log-level": "error", "event": "failed", "campaigns": [], "reason": "suppress-bounce", "user-variables": {"my_var_1": "Mailgun Variable #1", "my-var-2": "awesome"},
    // "flags": {"is-routed": false, "is-authenticated": true, "is-system-test": false, "is-test-mode": false}, "recipient-domain": "example.com", "envelope":
    // {"sender": "bob@sandboxd2bff44e72964702a4db1512384d6e9c.mailgun.org", "transport": "smtp", "targets": "alice@example.com"},
    // "message": {"headers": {"to": "Alice <alice@example.com>", "message-id": "20130503192659.13651.20287@sandboxd2bff44e72964702a4db1512384d6e9c.mailgun.org",
    // "from": "Bob <bob@sandboxd2bff44e72964702a4db1512384d6e9c.mailgun.org>", "subject": "Test permanent_fail webhook"}, "attachments": [], "size": 111},
    // "recipient": "alice@example.com", "id": "G9Bn5sl1TC6nu79C8C0bwg", "delivery-status": {"code": 605, "message": "", "attempt-no": 1,
    // "description": "Not delivering to previously bounced address", "session-seconds": 0}}}

    @ResponseBody
    @RequestMapping(value = "/notice/callback/mailgun/{source}")
    public String sendgridEmailCallback(@RequestBody String body, @PathVariable String source){
        log.info("source:{} body:{}", source, body);
        JsonObject eventData = JsonUtil.defaultGson().fromJson(body, JsonElement.class)
                .getAsJsonObject().getAsJsonObject("event-data");
        //JSONObject eventData = JSON.parseObject(body).getJSONObject("event-data");
        String event = eventData.get("event").getAsString();

        //log.info("source:{} eventData:{}", source, eventData);
        JsonObject deliveryStatus = eventData.getAsJsonObject("delivery-status");
        String messageId = eventData.getAsJsonObject("message").getAsJsonObject("headers").get("message-id").getAsString().split("@")[0];
        log.info("event:{} body:{} :{}", event, messageId, deliveryStatus);
        if (event.equalsIgnoreCase("delivered")) {
            emailDeliveryRecordService.updateDeliveryStatus(messageId, "SUCCESS", System.currentTimeMillis(), "OK");
        } else if (event.equalsIgnoreCase("unsubscribed")
                || event.equalsIgnoreCase("failed")
                || event.equalsIgnoreCase("complained")
                || event.equalsIgnoreCase("delivered")) {
            emailDeliveryRecordService.updateDeliveryStatus(messageId, event, System.currentTimeMillis(),
                    deliveryStatus != null ? deliveryStatus.toString() : "");
        }

        return "OK";
    }


}
