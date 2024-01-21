package io.bhex.base.common.service;

import io.bhex.base.common.DeliveryRecord;
import io.bhex.base.common.MessageType;
import io.bhex.base.common.util.SendChannelEnum;

import java.util.List;

/**
 * @Description:
 * @Date: 2018/12/6 下午7:33
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
public interface EmailDeliveryRecordService {

    void insertEmailDeliveryRecord(Long orgId, SendChannelEnum channelEnum, String messageId, String email,
                                   String status, String bizType, String content, int spId, String reqOrderId, long userId);

    void insertSendCloudEmailDeliveryRecord(Long orgId, String sid, String email, String status, String description, String bizType, String content, int spId);

    void insertAwsEmailDeliveryRecord(Long orgId, String messageId, String email, String bizType, String content, int spId);

    void updateDeliveryStatus(String messageId, String deliveryStatus, Long deliveriedAt, String description);

    void updateTempMessageId(String tempMessageId, String realMessageId, String deliveryStatus, String description);

    List<DeliveryRecord> getRecords(long orgId, String email);

    void updateCodeFeedbackStatus(String reqOrderId, boolean validResult, Long validTime);
}
