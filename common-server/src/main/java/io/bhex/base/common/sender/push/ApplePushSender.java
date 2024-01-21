package io.bhex.base.common.sender.push;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import com.google.api.client.util.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.entity.AppPushRecord;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.mapper.AppPushRecordMapper;
import io.bhex.base.common.service.impl.SimpleMessageService;
import io.bhex.base.common.util.AESUtil;
import io.bhex.base.common.util.GsonObjectUtil;
import io.bhex.base.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ApplePushSender extends BasePushSender implements IPushSender {

    @Resource
    private SimpleMessageService simpleMessageService;
    @Resource
    private AppPushRecordMapper appPushRecordMapper;

    private final static ConcurrentHashMap<Integer, ApnsClient> apnsClientMap = new ConcurrentHashMap<>();

    public ApnsClient getClient(Long orgId, SpInfo spInfo) {
        if (apnsClientMap.containsKey(spInfo.getId())) {
            return apnsClientMap.get(spInfo.getId());
        }

        String extraInfo = spInfo.getConfigInfo();
        JsonObject jo = JsonUtil.defaultGson().fromJson(extraInfo, JsonElement.class).getAsJsonObject();

        String certType = GsonObjectUtil.getAsString(jo, "cert_type");
        log.info("cert_type: [{}]", certType);
        byte[] bytes = Base64.decodeBase64(spInfo.getSecretKey());
        byte[] decryptFrom = AESUtil.parseHexStr2Byte(spInfo.getAccessKeyId());
        byte[] password = AESUtil.decrypt(decryptFrom, spInfo.getChannel() + GsonObjectUtil.getAsString(jo, "salt"));

        ApnsClient apnsClient = null;
        try {
            apnsClient = new ApnsClientBuilder()
                    .setApnsServer(developmentCertTypeKey.equals(certType) ? ApnsClientBuilder.DEVELOPMENT_APNS_HOST : ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setClientCredentials(new ByteArrayInputStream(bytes), new String(password))
                    .build();
            apnsClientMap.put(spInfo.getId(), apnsClient);
        } catch (Exception e) {
            log.error("create pushy client error", e);
        }
        return apnsClient;
    }

    private static String developmentCertTypeKey = "development";

    public boolean sendBusinessPush(AppPushRecord pushRecord) {
        return send(pushRecord);
    }

    @Override
    public boolean sendPush(AppPushRecord pushRecord) {
        return send(pushRecord);
    }

    private boolean send(AppPushRecord pushRecord) {
        SpInfo spInfo = simpleMessageService.getSpInfo(pushRecord.getSpId());
        if (spInfo == null) {
            log.warn("send apns error with no spInfo, req:{}", pushRecord);
            return false;
        }

        ApnsClient apnsClient = getClient(pushRecord.getOrgId(), spInfo);
        if (apnsClient == null) {
            return false;
        }
        final ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
        payloadBuilder.setAlertTitle(pushRecord.getPushTitle())
                .setAlertBody(pushRecord.getPushContent())
                .setSound(ApnsPayloadBuilder.DEFAULT_SOUND_FILENAME);
        Map<String, String> customerData = PushCustomDataDTO.convertMap(getUrlData(pushRecord));
        for (String key : customerData.keySet()) {
            payloadBuilder.addCustomProperty(key, customerData.get(key));
        }

        String payload = payloadBuilder.build();
        List<String> pushTokens = Arrays.asList(pushRecord.getPushTokens().split(","));
        List<SimpleApnsPushNotification> notifications = Lists.newArrayList();
        for (String deviceToken : pushTokens) {
            notifications.add(new SimpleApnsPushNotification(deviceToken, spInfo.getAppId(), payload));
        }

        StringBuilder error = new StringBuilder();
        List<PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>> futures = Lists.newArrayList();
        for (final SimpleApnsPushNotification pushNotification : notifications) {
            final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> sendNotificationFuture = apnsClient.sendNotification(pushNotification);
            futures.add(sendNotificationFuture);
        }

        for (PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> future : futures) {
            try {
                PushNotificationResponse<SimpleApnsPushNotification> response = future.get();
                if (response != null) {
                    log.info("{}", response);
                    if (!response.isAccepted()) {
                        if (pushTokens.size() == 1) {
                            error.append(response.getRejectionReason());
                        } else {
                            error.append(response.getPushNotification().getToken() + ":" + response.getRejectionReason()).append(";");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("apns record : {} error", pushRecord.getId(), e);
            }
        }
        //2021-06-30 19:38:42.581|1625053122581|nioEventLoopGroup-2-1|INFO ||||||i.b.b.c.s.p.ApplePushySender:100|
        // response:SimplePushNotificationResponse{pushNotification=SimpleApnsPushNotification{token='765ceb8dbd2b9529ba89432d9dbdb0efb87b243e200f1547877922efcee5f04d',
        // payload='{"aps":{"alert":{"body":"Body!","title":"title"}},"key":"value"}', invalidationTime=2021-07-01T11:38:36.885213Z,
        // priority=IMMEDIATE, pushType=null, topic='com.Blex.broker.iostest', collapseId='null', apnsId=null}, success=true,
        // apnsId=39141f83-7c7d-5968-2325-7527d5cbf177, rejectionReason='null',
        // tokenExpirationTimestamp=null}

        pushRecord.setPushResult(error.toString().length() > 200 ? error.substring(0,200) : error.toString());
        pushRecord.setStatus(1);
        pushRecord.setMessageId("");
        pushRecord.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        appPushRecordMapper.updateByPrimaryKeySelective(pushRecord);
        return true;
    }

    @Scheduled(cron = "55 * * * * ?")
    public void rePush() {
        rePush("APPLE");
    }
}
