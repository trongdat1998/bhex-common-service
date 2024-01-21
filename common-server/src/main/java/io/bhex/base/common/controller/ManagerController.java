package io.bhex.base.common.controller;


import com.google.gson.Gson;
import io.bhex.base.common.service.impl.SmsFeeStatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@Service
@RestController
public class ManagerController {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private SmsFeeStatisticsService feeStatisticsService;

    private static final String PROCESSING_HASH_KEY = "message.processing";

    @RequestMapping(value = "/manager/processing")
    public String processingMessages() {
        Map<Object, Object> valuesMap = redisTemplate.opsForHash().entries(PROCESSING_HASH_KEY);
        if (CollectionUtils.isEmpty(valuesMap)) {
            return "";
        }
        return new Gson().toJson(valuesMap);
    }

    @RequestMapping(value = "/manager/smsfee")
    public String fee() {
        feeStatisticsService.syncPriceData();
        return "ok";
    }
}
