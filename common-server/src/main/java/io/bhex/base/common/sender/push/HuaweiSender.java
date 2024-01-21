package io.bhex.base.common.sender.push;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import io.bhex.base.common.entity.AppPushRecord;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.mapper.AppPushRecordMapper;
import io.bhex.base.common.service.impl.SimpleMessageService;
import io.bhex.base.common.util.JsonUtil;
import lombok.Builder;
import lombok.Cleanup;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HuaweiSender extends BasePushSender  implements IPushSender {

    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;
    @Resource
    private SimpleMessageService simpleMessageService;
    @Resource
    private AppPushRecordMapper appPushRecordMapper;
    private static Map<String, AccessTokenObj> accessTokenMap = Maps.newHashMap();


    @PostConstruct
    @Scheduled(initialDelay = 10_000, fixedRate = 60_000)
    public void refresh() {
        List<SpInfo> spInfos = simpleMessageService.getSpInfos().stream()
                .filter(s -> s.getChannel().equals("HUAWEI"))
                .collect(Collectors.toList());
        for (SpInfo s : spInfos) {
            try {
                String key = s.getAccessKeyId() + "|" + s.getSecretKey();
                if (accessTokenMap.containsKey(key)){
                    if (accessTokenMap.get(key).getExpireAt() - System.currentTimeMillis() < 600_000) {
                        refreshToken(s.getAccessKeyId(), s.getSecretKey());
                    }
                } else {
                    refreshToken(s.getAccessKeyId(), s.getSecretKey());
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    public boolean refreshToken(String clientId, String clientSecret)  throws IOException {
        FormBody.Builder requestBuilder = new FormBody.Builder();
        requestBuilder.add("grant_type", "client_credentials");
        requestBuilder.add("client_id", clientId);
        requestBuilder.add("client_secret", clientSecret);
        Request oauthRequest = new
                Request.Builder()
                .url("https://oauth-login.cloud.huawei.com/oauth2/v3/token")
                .post(requestBuilder.build()).build();
        @Cleanup
        Response response = okHttpClient.newCall(oauthRequest).execute();
        if (response.isSuccessful()) {
            String text = response.body().string();
            GsonBuilder gsonBuilder = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
            AccessTokenObj obj = gsonBuilder.create().fromJson(text, AccessTokenObj.class);
            obj.setExpireAt(System.currentTimeMillis() + obj.getExpiresIn()*1000L);
            accessTokenMap.put(clientId + "|" + clientSecret, obj);
            log.info("client:{} {} {}", clientId, text, obj);
            return true;
        } else {
            log.error("{} {}", clientId, response.body().string());
            return false;
        }
    }

    public String getAccessKey(String clientId, String clientSecret) throws IOException{
        if (accessTokenMap.containsKey(clientId + "|" + clientSecret)) {
            return accessTokenMap.get(clientId + "|" + clientSecret).getAccessToken();
        } else {
            refreshToken(clientId, clientSecret);
            return accessTokenMap.get(clientId + "|" + clientSecret).getAccessToken();
        }
    }

    public boolean sendBusinessPush(AppPushRecord pushRecord) {
        return send(pushRecord);
    }

    @Override
    public boolean sendPush(AppPushRecord pushRecord) {
        return send(pushRecord);
    }

    private boolean send(AppPushRecord pushRecord) {
        List<String> deviceTokens = Arrays.asList(pushRecord.getPushTokens().split(","));
        if (CollectionUtils.isEmpty(deviceTokens)) {
            return true;
        }
        SpInfo spInfo = simpleMessageService.getSpInfo(pushRecord.getSpId());
        if (spInfo == null) {
            log.warn("send huaweipush error with no spInfo, req:{}", pushRecord);
            return false;
        }


        try {
            PushCustomDataDTO customData = getUrlData(pushRecord);
            String accessToken = getAccessKey(spInfo.getAccessKeyId(), spInfo.getSecretKey());
            if (accessToken == null) {
                log.warn("accessToken wrong! {} {}", pushRecord.getOrgId());
                return false;
            }
            String postUrl = String.format("https://push-api.cloud.huawei.com/v1/%s/messages:send", spInfo.getAccessKeyId());
            Headers head = new Headers.Builder()
                    .add("Authorization", "Bearer " + accessToken)
                    .build();
            ClickAction clickAction = ClickAction.builder()
                    .type(1) //1：用户自定义点击行为  2：点击后打开特定URL
                    //.action("io.bhex.app.intent.action.push")
                    //.intent("intent://io.bhex.app.intent.action.push/PushTranslateActivity#Intent;scheme=pushscheme;launchFlags=0x4000000;end")
                    .intent("intent://" + spInfo.getAppId() + ".intent.action.push/PushTranslateActivity" + PushCustomDataDTO.convertString(customData) +
                            "#Intent;scheme=pushscheme;launchFlags=0x4000000;end")
                    .build();
            AndroidNotification notification = AndroidNotification.builder()
                    .title(pushRecord.getPushTitle())
                    .body(Strings.nullToEmpty(pushRecord.getPushContent()))
                    .style(1)
                    .bigTitle(pushRecord.getPushTitle())
                    .bigBody(Strings.nullToEmpty(pushRecord.getPushContent()))
                    .notifySummary(Strings.nullToEmpty(pushRecord.getPushSummary()))
                    .tag(pushRecord.getReqOrderId())
                    .clickAction(clickAction)
                    .build();


            AndroidConfig androidConfig = AndroidConfig.builder()
                    .biTag(getBiTag(pushRecord))
                    .notification(notification)
                    .build();


            Message message = Message.builder()
                    .android(androidConfig)
                    .token(deviceTokens)
                    .data(JsonUtil.defaultGson().toJson(customData))
                    .build();
            PushContent pushContent = PushContent.builder().message(message).validateOnly(false).build();

            GsonBuilder gsonBuilder = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
            log.info("pushContent:{}", gsonBuilder.create().toJson(pushContent));
            RequestBody requestBody =
                    RequestBody.create(MediaType.parse("application/json;charset=utf-8;"), gsonBuilder.create().toJson(pushContent));

            Request request = new
                    Request.Builder()
                    .url(postUrl)
                    .headers(head).post(requestBody).build();
            Response response = okHttpClient.newCall(request).execute();

            //if (response.isSuccessful()) {
                String result = response.body().string();
                PushResult pushResult = JsonUtil.defaultGson().fromJson(result, PushResult.class);
                if (pushResult.getCode().equals("80000000")) {
                    pushRecord.setStatus(1);
                } else {
                    pushRecord.setStatus(2);
                }
                pushRecord.setMessageId(pushResult.getRequestId());
                pushRecord.setPushResult(result);
                pushRecord.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
                appPushRecordMapper.updateByPrimaryKeySelective(pushRecord);
           // }

        } catch (Exception e) {
            log.error("huawei push error ：", e);
        }
        return true;
    }

    @Scheduled(cron = "59 * * * * ?")
    public void rePush() {
        rePush("HUAWEI");
    }

    @Data
    @Builder
    private static class PushResult {
        private String code;
        private String msg;
        private String requestId;
    }

    @Data
    @Builder
    private static class PushContent {
        private boolean validateOnly;
        private Message message;
    }

    @Data
    @Builder
    private static class Message {
        private String data; //自定义消息  支持普通字符串或者JSON格式字符串
        private AndroidConfig android;
        private List<String> token;

    }

    @Data
    @Builder
    private static class AndroidConfig {
        private String biTag; //批量任务消息标识，消息回执时会返回给应用服务器，应用服务器可以识别bi_tag对消息的下发情况进行统计分析。
        private AndroidNotification notification;
    }
    @Data
    @Builder
    private static class AndroidNotification {
        private String title;
        private String body;
        private Integer style; //1：大文本样式  3：Inbox样式  0-default 单行
        private String bigTitle;
        private String bigBody;
        private String notifySummary;
        private String icon;
        private String tag; //消息标签，同一应用下使用同一个消息标签的消息会相互覆盖，只展示最新的一条。 重试就不怕发多次了
        private ClickAction clickAction;
        private String image; // 自定义通知栏消息右侧大图标 必须是HTTPS
        private Integer autoClear; //消息展示时长，超过后自动清除，单位：毫秒。
    }

    @Data
    @Builder
    private static class ClickAction {
        private Integer type; //2：点击后打开特定URL   3：点击后打开应用App
        private String intent;
        private String url; //URL使用的协议必须是HTTPS协议  当type为2时必选
        private String action;
    }

    @Data
    private static class AccessTokenObj {
        private String accessToken;
        private Integer expiresIn;
        private Long expireAt;
        private String tokenType;
    }
}
