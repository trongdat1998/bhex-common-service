package io.bhex.base.common.sender.v2;

import com.github.qcloudsms.SmsSingleSender;
import com.github.qcloudsms.SmsSingleSenderResult;
import com.github.qcloudsms.SmsVoiceVerifyCodeSender;
import com.github.qcloudsms.SmsVoiceVerifyCodeSenderResult;
import io.bhex.base.common.SimpleSMSRequest;
import io.bhex.base.common.Telephone;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
public class TencentSender  implements ISmsSender{

    private static Map<Integer, SmsSingleSender> senderMap = new HashMap<>();

    private static Map<Integer, SmsVoiceVerifyCodeSender> voiceSenderMap = new HashMap<>();

    private SmsSingleSender getSmsSender(long orgId, SpInfo spInfo) {
        if (senderMap.containsKey(spInfo.getId())) {
            return senderMap.get(spInfo.getId());
        }
        SmsSingleSender smsSingleSender = new SmsSingleSender(Integer.parseInt(spInfo.getAccessKeyId()),
                spInfo.getSecretKey());
        senderMap.put(spInfo.getId(), smsSingleSender);
        return smsSingleSender;
    }

    private SmsVoiceVerifyCodeSender getVoiceSmsSender(long orgId, SpInfo spInfo) {
        if (senderMap.containsKey(spInfo.getId())) {
            return voiceSenderMap.get(spInfo.getId());
        }
        SmsVoiceVerifyCodeSender voiceSender = new SmsVoiceVerifyCodeSender(Integer.parseInt(spInfo.getAccessKeyId()),
                spInfo.getSecretKey());
        voiceSenderMap.put(spInfo.getId(), voiceSender);
        return voiceSender;
    }


    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;

    public boolean send(Long orgId, SenderDTO senderDTO, SimpleSMSRequest request) throws IOException {
        try {
            List<String> params = request.getParamsList();
            Telephone telephone = request.getTelephone();
            String sign = senderDTO.getOriginSignName();
            Integer templateId = Integer.parseInt(senderDTO.getTargetTmplId());
            SmsSingleSenderResult result = getSmsSender(orgId, senderDTO.getSpInfo()).sendWithParam(telephone.getNationCode(), telephone.getMobile(),
                    templateId, params.toArray(new String[params.size()]), sign, "", "");
            log.info("sendSms. sp:{} org:{},{}, templateId:{} result【sid:{},result:{}, msg:{} fee:{}】", senderDTO.getSpInfo().getId(),
                    orgId, MaskUtil.maskMobile(telephone.getNationCode(), telephone.getMobile()), templateId,
                    result.sid, result.result, result.errMsg,  result.fee);
            smsDeliveryRecordService.insertSmsDeliveryRecord(orgId, SendChannelEnum.TENCENT, result.sid, telephone.getNationCode(),
                    telephone.getMobile(),
                    result.result == 0 ? "request-200" : result.errMsg + "",
                    result.errMsg,
                    senderDTO.getScenario(),
                    request.getLanguage()+" "+request.getBusinessType(), senderDTO.getSpInfo().getId(), "", 0L);
            PrometheusUtil.smsSendCounter(ChannelConstant.TENCENT, result.result == 0 ? 200 : 400);
            return result.result == 0;
        } catch (Exception e) {
            //log.error("sendSms error", e);
            throw new IOException(e);
        }
    }

    public  boolean sendVoiceSms(Long orgId, SenderDTO senderDTO,String areaCode, String telephone, String code) {
        try {
            SmsVoiceVerifyCodeSenderResult result = getVoiceSmsSender(orgId, senderDTO.getSpInfo()).send(areaCode, telephone,
                    code, 2, "");
            log.info("result:{}", result);
            return result.result == 0;
        } catch (Exception e) {
            log.error("sendVoiceSms error", e);
        }
        return false;
    }
}


