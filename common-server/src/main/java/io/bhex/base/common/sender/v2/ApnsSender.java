package io.bhex.base.common.sender.v2;

import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.TextFormat;
import com.notnoop.apns.APNS;
import com.notnoop.apns.ApnsService;
import io.bhex.base.common.ApnsNotification;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.util.JsonUtil;
import io.bhex.base.common.util.SenderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("apnsSender")
public class ApnsSender {
    private static String developmentCertTypeKey = "development";

    public boolean send(SenderDTO senderDTO, ApnsNotification request) {
        log.info("begin apns send to deviceToken{}", request.getDeviceToken());
        SpInfo spInfo = senderDTO.getSpInfo();
        if (spInfo == null) {
            log.warn("send apns error with no spInfo, req:{}", TextFormat.shortDebugString(request));
            return false;
        }
        String extraInfo = spInfo.getConfigInfo();
        //JSONObject jo = JSON.parseObject(extraInfo);
        JsonObject jo = JsonUtil.defaultGson().fromJson(extraInfo, JsonElement.class).getAsJsonObject();
        String keyFile = jo.get("key_file").getAsString();
        String certType = jo.get("cert_type").getAsString();
        log.info("key_file: [{}], cert_type: [{}]", keyFile, certType);
        ApnsService apnsService;
        if (developmentCertTypeKey.equals(certType)) {
            apnsService = APNS.newService()
                    .withCert(keyFile, spInfo.getSecretKey())
                    .withSandboxDestination().build();
        } else {
            apnsService = APNS.newService()
                    .withCert(keyFile, spInfo.getSecretKey())
                    .withProductionDestination().build();
        }

        String payload = APNS.newPayload()
                .alertBody(senderDTO.getSendContent(request.getParamsList(), Maps.newHashMap()))
                .build();

        apnsService.push(request.getDeviceToken(), payload);
        log.info("apns payload:{}", payload);

        log.info("end apns send to deviceToken{}", request.getDeviceToken());
        return false;
    }
}
