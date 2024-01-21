package io.bhex.base.common.sender.v2;


import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.SimpleSMSRequest;
import io.bhex.base.common.Telephone;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @Description:
 * @Date: 2018/9/29 上午10:07
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */

@Slf4j
@Component("awsSmsSenderV2")
public class AwsSmsSenderV2 implements ISmsSender{

    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;

    private static Map<Integer,List<ClientInfo>> snsClientMap = new HashMap<>();
    private Map<Integer,AtomicInteger> snsCounter = new HashMap<>();

    @Data
    private static class ClientInfo {
        private String account;
        private String region;
        private AmazonSNSClient snsClient;
    }


    public ClientInfo getClient(Long orgId, SpInfo spInfo) {

        if (snsClientMap.containsKey(spInfo.getId())) {
            List<ClientInfo> clients = snsClientMap.get(spInfo.getId());
            return clients.get(snsCounter.get(spInfo.getId()).incrementAndGet()%clients.size());
        }

        String extraInfo = spInfo.getConfigInfo();
        JsonObject jo = JsonUtil.defaultGson().fromJson(extraInfo, JsonElement.class).getAsJsonObject();
        //JSONObject jo = JSON.parseObject(extraInfo);
        //List<String> regions = jo.getAsJsonArray("regions").getAsString().toJavaList(String.class);
        List<String> regions = Lists.newArrayList(jo.getAsJsonArray("regions")).stream().map(e -> e.getAsString()).collect(Collectors.toList());
//{"regions":["us-east-1","us-west-2","ap-northeast-1","ap-southeast-1","ap-southeast-2","eu-west-1"],"reportBucket":"sms.account.bhex.com","aws_account":"broker.bhop"}

        List<ClientInfo> snsClients = new ArrayList<>();
        AWSCredentials awsCredentials = new BasicAWSCredentials(spInfo.getAccessKeyId(), spInfo.getSecretKey());
        for(String region : regions){
            ClientInfo clientInfo = new ClientInfo();
            AmazonSNSClient snsClient = new AmazonSNSClient(awsCredentials).withRegion(Regions.fromName(region));
            clientInfo.setAccount(jo.get("aws_account").getAsString());
            clientInfo.setRegion(region);
            clientInfo.setSnsClient(snsClient);
            snsClients.add(clientInfo);
        }
        snsCounter.put(spInfo.getId(), new AtomicInteger(0));
        snsClientMap.put(spInfo.getId(), snsClients);

        return snsClientMap.get(spInfo.getId()).get(0);
    }



    public boolean send(Long orgId, SenderDTO senderDTO, SimpleSMSRequest request) throws IOException {
        //try {
            Telephone telephone = request.getTelephone();

            String content = senderDTO.getSendContent(request.getParamsList(), request.getReqParamMap());
            SpInfo spInfo = senderDTO.getSpInfo();

            String message;
            if (StringUtils.isNotEmpty(senderDTO.getOriginSignName())) {
                if (!request.getLanguage().equals("zh_CN")) {
                    message = "[" + senderDTO.getOriginSignName() + "]" + content;
                } else {
                    message = senderDTO.getSignName() + content;
                }
            } else {
                message = content;
            }


            message = StringUtils.rightPad(message, 70);//右边补空格，防止aws在短信内容后面缀上 [aws]
            sendSMSMessage(orgId, spInfo, message, telephone.getNationCode(),
                    telephone.getMobile(), senderDTO.getTempMessageId());
            return true;
//        } catch (Exception e) {
//            log.error("sendSms error", e);
//        }
        //return false;
    }

    private void sendSMSMessage(Long orgId, SpInfo spInfo, String message,
                                      String areaCode, String telephone, String tempMessageId) {

        Map<String, MessageAttributeValue> smsAttributes = new HashMap<>();
        smsAttributes.put("AWS.SNS.SMS.SMSType", new MessageAttributeValue()
                .withStringValue("Transactional") //Sets the type to Transactional.
                .withDataType("String"));

        if (telephone.startsWith("0")) {
            telephone = telephone.replaceFirst("0", "");
        }

        String phoneNumber = "+" + areaCode + telephone;

        ClientInfo clientInfo = getClient(orgId, spInfo);
        PublishResult result = clientInfo.getSnsClient()
                .publish(new PublishRequest()
                .withMessage(message).withPhoneNumber(phoneNumber)
                .withMessageAttributes(smsAttributes));


        log.info("sendSms sp:{} account:{} region:{} orgId:{} {}, content:{} {}", spInfo.getId(), clientInfo.getAccount(), clientInfo.getRegion(),
                orgId, MaskUtil.maskMobile(areaCode, telephone), MaskUtil.maskValidateCode(message), result);

        smsDeliveryRecordService.updateTempMessageId(tempMessageId, result.getMessageId(), "UNKNOWN", "");

        PrometheusUtil.smsSendCounter(ChannelConstant.AWS, 200);
        PrometheusUtil.smsDeliveryCounter(ChannelConstant.AWS, 200);
    }

}
