package io.bhex.base.common.sender.v2;

import com.google.protobuf.TextFormat;
import com.sendgrid.*;
import io.bhex.base.common.SimpleMailRequest;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.EmailDeliveryRecordService;
import io.bhex.base.common.util.JsonUtil;
import io.bhex.base.common.util.MaskUtil;
import io.bhex.base.common.util.SenderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
public class TwilioEmailSender  implements IEmailSender{


    @Autowired
    private EmailDeliveryRecordService emailDeliveryRecordService;


    @Override
    public boolean send(Long orgId, SenderDTO senderDTO, SimpleMailRequest request) throws IOException {
        String content = senderDTO.getEmailSendContent(request.getParamsList(), request.getReqParamMap());
        SpInfo spInfo = senderDTO.getSpInfo();
        String extraInfo = spInfo.getConfigInfo();
        Map<String, String> jo = JsonUtil.defaultGson().fromJson(extraInfo, Map.class);

        Email from = new Email(jo.get("from"));
        String subject = senderDTO.getEmailSubject();
        Email to = new Email(request.getMail());
        Content gridContent = new Content("text/html", content);
        Mail mail = new Mail(from, subject, to, gridContent);

        SendGrid sg = new SendGrid(spInfo.getSecretKey());
        Request gridRequest = new Request();
        try {
            gridRequest.setMethod(Method.POST);
            gridRequest.setEndpoint("mail/send");
            gridRequest.setBody(mail.build());

            Response response = sg.api(gridRequest);

            String sid = response.getHeaders().get("X-Message-Id");
            emailDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), sid, "request-200", "");

            log.info("succ sendGridMail sp:{} orgId:{} to:{} content:{} result:{}", spInfo.getId(), orgId, MaskUtil.maskEmail(request.getMail()),
                    MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)), response.getHeaders());
        } catch (IOException ex) {
            log.info("fail sendCloudMail sp:{} orgId:{} to:{} bizType:{}",
                    spInfo.getId(), orgId, MaskUtil.maskEmail(request.getMail()), request.getBusinessType());
            throw new IOException(ex);
        }
        return true;
    }
}
