package io.bhex.base.common.controller;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.service.EmailDeliveryRecordService;
import io.bhex.base.common.util.GsonObjectUtil;
import io.bhex.base.common.util.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@RestController
public class AwsCallbackController {

    @Autowired
    private EmailDeliveryRecordService emailDeliveryRecordService;



    @RequestMapping(value = "/notice/callback/aws/email_callback/{region}")
    public String emailCallback(HttpServletRequest request, @PathVariable String region, @RequestBody String j){
//        Enumeration<String> names = request.getHeaderNames();
//        while (names.hasMoreElements()) {
//            String key = names.nextElement();
//            log.info(key+" "+request.getHeader(key));
//        }

        try {
            //log.info("{}", j);
            JsonObject joObject = JsonUtil.defaultGson().fromJson(j, JsonElement.class).getAsJsonObject();
            String type = GsonObjectUtil.getAsString(joObject, "Type");
            if (StringUtils.isNotEmpty(type) && !type.equals("Notification")) {
                log.info("{}", j);
                return "ok";
            }

            MessageData messageData = StringUtils.isNotEmpty(type) ? processCommenMessage(joObject) : processNoHeaderMessage(joObject);

            emailDeliveryRecordService.updateDeliveryStatus(messageData.getMessageId(),
                    messageData.getStatus(), messageData.getDeliveriedAt().getTime(), "");
            log.info("region:{} messageId:{} status:{} processMs:{}", region,
                    messageData.getMessageId(), messageData.getStatus(), messageData.getProcessMs());

        } catch (Exception e) {
            log.warn("aws callback error : {}", j, e);
        }
        return "ok";
    }

    @Data
    @AllArgsConstructor
    private static class MessageData {

        private String messageId;
        private Date deliveriedAt;
        private String status;
        private long processMs;
    }

    public MessageData processCommenMessage(JsonObject joObject) {
        JsonObject jo = JsonUtil.defaultGson().fromJson(GsonObjectUtil.getAsString(joObject, "Message"), JsonElement.class).getAsJsonObject();

        String notificationType = jo.get("notificationType").getAsString();
        JsonObject mailjo = jo.getAsJsonObject("mail");
        String messageId = mailjo.get("messageId").getAsString();
        long processMs = 0;
        String status = "";
        Date deliveriedAt = new Date();
        if (notificationType.equalsIgnoreCase("Delivery")) {
            JsonObject djo = jo.getAsJsonObject("delivery");
            deliveriedAt = getDateFromISO(djo.get("timestamp").getAsString());
            status = "SUCCESS";
            processMs = djo.get("processingTimeMillis").getAsInt();
        } else  {
            status = notificationType;
        }
        return new MessageData(messageId, deliveriedAt, status, processMs);
    }

    public MessageData processNoHeaderMessage(JsonObject jo) {
        String notificationType = jo.get("notificationType").getAsString();
        JsonObject mailjo = jo.getAsJsonObject("mail");
        String messageId = mailjo.get("messageId").getAsString();
        String status = "";
        long processMs = 0;
        Date deliveriedAt = new Date();
        if (notificationType.equalsIgnoreCase("Delivery")) {
            JsonObject djo = jo.getAsJsonObject("delivery");
            deliveriedAt = getDateFromISO(djo.get("timestamp").getAsString());
            status = "SUCCESS";
            processMs = djo.get("processingTimeMillis").getAsInt();
        } else {
            status = notificationType;
        }
        return new MessageData(messageId, deliveriedAt, status, processMs);
    }

    public static Date getDateFromISO(String isoDate){
        DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        try {
            return sdf.parse(isoDate);
        } catch (ParseException e) {
            return null;
        }
    }


}
