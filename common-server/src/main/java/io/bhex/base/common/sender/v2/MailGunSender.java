package io.bhex.base.common.sender.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.TextFormat;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import io.bhex.base.common.SimpleMailRequest;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.EmailDeliveryRecordService;
import io.bhex.base.common.util.GsonObjectUtil;
import io.bhex.base.common.util.JsonUtil;
import io.bhex.base.common.util.MaskUtil;
import io.bhex.base.common.util.SenderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class MailGunSender   implements IEmailSender{

    @Autowired
    private EmailDeliveryRecordService emailDeliveryRecordService;

    @Override
    public boolean send(Long orgId, SenderDTO senderDTO, SimpleMailRequest request) throws IOException {
        String content = senderDTO.getEmailSendContent(request.getParamsList(), request.getReqParamMap());
        SpInfo spInfo = senderDTO.getSpInfo();
        String extraInfo = spInfo.getConfigInfo();

        //JSONObject jo = JSON.parseObject(extraInfo);

        JsonObject jo = JsonUtil.defaultGson().fromJson(extraInfo, JsonElement.class).getAsJsonObject();

        String subject = senderDTO.getEmailSubject();
        String to = request.getMail();

        try {
            HttpResponse<JsonNode> gunRequest = Unirest.post("https://api.mailgun.net/v3/" + GsonObjectUtil.getAsString(jo,"domain") + "/messages")
                    .basicAuth("api", spInfo.getSecretKey())
                    .field("from", GsonObjectUtil.getAsString(jo,"from"))
                    .field("to", to)

                    .field("subject", subject)
                    .field("html", content)
                    .asJson();
            JsonNode jsonNode = gunRequest.getBody();
            log.info("node:{}", jsonNode.toString());
            String sid = jsonNode.getObject().getString("id").split("@")[0].substring(1);

            emailDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), sid,
                    gunRequest.getStatus() == 200 ? "request-200" : gunRequest.getStatus() + "-" + gunRequest.getStatusText(),
                    gunRequest.getStatusText());

            log.info("succ sendMailGun sp:{} orgId:{} to:{} content:{} result:{}", spInfo.getId(), orgId, MaskUtil.maskEmail(request.getMail()),
                    MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)), jsonNode.toString());
        } catch (Exception ex) {
            log.info("fail sendMailGun sp:{} orgId:{} to:{} bizType:{}",
                    spInfo.getId(), orgId, MaskUtil.maskEmail(request.getMail()), request.getBusinessType(), ex);
            throw new IOException(ex);
        }
        return true;
    }

}
