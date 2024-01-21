package io.bhex.base.common.service.impl;

import com.google.common.base.Strings;
import io.bhex.base.common.DeliveryRecord;
import io.bhex.base.common.entity.EmailDeliveryRecordEntity;
import io.bhex.base.common.mapper.EmailDeliveryRecordMapper;
import io.bhex.base.common.service.EmailDeliveryRecordService;
import io.bhex.base.common.util.SendChannelEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;


/**
 * @Description:
 * @Date: 2019/1/30 下午5:56
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
@Slf4j
@Service
public class EmailDeliveryRecordServiceImpl implements EmailDeliveryRecordService {

    @Autowired
    private EmailDeliveryRecordMapper emailDeliveryRecordMapper;

    @Override
    public void insertSendCloudEmailDeliveryRecord(Long orgId, String sid, String email, String status, String description, String bizType, String content, int spId) {
        EmailDeliveryRecordEntity entity = new EmailDeliveryRecordEntity();
        entity.setOrgId(orgId);
        entity.setChannel("SEND_CLOUD");
        entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        entity.setMessageId(sid);
        entity.setEmail(email);
        entity.setContent(content);
        entity.setDeliveryStatus(status);
        entity.setDescription(description);
        entity.setBizType(bizType);
        entity.setSpId(spId);
        emailDeliveryRecordMapper.insertSelective(entity);
    }

    @Override
    public void insertAwsEmailDeliveryRecord(Long orgId, String messageId, String email, String bizType, String content, int spId) {
        EmailDeliveryRecordEntity entity = new EmailDeliveryRecordEntity();
        entity.setOrgId(orgId);
        entity.setChannel("AWS");
        entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        entity.setMessageId(messageId);
        entity.setEmail(email);
        entity.setContent(content);
        entity.setDeliveryStatus("UNKNOWN");
        entity.setDescription("");
        entity.setBizType(bizType);
        entity.setSpId(spId);
        emailDeliveryRecordMapper.insertSelective(entity);
    }

    public void insertEmailDeliveryRecord(Long orgId, SendChannelEnum channelEnum, String messageId, String email,
                                          String status, String bizType, String content, int spId, String reqOrderId, long userId) {
        EmailDeliveryRecordEntity entity = new EmailDeliveryRecordEntity();
        entity.setOrgId(orgId);
        entity.setChannel(channelEnum.name());
        entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        entity.setMessageId(messageId);
        entity.setEmail(email);
        entity.setContent(content);
        entity.setDeliveryStatus(status);
        entity.setDescription("");
        entity.setBizType(bizType);
        entity.setSpId(spId);
        entity.setReqOrderId(Strings.nullToEmpty(reqOrderId));
        entity.setCodeFeedbackTime(null);
        entity.setCodeFeedbackResult(-1);
        entity.setUserId(userId);
        emailDeliveryRecordMapper.insertSelective(entity);
    }

    @Override
    public void updateDeliveryStatus(String messageId, String deliveryStatus, Long deliveriedAt, String description) {
        emailDeliveryRecordMapper.updateDeliveryStatus(messageId, deliveryStatus,
                deliveriedAt == null ? null : new Timestamp(deliveriedAt), description);
    }

    @Override
    public void updateTempMessageId(String tempMessageId, String realMessageId, String deliveryStatus, String description) {
        emailDeliveryRecordMapper.updateTempMessageId(tempMessageId, realMessageId, deliveryStatus, description);
    }

    public List<DeliveryRecord> getRecords(long orgId, String email) {
        List<EmailDeliveryRecordEntity> records = emailDeliveryRecordMapper.getRecords(orgId, email);
        if (CollectionUtils.isEmpty(records)) {
            return new ArrayList<>();
        }
        List<DeliveryRecord> deliveryRecods = new ArrayList<>();
        for (EmailDeliveryRecordEntity item : records) {
            DeliveryRecord.Builder builder = DeliveryRecord.newBuilder()
                    .setOrgId(item.getOrgId())
                    .setChannel(item.getChannel())
                    .setMessageId(item.getMessageId())
                    .setDeliveryStatus(item.getDeliveryStatus())
                    .setContent(Strings.nullToEmpty(item.getContent()))
                    .setCreated(item.getCreatedAt().getTime())
                    .setBizType(item.getBizType())
                    .setReceiver(item.getEmail())
                    .setDescription(Strings.nullToEmpty(item.getDescription()));
            if (item.getDeliveriedAt() != null) {
                builder.setDeliveriedAt(item.getDeliveriedAt().getTime());
            }
            deliveryRecods.add(builder.build());
        }
        return deliveryRecods;
    }

    @Override
    public void updateCodeFeedbackStatus(String reqOrderId, boolean validResult, Long validTime) {
        emailDeliveryRecordMapper.updateFeedbackStatus(new Timestamp(validTime), validResult ? 1 : 0, reqOrderId);
    }
}
