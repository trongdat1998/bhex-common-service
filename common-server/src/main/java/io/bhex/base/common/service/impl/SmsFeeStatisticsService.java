package io.bhex.base.common.service.impl;

import com.google.common.base.Strings;
import io.bhex.base.common.entity.SmsDeliveryRecordEntity;
import io.bhex.base.common.entity.SmsFeeStatistics;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.mapper.SmsDeliveryRecordMapper;
import io.bhex.base.common.mapper.SmsFeeStatisticsMapper;
import io.bhex.base.common.sender.v2.TwilioSmsSender;
import io.bhex.base.common.sender.v2.ZhixinIntlSmsSender;
import io.bhex.base.common.sender.v2.ZhuTongIntlSender;
import io.bhex.base.common.util.RedisLockUtils;
import io.bhex.base.env.BhexEnv;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class SmsFeeStatisticsService {

    @Autowired
    private SimpleMessageService messageService;
    @Autowired
    private SmsFeeStatisticsMapper smsFeeStatisticsMapper;
    @Autowired
    private SmsDeliveryRecordMapper deliveryRecordMapper;
    @Autowired
    private TwilioSmsSender twilioSmsSender;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Scheduled(initialDelay = 500_000, fixedRate = 3600_000)
    public void syncPriceData() {
        if (!new BhexEnv().isBHEXB()) {
            return;
        }
        boolean lock = RedisLockUtils.tryLock(redisTemplate, "syncPriceData", 7200_000);
        if (!lock) {
            log.info("syncPriceData not get lock");
            return;
        }
        try {
            long startTime = System.currentTimeMillis();
            for (;;) {
                if (System.currentTimeMillis() - startTime > 6600_000) {
                    return;
                }
                long currentHour = Long.parseLong(new DateTime().toString("yyyyMMddHH"));
                long lastId = smsFeeStatisticsMapper.getMaxRecordId();
                log.info("compute from id : {}", lastId);
                List<SmsDeliveryRecordEntity> list = deliveryRecordMapper.getRecordsByLastId(lastId, 500);
                if (CollectionUtils.isEmpty(list)) {
                    break;
                }
                boolean finished = false;
                for (SmsDeliveryRecordEntity record : list) {
                    long recordHour = Long.parseLong(new DateTime(record.getCreatedAt().getTime()).toString("yyyyMMddHH"));
                    if (currentHour == recordHour) {
                        finished = true;
                        break;
                    }
                    if (StringUtils.isEmpty(record.getMessageId())) {
                        continue;
                    }
                    if (record.getChannel().equals("NO_OP")) {
                        continue;
                    }
                    if (Strings.nullToEmpty(record.getDeliveryStatus()).equals("NOTSENDED")) {
                        continue;
                    }
                    long statisticsTime = Long.parseLong(new DateTime(record.getCreatedAt().getTime()).toString("yyyyMMdd"));
                    if (record.getNationCode().equals("86")) {
                        handleCnChannel(record, statisticsTime);
                    } else if (record.getChannel().equals("ZHIXIN_INTL") || record.getChannel().equals("ZHUTONG_INTL")) {
                        handleZhixinIntl(record, statisticsTime);
                    } else if (record.getChannel().equals("TWILIO")) {
                        handleTwilio(record, statisticsTime);
                    }
                }
                if (finished || list.size() < 500) {
                    break;
                }
            }
        } finally {
            RedisLockUtils.releaseLock(redisTemplate, "syncPriceData");
        }

    }


    private void handleZhixinIntl(SmsDeliveryRecordEntity record, long statisticsTime) {
        SmsFeeStatistics statistics = initStatisticsRecord(record.getOrgId(), record.getChannel(), statisticsTime, "CNY");
        BigDecimal price = BigDecimal.ZERO;
        if (record.getPrice() == null || record.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            if (record.getChannel().equals("ZHIXIN_INTL")) {
                price = ZhixinIntlSmsSender.PRICE_MAP.getOrDefault(record.getNationCode(), BigDecimal.ZERO).multiply(new BigDecimal(record.getCountNum()));
            } else if (record.getChannel().equals("ZHUTONG_INTL")) {
                price = ZhuTongIntlSender.PRICE_MAP.getOrDefault(record.getNationCode(), BigDecimal.ZERO).multiply(new BigDecimal(record.getCountNum()));
            } else {
                return;
            }

            record.setPrice(price);
            record.setPriceUnit("CNY");
            deliveryRecordMapper.updateByPrimaryKeySelective(record);
        } else {
            price = record.getPrice();
        }

        statistics.setPrice(statistics.getPrice().add(price));
        statistics.setLastRecordId(record.getId());
        statistics.setCount(statistics.getCount() + record.getCountNum());
        smsFeeStatisticsMapper.updateByPrimaryKeySelective(statistics);
    }

    private void handleTwilio(SmsDeliveryRecordEntity record, long statisticsTime) {
        BigDecimal price = BigDecimal.ZERO;
        String priceUnit = "";
        if (record.getPrice() == null || record.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            SpInfo spInfo  = messageService.getSpInfo(record.getSpId());
            if (spInfo == null) {
                return;
            }
            TwilioSmsSender.MessageData messageData = new TwilioSmsSender.MessageData();
            messageData.setUser(spInfo.getAccessKeyId());
            messageData.setPassword(spInfo.getSecretKey());
            messageData.setSid(record.getMessageId());
            SmsDeliveryRecordEntity recordEntity = twilioSmsSender.fetchMessage(messageData);
            if (recordEntity == null) {
                return;
            }
            price = recordEntity.getPrice();
            priceUnit = recordEntity.getPriceUnit();
            if (recordEntity.getCountNum() > 1) {
                record.setCountNum(recordEntity.getCountNum());
                deliveryRecordMapper.updateByPrimaryKeySelective(record);
            }
        } else {
            price = record.getPrice();
            priceUnit = record.getPriceUnit();
        }
        SmsFeeStatistics statistics = initStatisticsRecord(record.getOrgId(), record.getChannel(), statisticsTime, priceUnit);
        statistics.setPrice(statistics.getPrice().add(price));
        statistics.setLastRecordId(record.getId());
        statistics.setCount(statistics.getCount() + record.getCountNum());
        smsFeeStatisticsMapper.updateByPrimaryKeySelective(statistics);
    }

    private void handleCnChannel(SmsDeliveryRecordEntity record, long statisticsTime) {
        SmsFeeStatistics statistics = initStatisticsRecord(record.getOrgId(), record.getChannel(), statisticsTime, "CNY");
        BigDecimal price = BigDecimal.ZERO;
        if (record.getPrice() == null || record.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal channelPrice = BigDecimal.ZERO;
            if (record.getChannel().equals("ZHIXIN")) {
                channelPrice = new BigDecimal("0.035");
            } else if (record.getChannel().equals("ZHUTONG")) {
                channelPrice = new BigDecimal("0.038");
            } else if (record.getChannel().equals("SEND_CLOUD")) {
                channelPrice = new BigDecimal("0.05");
            } else {
                return;
            }
            price = channelPrice.multiply(new BigDecimal(record.getCountNum()));
            record.setPrice(price);
            record.setPriceUnit("CNY");
            deliveryRecordMapper.updateByPrimaryKeySelective(record);
        } else {
            price = record.getPrice();
        }

        statistics.setPrice(statistics.getPrice().add(price));
        statistics.setLastRecordId(record.getId());
        statistics.setCount(statistics.getCount() + record.getCountNum());
        smsFeeStatisticsMapper.updateByPrimaryKeySelective(statistics);
    }

    private SmsFeeStatistics initStatisticsRecord(long orgId, String channel, long statisticsTime, String priceUnit) {
        SmsFeeStatistics statisticsRecord = smsFeeStatisticsMapper.getRecord(orgId, channel, statisticsTime, priceUnit);
        if (statisticsRecord != null) {
            return statisticsRecord;
        }
        statisticsRecord = new SmsFeeStatistics();
        statisticsRecord.setOrgId(orgId);
        statisticsRecord.setChannel(channel);
        statisticsRecord.setStatisticsTime(statisticsTime);
        statisticsRecord.setPriceUnit(priceUnit);
        statisticsRecord.setPrice(BigDecimal.ZERO);
        statisticsRecord.setLastRecordId(0L);
        statisticsRecord.setCount(0L);
        smsFeeStatisticsMapper.insertSelective(statisticsRecord);
        return statisticsRecord;
    }
}
