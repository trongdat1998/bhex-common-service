package io.bhex.base.common.controller;

import com.google.gson.reflect.TypeToken;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.ChannelConstant;
import io.bhex.base.common.util.JsonUtil;
import io.bhex.base.common.util.PrometheusUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class TencentCallbackController {


    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;

    @ResponseBody
    @RequestMapping(value = "/notice/callback/tencent/smscallback")
    public Map smsCallback(@RequestBody String json){

        List<SmsCallbackInfo> callbackInfos = JsonUtil.defaultGson().fromJson(json, new TypeToken<List<SmsCallbackInfo>>() {}.getType());
        for(SmsCallbackInfo info : callbackInfos){
            log.info("TencentSmsCallbackInfo :{}", info);

            Long deliveriedAt = null;
            if (!info.getReportStatus().equals("SUCCESS")) {
                PrometheusUtil.smsSendCounter(ChannelConstant.TENCENT, 400);
                log.warn("ALERT mobile:{} sid:{} errmsg:{} description:{}", info.getMobile(), info.getSid(), info.getErrmsg(), info.getDescription());
            } else{
                PrometheusUtil.smsSendCounter(ChannelConstant.TENCENT, 200);
                //返回来的是北京时间 要减去8个小时
                deliveriedAt = DateTime.parse(info.getUserReceiveTime(),
                        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")).toDate().getTime() - 8*3600*1000;
            }

            smsDeliveryRecordService.updateDeliveryStatus(info.getSid(), info.getReportStatus(), deliveriedAt,
                    info.getErrmsg()+" "+ info.getDescription());

        }
        Map<String,Object> result = new HashMap<>();
        result.put("result",0);
        result.put("errmsg","OK");
        return result;
    }


    @Data
    private static class SmsCallbackInfo{
        private String userReceiveTime;
        private String nationcode;
        private String mobile;
        private String reportStatus;
        private String errmsg;
        private String description;
        private String sid;
    }
}
