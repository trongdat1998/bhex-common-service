///*
// ************************************
// * @项目名称: bhex-common-service
// * @文件名称: SmsSender
// * @Date 2018/08/14
// * @Author will.zhao@bhex.io
// * @Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
// * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
// **************************************
// */
//package io.bhex.base.common.sender;
//
//import com.github.qcloudsms.SmsSingleSender;
//import com.github.qcloudsms.SmsSingleSenderResult;
//import io.bhex.base.common.service.SmsDeliveryRecordService;
//import io.bhex.base.common.util.ChannelConstant;
//import io.bhex.base.common.util.MaskUtil;
//import io.bhex.base.common.util.PrometheusUtil;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//
//@Slf4j
//@Deprecated
//public class SmsSender {
//
//    private static int appId = 1400101522;
//
//    private static String appKey = "60c47f8860ab245089f283bf7ebfc1ff";
//
//    private static SmsSingleSender smsSingleSender = new SmsSingleSender(appId, appKey);
//
//    @Autowired
//    private SmsDeliveryRecordService smsDeliveryRecordService;
//
//    public boolean send(Long orgId, String areaCode, String telephone, String sign, int templateId, String[] params, String bizType) {
//        try {
//            sign = sign.replace("【", "").replace("】", "");
//            SmsSingleSenderResult result = smsSingleSender.sendWithParam(areaCode, telephone,
//                    templateId, params, sign, "", "");
//            log.info("sendSms. {},{}, templateId:{} result【sid:{},result:{}, msg:{} fee:{}】",
//                    orgId, MaskUtil.maskMobile(areaCode, telephone), templateId,
//                    result.sid, result.result, result.errMsg,  result.fee);
//            smsDeliveryRecordService.insertTencentSmsDeliveryRecord(orgId, result.sid, areaCode, telephone, bizType,"");
//            PrometheusUtil.smsSendCounter(ChannelConstant.TENCENT, result.result == 0 ? 200 : 400);
//            return result.result == 0;
//        } catch (Exception e) {
//            log.error("sendSms error", e);
//        }
//        return false;
//    }
//}
