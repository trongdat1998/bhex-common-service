package io.bhex.base.common.sender.push;

import io.bhex.base.common.entity.AppPushRecord;
import io.bhex.base.common.mapper.AppPushRecordMapper;
import io.bhex.base.common.util.RedisLockUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public abstract class BasePushSender implements IPushSender {
    @Resource
    private AppPushRecordMapper appPushRecordMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Resource(name = "cronTaskExecutor")
    private TaskExecutor taskExecutor;

    public void rePush(String pushChannel) {
        boolean lock = RedisLockUtils.tryLock(redisTemplate, "rePush" + pushChannel, 55_000);
        if (!lock) {
            log.info("rePush not get lock");
            return;
        }
        long date = DateUtils.truncate(new Date(), Calendar.MINUTE).getTime();
        Timestamp end = new Timestamp(date);
        Timestamp start = new Timestamp(date - 60_000L);
        List<AppPushRecord> list = appPushRecordMapper.getUnPushedRecords(pushChannel, start, end);
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        CompletableFuture.runAsync( () -> {
            for (AppPushRecord pushRecord : list) {
                try {
                    if (StringUtils.isEmpty(pushRecord.getBizType())) {
                        sendPush(pushRecord);
                    } else {
                        sendBusinessPush(pushRecord);
                    }
                } catch (Exception e) {
                    log.error("repush error : {}", pushRecord, e);
                }
            }
        }, taskExecutor);
    }
}
