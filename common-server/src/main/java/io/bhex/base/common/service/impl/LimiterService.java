package io.bhex.base.common.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import io.bhex.base.common.entity.RateLimiter;
import io.bhex.base.common.mapper.AppPushRecordMapper;
import io.bhex.base.common.mapper.EmailDeliveryRecordMapper;
import io.bhex.base.common.mapper.RateLimiterMapper;
import io.bhex.base.common.mapper.SmsDeliveryRecordMapper;
import io.bhex.base.common.util.ChannelConstant;
import io.bhex.base.common.util.Combo2;
import io.bhex.base.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LimiterService {

    @Autowired
    private SmsDeliveryRecordMapper smsDeliveryRecordMapper;

    @Autowired
    private EmailDeliveryRecordMapper emailDeliveryRecordMapper;
    @Autowired
    private AppPushRecordMapper appPushRecordMapper;

    @Autowired
    private RateLimiterMapper rateLimiterMapper;

    private Cache<String, String> senderLocalCache = CacheBuilder
            .newBuilder()
            .maximumSize(6000)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();

    private Cache<String, Integer> alertLocalCache = CacheBuilder
            .newBuilder()
            .maximumSize(6000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();


    public Combo2<Boolean, String> overLimitMobile(long orgId, String mobile, String bizType, List<String> params, Map<String, String> reqParam){
        long nowMs = System.currentTimeMillis();
        Timestamp now = new Timestamp(nowMs);

        List<RateLimiter> limiters = getLimitValue(orgId, ChannelConstant.BIZ_TYPE_SMS, bizType);
        if (CollectionUtils.isEmpty(limiters)) {
            return new Combo2<>(false, "");
        }
        List<String> theParams = Lists.newArrayList(params);
        //30s内不能发送相同的内容
        String key = orgId + "-" + mobile + "-" + bizType;
        theParams.addAll(reqParam.values());
        String paramsContent = JsonUtil.defaultGson().toJson(params);
        String v = senderLocalCache.getIfPresent(key);
        if (v != null && v.equals(paramsContent)) {
            senderLocalCache.put(key, paramsContent);
            return new Combo2<>(true, "same content in 30s");
        } else {
            senderLocalCache.put(key, paramsContent);
        }


        for (RateLimiter limiter : limiters) {
            Timestamp startTime = new Timestamp(nowMs - limiter.getIntervalSeconds()*1000);
            int counter = smsDeliveryRecordMapper.countByCreated(orgId, mobile, bizType, startTime, now);
            int limitValue = limiter.getLimiterValue();
            if (counter >= limitValue) {
                Integer cacheValue = alertLocalCache.getIfPresent(mobile);
                if (cacheValue == null) {
                    alertLocalCache.put(mobile, counter);
                    log.warn("org:{} {} send sms over {} times, currentValue:{}", orgId, mobile, limiter.getLimiterValue() , counter);
                }
                return new Combo2<>(true, limiter.getLimiterKey());
            }
        }

        return new Combo2<>(false, "");
    }

    public Combo2<Boolean, String>  overLimitEmail(long orgId, String email, String bizType, List<String> params, Map<String, String> reqParam){
        List<String> theParams = Lists.newArrayList(params);
        //30s内不能发送相同的内容
        String key = orgId + "-" + email + "-" + bizType;
        theParams.addAll(reqParam.values());
        String paramsContent = JsonUtil.defaultGson().toJson(params);
        String v = senderLocalCache.getIfPresent(key);
        if (v != null && v.equals(paramsContent)) {
            senderLocalCache.put(key, paramsContent);
            return new Combo2<>(true, "same content in 30s");
        } else {
            senderLocalCache.put(key, paramsContent);
        }

        long nowMs = System.currentTimeMillis();
        Timestamp now = new Timestamp(nowMs);

        List<RateLimiter> limiters = getLimitValue(orgId, ChannelConstant.BIZ_TYPE_EMAIL, bizType);
        for (RateLimiter limiter : limiters) {
            Timestamp startTime = new Timestamp(nowMs - limiter.getIntervalSeconds()*1000);
            int counter = emailDeliveryRecordMapper.countByCreated(orgId, email, bizType, startTime, now);
            int limitValue = limiter.getLimiterValue();
            if (counter >= limitValue) {
                Integer cacheValue = alertLocalCache.getIfPresent(email);
                if (cacheValue == null) {
                    alertLocalCache.put(email, counter);
                    log.warn("org:{} {} send email over {} times, currentValue:{}", orgId, email, limiter.getLimiterValue() , counter);
                }
                return new Combo2<>(true, limiter.getLimiterKey());
            }
        }

        return new Combo2<>(false, "");
    }

    public Combo2<Boolean, String>  overLimitPush(long orgId, String pushToken, String bizType, List<String> params, Map<String, String> reqParam){

        //30s内不能发送相同的内容
        String key = orgId + "-" + pushToken + "-" + bizType;
        params.addAll(reqParam.values());
        String paramsContent = JsonUtil.defaultGson().toJson(params);
        String v = senderLocalCache.getIfPresent(key);
        if (v != null && v.equals(paramsContent)) {
            senderLocalCache.put(key, paramsContent);
            return new Combo2<>(true, "same content in 30s");
        } else {
            senderLocalCache.put(key, paramsContent);
        }

        long nowMs = System.currentTimeMillis();
        Timestamp now = new Timestamp(nowMs);

        List<RateLimiter> limiters = getLimitValue(orgId, ChannelConstant.BIZ_TYPE_PUSH, bizType);
        if (CollectionUtils.isEmpty(limiters)) {
            return new Combo2<>(false, "");
        }
        for (RateLimiter limiter : limiters) {
            Timestamp startTime = new Timestamp(nowMs - limiter.getIntervalSeconds()*1000);
            int counter = appPushRecordMapper.countByCreated(orgId, pushToken, bizType, startTime, now);
            int limitValue = limiter.getLimiterValue();
            if (counter >= limitValue) {
                Integer cacheValue = alertLocalCache.getIfPresent(pushToken);
                if (cacheValue == null) {
                    alertLocalCache.put(pushToken, counter);
                    log.error("org:{} {} sendBusinessPush over {} times, currentValue:{}", orgId, pushToken, limiter.getLimiterValue() , counter);
                }
                return new Combo2<>(true, limiter.getLimiterKey());
            }
        }

        return new Combo2<>(false, "");
    }


    private static Map<String, List<RateLimiter>> limiterMap = new HashMap<>();

    @PostConstruct
    @Scheduled(initialDelay = 10_000, fixedRate = 120_000)
    private void loadLimiterConfig(){
        List<RateLimiter> list = rateLimiterMapper.selectAll();
        if(CollectionUtils.isEmpty(list)){
            return;
        }
        Function<RateLimiter, String> groupFunc = r -> {
            String key = r.getSenderType() + r.getBizType().toUpperCase();
            if (r.getOrgId() != null && r.getOrgId() > 0) {
                key = r.getOrgId() + key;
            }
            return key;
        };
        Map<String, List<RateLimiter>>  tmp =  list.stream().collect(Collectors.groupingBy(groupFunc));

        if (!limiterMap.toString().equals(tmp.toString())) {
            log.info("limitermap:{} ", tmp);
        }
        limiterMap = tmp;

    }

    private List<RateLimiter> getLimitValue(long orgId, int senderType, String bizType) {
        //券商自行设定 bizType 的限制
        String key = orgId + senderType + bizType.toUpperCase();
        if (limiterMap.containsKey(key)) {
            return limiterMap.get(key);
        }

        //共用bizType 的限制
        key = senderType + bizType.toUpperCase();
        if (limiterMap.containsKey(key)) {
            return limiterMap.get(key);
        }

        //不分bizType的限制
        return limiterMap.get(senderType + "DEFAULT");
    }
}
