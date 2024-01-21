package io.bhex.base.common.service.impl;

import com.google.common.base.Strings;
import io.bhex.base.common.DeliveryRecord;
import io.bhex.base.common.entity.SmsDeliveryRecordEntity;
import io.bhex.base.common.mapper.SmsDeliveryRecordMapper;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.SendChannelEnum;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description:
 * @Date: 2018/12/6 下午7:34
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
@Service
public class SmsDeliveryRecordServiceImpl implements SmsDeliveryRecordService {
    @Autowired
    private SmsDeliveryRecordMapper smsDeliveryRecordMapper;

    public void insertTencentSmsDeliveryRecord(Long orgId, String sid, String areaCode, String mobile, String bizType, String content, int spId){
        SmsDeliveryRecordEntity entity = new SmsDeliveryRecordEntity();
        entity.setOrgId(orgId);
        entity.setChannel("TENCENT");
        entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        entity.setMessageId(sid);
        entity.setNationCode(areaCode);
        entity.setMobile(mobile);
        entity.setPriceUnit(null);
        entity.setPrice(null);
        entity.setDeliveryStatus("UNKNOWN");
        entity.setRegionName(null);
        entity.setBizType(bizType);
        entity.setContent(content);
        entity.setSpId(spId);
        smsDeliveryRecordMapper.insertSelective(entity);
    }

    public void insertAwsSmsDeliveryRecord(Long orgId, String messageId, String areaCode, String mobile, String regionName,String bizType, String content, int spId){
        SmsDeliveryRecordEntity entity = new SmsDeliveryRecordEntity();
        entity.setOrgId(orgId);
        entity.setChannel("AWS");
        entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        entity.setMessageId(messageId);
        entity.setNationCode(areaCode);
        entity.setMobile(mobile);
        entity.setPriceUnit("USD");
        entity.setPrice(areaCode.equals("86") ? new BigDecimal("0.01531") : null);
        entity.setDeliveryStatus("UNKNOWN");
        entity.setRegionName(regionName);
        entity.setBizType(bizType);
        entity.setContent(content);
        entity.setSpId(spId);
        smsDeliveryRecordMapper.insertSelective(entity);
    }

    public void insertSmsDeliveryRecord(Long orgId, SendChannelEnum channel, String sid, String areaCode, String mobile,
                                        String status, String description, String bizType, String content, int spId, int countNum, String reqOrderId, long userId){
        SmsDeliveryRecordEntity entity = new SmsDeliveryRecordEntity();
        entity.setOrgId(orgId);
        entity.setChannel(channel.name());
        entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        entity.setMessageId(sid);
        entity.setNationCode(areaCode);
        entity.setMobile(mobile);
        entity.setPriceUnit(null);
        entity.setPrice(null);
        entity.setDeliveryStatus(status);
        entity.setRegionName(null);
        entity.setDescription(description);
        entity.setBizType(bizType);
        entity.setContent(content);
        entity.setSpId(spId);
        entity.setCountNum(countNum);

        entity.setReqOrderId(Strings.nullToEmpty(reqOrderId));
        entity.setCodeFeedbackTime(null);
        entity.setCodeFeedbackResult(-1);
        entity.setUserId(userId);

        smsDeliveryRecordMapper.insertSelective(entity);
    }

    public void insertSmsDeliveryRecord(Long orgId, SendChannelEnum channel, String sid, String areaCode, String mobile,
                                        String status, String description, String bizType, String content, int spId, String reqOrderId, long userId){
        insertSmsDeliveryRecord(orgId, channel, sid, areaCode, mobile, status, description, bizType, content, spId, 1, reqOrderId, userId);
    }



    public void updateDeliveryStatus(String messageId, String deliveryStatus, Long deliveriedAt, String description){

        smsDeliveryRecordMapper.updateDeliveryStatus(messageId, deliveryStatus,
                deliveriedAt == null ? null : new Timestamp(deliveriedAt), description);
    }

    public void updateDeliveryStatus(String messageId, String deliveryStatus, Long deliveriedAt, String description, int smsCount){

        smsDeliveryRecordMapper.updateDeliveryStatusAndCount(messageId, deliveryStatus,
                deliveriedAt == null ? null : new Timestamp(deliveriedAt), description, smsCount);
    }

    @Override
    public void updateTempMessageId(String tempMessageId, String realMessageId, String deliveryStatus, String description) {
        smsDeliveryRecordMapper.updateTempMessageId(tempMessageId, realMessageId, deliveryStatus, description);
    }

    @Override
    public void updateTempMessageIdExtra(String tempMessageId, String realMessageId, String deliveryStatus, String description,
                                         int countNum, String fee, String feeUnit) {
        smsDeliveryRecordMapper.updateTempMessageIdExtra(tempMessageId, realMessageId, deliveryStatus, description,
                countNum, StringUtils.isEmpty(fee) ? null : new BigDecimal(fee), feeUnit);
    }

    @Override
    public int updateFee(String messageId, String fee, String unit) {
        return smsDeliveryRecordMapper.updateFee(messageId, StringUtils.isEmpty(fee) ? null : new BigDecimal(fee), unit);
    }

    public List<DeliveryRecord> getRecords(long orgId, String mobile) {
        List<SmsDeliveryRecordEntity> records = smsDeliveryRecordMapper.getRecords(orgId, mobile);
        if (CollectionUtils.isEmpty(records)) {
            return new ArrayList<>();
        }
        List<DeliveryRecord> deliveryRecods = new ArrayList<>();
        for (SmsDeliveryRecordEntity item : records) {
            DeliveryRecord.Builder builder = DeliveryRecord.newBuilder()
                    .setOrgId(item.getOrgId())
                    .setChannel(item.getChannel())
                    .setMessageId(Strings.nullToEmpty(item.getMessageId()))
                    .setDeliveryStatus(item.getDeliveryStatus())
                    .setContent(Strings.nullToEmpty(item.getContent()))
                    .setCreated(item.getCreatedAt().getTime())
                    .setBizType(item.getBizType())
                    .setReceiver(item.getNationCode() + "-" + item.getMobile())
                    .setDescription(Strings.nullToEmpty(item.getDescription()));
            if (item.getDeliveriedAt() != null) {
                builder.setDeliveriedAt(item.getDeliveriedAt().getTime());
            }
            deliveryRecods.add(builder.build());
        }
        return deliveryRecods;
    }

    public SmsDeliveryRecordEntity getOneRecordByMobile(long orgId, String mobile) {
        return smsDeliveryRecordMapper.getOneRecordByMobile(orgId, mobile);
    }

    @Override
    public void updateCodeFeedbackStatus(String reqOrderId, boolean validResult, Long validTime) {
        smsDeliveryRecordMapper.updateFeedbackStatus(new Timestamp(validTime), validResult ? 1 : 0, reqOrderId);
    }
}
