package io.bhex.base.common.sender.v2;


import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.TextFormat;
import io.bhex.base.common.SimpleMailRequest;
import io.bhex.base.common.config.EmailConfig;
import io.bhex.base.common.entity.SpAccountInfo;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.EmailDeliveryRecordService;
import io.bhex.base.common.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeUtility;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AwsEmailSenderV2  implements IEmailSender{


    @Autowired
    private EmailDeliveryRecordService emailDeliveryRecordService;

    Map<Integer, AmazonSimpleEmailService> emailClientMap = new HashMap<>();

    public AmazonSimpleEmailService getClient(Long orgId, SpInfo spInfo) {
        if (emailClientMap.containsKey(spInfo.getId())) {
            return emailClientMap.get(spInfo.getId());
        }
        String extraInfo = spInfo.getConfigInfo();
        //JSONObject jo = JSON.parseObject(extraInfo);
        JsonObject jo = JsonUtil.defaultGson().fromJson(extraInfo, JsonElement.class).getAsJsonObject();
        AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                .withRegion(Regions.fromName(jo.get("region_name").getAsString()))
                .withCredentials(new AWSCredentialsProvider(){
                    @Override
                    public AWSCredentials getCredentials() {
                        return new AWSCredentials() {
                            @Override
                            public String getAWSAccessKeyId() {
                                return spInfo.getAccessKeyId();
                            }

                            @Override
                            public String getAWSSecretKey() {
                                return spInfo.getSecretKey();
                            }
                        };
                    }

                    @Override
                    public void refresh() {

                    }
                })
                .build();
        emailClientMap.put(spInfo.getId(), client);
        return client;
    }


    @Override
    public boolean send(Long orgId, SenderDTO senderDTO, SimpleMailRequest request) throws IOException {
        String content = senderDTO.getEmailSendContent(request.getParamsList(), request.getReqParamMap());
        if (request.getBusinessType().equals("PROMOTION")) {
            content = senderDTO.getSendContent(request.getParamsList(), request.getReqParamMap());
        }
        String to = request.getMail();
       // try{

            String sign = senderDTO.getSignName();
            if(StringUtils.isNoneEmpty(sign)){
                sign = MimeUtility.encodeText(sign, "utf-8", "Q");
            }

            SpInfo spInfo = senderDTO.getSpInfo();
            String extraInfo = spInfo.getConfigInfo();
            //JSONObject jo = JSON.parseObject(extraInfo);
            JsonObject jo = JsonUtil.defaultGson().fromJson(extraInfo, JsonElement.class).getAsJsonObject();

            String from = sign + "<" + jo.get("from_address").getAsString() + ">";
            SendEmailRequest sendEmailRequest = new SendEmailRequest()
                    .withDestination(
                            new Destination().withToAddresses(to))
                    .withMessage(new Message()
                            .withBody(new Body()
                                    .withHtml(new Content()
                                            .withCharset("UTF-8").withData(content)))
                            .withSubject(new Content()
                                    .withCharset("UTF-8").withData(senderDTO.getEmailSubject())))
                    .withSource(from);

            SendEmailResult result = getClient(orgId, spInfo).sendEmail(sendEmailRequest);
            log.info("succ sendAwsMail sp:{} orgId:{} to:{} content:{} result:{}", senderDTO.getSpInfo().getId(), orgId, MaskUtil.maskEmail(to),
                    MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)), result);

            String sid = result.getMessageId();

            emailDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), sid, "request-200", "");

            PrometheusUtil.emailSendCounter(ChannelConstant.AWS, 200);

//        } catch (Exception e) {
//            log.warn("fail send simple mail {} {} content:{}", orgId, MaskUtil.maskEmail(to),
//                    MaskUtil.maskValidateCode(content), e);
//            return false;
//        }
        return false;
    }

}
