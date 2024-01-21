package io.bhex.base.common.sender.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import io.bhex.base.common.entity.AppPushRecord;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.mapper.AppPushRecordMapper;
import io.bhex.base.common.service.impl.SimpleMessageService;
import io.bhex.base.common.util.RedisLockUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FcmSender extends BasePushSender implements IPushSender {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Resource
    private SimpleMessageService simpleMessageService;
    @Resource
    private AppPushRecordMapper appPushRecordMapper;
   

    private static Map<String, FirebaseApp> firebaseAppMap = new ConcurrentHashMap<>();

    @Override
    public boolean sendBusinessPush(AppPushRecord pushRecord) {
        try {
            if (pushRecord.getPushTokens().contains(",")) {
                batchPush(pushRecord);
            } else {
                pushSingleToAndroid(pushRecord);
            }
        } catch (Exception e) {
            log.warn("{} {}", pushRecord.getId(), pushRecord.getPushTokens(), e);
        }
        return true;
    }

    @Override
    public boolean sendPush(AppPushRecord pushRecord) {
        try {
            batchPush(pushRecord);
        } catch (Exception e) {
            log.error("{}", pushRecord, e);
        }
        return false;
    }

    @PostConstruct
    @Scheduled(initialDelay = 10_000, fixedRate = 60_000)
    public void refresh() {
        List<SpInfo> spInfos = simpleMessageService.getSpInfos().stream()
                .filter(s -> s.getChannel().equals("FCM"))
                .collect(Collectors.toList());
        for (SpInfo s : spInfos) {
            try {
                initSDK(s);
            } catch (Exception e) {
                log.error("{}", s, e);
            }
        }
    }


    //only once
    public static void initSDK(SpInfo spInfo) throws IOException {
        if (firebaseAppMap.containsKey(spInfo.getAppId())) {
            return;
        }
        String json = spInfo.getSecretKey();
        InputStream serviceAccount = new ByteArrayInputStream(json.getBytes());
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setDatabaseUrl(spInfo.getAccessKeyId())
                .build();
        //初始化firebaseApp
        FirebaseApp firebaseApp = FirebaseApp.initializeApp(options, spInfo.getId().toString());
        //存放
        firebaseAppMap.put(spInfo.getAppId(), firebaseApp);
    }

    private void batchPush(AppPushRecord pushRecord)  throws FirebaseMessagingException {
        SpInfo spInfo = simpleMessageService.getSpInfo(pushRecord.getSpId());
        FirebaseApp firebaseApp = firebaseAppMap.get(spInfo.getAppId());
        if(firebaseApp == null) {
            return;
        }
        String topic = getBiTag(pushRecord);
        List<String> deviceTokens = Arrays.asList(pushRecord.getPushTokens().split(","));
        TopicManagementResponse createTopicResponse = FirebaseMessaging.getInstance(firebaseApp)
                .subscribeToTopic(deviceTokens, topic);
        log.info("createTopic {} {} {}", topic, createTopicResponse.getFailureCount());
        if (createTopicResponse.getFailureCount() > 0) {
            for (TopicManagementResponse.Error error : createTopicResponse.getErrors()) {
                log.info("{} {} {}", error.getIndex(), error.getReason(), deviceTokens.get(error.getIndex()));
            }
           if (createTopicResponse.getFailureCount() == deviceTokens.size()) {
               pushRecord.setStatus(2);
               pushRecord.setPushResult("all tokens are error");
               appPushRecordMapper.updateByPrimaryKeySelective(pushRecord);
               return;
           }
        }

        Message message = getPushMessage(pushRecord, topic, spInfo.getAppId());
        String response = FirebaseMessaging.getInstance(firebaseApp).send(message);
        log.info("batchPush {} {}", topic, response);
        updatePushRecord(pushRecord, response);
    }

    private void cancelTopic(AppPushRecord pushRecord) throws FirebaseMessagingException {
        SpInfo spInfo = simpleMessageService.getSpInfo(pushRecord.getSpId());
        FirebaseApp firebaseApp = firebaseAppMap.get(spInfo.getAppId());
        if(firebaseApp == null) {
            return;
        }
        String topic = getBiTag(pushRecord);
        List<String> deviceTokens = Arrays.asList(pushRecord.getPushTokens().split(","));
        TopicManagementResponse cancelTopicResponse = FirebaseMessaging.getInstance(firebaseApp).unsubscribeFromTopic(deviceTokens, topic);
        log.info("cancelTopic {} {}", topic, cancelTopicResponse);
    }

    private void pushSingleToAndroid(AppPushRecord pushRecord) throws FirebaseMessagingException {
        SpInfo spInfo = simpleMessageService.getSpInfo(pushRecord.getSpId());
        FirebaseApp firebaseApp = firebaseAppMap.get(spInfo.getAppId());
        if (firebaseApp == null) {
            return;
        }
        Message message = getPushMessage(pushRecord, "", spInfo.getAppId());
        String response = FirebaseMessaging.getInstance(firebaseApp).send(message);
        log.info("pushSingleToAndroid : {} {}" , pushRecord.getReqOrderId(), response);

        updatePushRecord(pushRecord, response);
    }

    private void updatePushRecord(AppPushRecord pushRecord, String response) {
        pushRecord.setStatus(1);
        pushRecord.setMessageId(response);
        pushRecord.setPushResult(response);
        pushRecord.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        appPushRecordMapper.updateByPrimaryKeySelective(pushRecord);
    }

    private Message getPushMessage(AppPushRecord pushRecord, String topic, String appId) {
        com.google.firebase.messaging.AndroidConfig.Builder androidConfigBuilder=AndroidConfig.builder();
        //androidConfigBuilder.setRestrictedPackageName("io.telecomm.telecomm");

        AndroidNotification.Builder androidNotifiBuilder=AndroidNotification.builder();
        //androidNotifiBuilder.setColor("#55BEB7");// 设置消息通知颜色
        //androidNotifiBuilder.setIcon("https://www.shiku.co/images/favicon.png");// 设置消息图标
        androidNotifiBuilder.setTitle(pushRecord.getPushTitle());
        androidNotifiBuilder.setBody(pushRecord.getPushContent());

        androidNotifiBuilder.setTag(pushRecord.getReqOrderId());
        androidNotifiBuilder.setClickAction(appId + ".intent.action.push");
        AndroidNotification androidNotification = androidNotifiBuilder.build();
        androidConfigBuilder.setNotification(androidNotification);



        androidConfigBuilder.putAllData(PushCustomDataDTO.convertMap(getUrlData(pushRecord)));
        androidConfigBuilder.setCollapseKey(System.currentTimeMillis()%4 + "");

        AndroidConfig androidConfig = androidConfigBuilder.build();

        List<String> deviceTokens = Arrays.asList(pushRecord.getPushTokens().split(","));
        Message.Builder messageBuilder = Message.builder()
                .setAndroidConfig(androidConfig);
        if (!StringUtils.isEmpty(topic)) {
            messageBuilder.setTopic(topic);
        } else {
            messageBuilder.setToken(deviceTokens.get(0));
        }
        log.info("messageBuilder {}", messageBuilder);
        return messageBuilder.build();
    }


    @Scheduled(cron = "23 15,30 0 * * ?")
    public void deleteFcmTopic() {
        boolean lock = RedisLockUtils.tryLock(redisTemplate, "deleteFcmTopic", 30_000);
        if (!lock) {
            log.info("deleteFcmTopic not get lock");
            return;
        }
        long date = DateUtils.truncate(new Date(), Calendar.DATE).getTime();
        Timestamp end = new Timestamp(date);
        Timestamp start = new Timestamp(date - 24*3600_000L);
        List<AppPushRecord> list = appPushRecordMapper.getFcmDeletedTopicRecords(start, end);
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        list.forEach(r -> {
            try {
                cancelTopic(r);
            } catch (Exception e) {
                log.error("cancel topic error {}", r, e);
            }
        });
    }

    @Scheduled(cron = "57 * * * * ?")
    public void rePush() {
        rePush("FCM");
    }
}
