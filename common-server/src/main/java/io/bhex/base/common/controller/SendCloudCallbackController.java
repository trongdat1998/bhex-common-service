package io.bhex.base.common.controller;

import com.google.common.base.Strings;
import io.bhex.base.common.config.SendCloudEmailProperties;
import io.bhex.base.common.config.SendCloudSmsProperties;
import io.bhex.base.common.service.EmailDeliveryRecordService;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.service.impl.ServiceProviderService;
import io.bhex.base.common.util.ChannelConstant;
import io.bhex.base.common.util.PrometheusUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@RestController
public class SendCloudCallbackController {


    @Autowired
    private EmailDeliveryRecordService emailDeliveryRecordService;

    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;
//    public boolean verify(String appkey, String token, long timestamp,
//                          String signature) throws NoSuchAlgorithmException, InvalidKeyException {
//        Mac sha256HMAC = Mac.getInstance("HmacSHA256");
//        SecretKeySpec secretKey = new SecretKeySpec(appkey.getBytes(),"HmacSHA256");
//        sha256HMAC.init(secretKey);
//        StringBuffer buf = new StringBuffer();
//        buf.append(timestamp).append(token);
//        String signatureCal = new String(Hex.encodeHex(sha256HMAC.doFinal(buf.toString().getBytes())));
//        return signatureCal.equals(signature);
//    }

    @RequestMapping(value = "/notice/callback/sendcloud/email_callback/{type}")
    public String emailCallback(
            @PathVariable String type,@RequestParam(required = false) String event,
                                @RequestParam(required = false) String token,
                                @RequestParam(required = false) Long timestamp,
                                @RequestParam(required = false) String signature,
                                @RequestParam(required = false) String emailId,
                                @RequestParam(required = false)  String recipient,
                                HttpServletRequest request){
        try {
            String msg = "";
            if(event.equals("deliver")){
                PrometheusUtil.emailDeliveryCounter(ChannelConstant.SEND_CLOUD, 200);
                log.info("{} event:{} recipient:{} emailId:{} timestamp:{}", type, event, recipient, emailId, timestamp);
            }
            else if(event.equals("open") || event.equals("click") || event.equals("unsubscribe") || event.equals("route")){
                log.info("{} event:{} recipient:{} emailId:{} timestamp:{}", event, recipient, emailId, timestamp);
            }
            else if(event.equals("invalid")){
                msg = request.getParameter("message") + request.getParameter("subStat") + "-" + request.getParameter("subStatDesc");
                log.info("WARN {} event:{} emailId:{} recipient:{} message:{}", type, event, emailId, recipient, msg);
            }
            else if(event.equals("report_spam") || event.equals("soft_bounce")){
                PrometheusUtil.emailDeliveryCounter(ChannelConstant.SEND_CLOUD, 400);
                log.info("WARN {} event:{} emailId:{} recipient:{} timestamp:{}", type, event, emailId, recipient, timestamp);
            } else {
                log.info("{} event:{} emailId:{} recipient:{} timestamp:{}", type, event, emailId, recipient, timestamp);
            }

            //if(event.equals("deliver") || event.equals("report_spam") || event.equals("invalid") || event.equals("soft_bounce")) {
            emailDeliveryRecordService.updateDeliveryStatus(emailId,
                    event.equals("deliver") ? "SUCCESS" : event, timestamp, msg);
            //}
        } catch (Exception e) {
            log.warn("ignore exeception", e);
        }

        return "OK";
    }


//    请求(request)	邮件请求成
    //    打开(open)	用户打开邮件
//    点击(click)	用户点击链接

//    取消订阅(unsubscribe)	用户取消订阅邮件
//    发送(deliver)	邮件发送成功
//    举报(report_spam)	用户举报邮件
//    无效邮件(invalid)	邮件未发送成功
//    软退信(soft_bounce)	接收方拒收该邮件

//    转信(route)	转信/收信路由


    @RequestMapping(value = "/notice/callback/sendcloud/sms_callback/{orgId}")
    public String smsCallback(@PathVariable String orgId, @RequestParam(required = false) String event, @RequestParam(required = false) String token,
                              @RequestParam(required = false) Long timestamp, @RequestParam(required = false) String signature,
                              @RequestParam(required = false) String phone, @RequestParam(required = false)  String smsId,
                              @RequestParam(required = false)  String message, @RequestParam(required = false)  Integer templateId){
        event = Strings.nullToEmpty(event);
        if (event.equals("deliver")) {
            PrometheusUtil.smsSendCounter(ChannelConstant.SEND_CLOUD, 200);
            smsDeliveryRecordService.updateDeliveryStatus(smsId, "SUCCESS", timestamp, message);
            log.info("org:{} event:{} sid:{} phone:{} templateId:{}", orgId, event, smsId, phone, templateId);
        } else if (event.equals("workererror") || event.equals("delivererror")) {
            PrometheusUtil.smsSendCounter(ChannelConstant.SEND_CLOUD, 400);
            smsDeliveryRecordService.updateDeliveryStatus(smsId, event, timestamp, message);
            log.warn("ALERT org{} :event:{} sid:{} phone:{} templateId:{} message:{}", orgId, event, smsId, phone, templateId, message);
        }



        return "OK";
    }
}
