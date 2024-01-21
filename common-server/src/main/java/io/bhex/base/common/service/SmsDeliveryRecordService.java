package io.bhex.base.common.service;

import io.bhex.base.common.DeliveryRecord;
import io.bhex.base.common.entity.SmsDeliveryRecordEntity;
import io.bhex.base.common.util.SendChannelEnum;

import java.util.List;

/**
 * @Description:
 * @Date: 2018/12/6 下午7:33
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
public interface SmsDeliveryRecordService {

    void insertTencentSmsDeliveryRecord(Long orgId, String sid, String areaCode, String mobile, String bizType, String content, int spId);

    void insertSmsDeliveryRecord(Long orgId, SendChannelEnum channelEnum,
            String sid, String areaCode, String mobile, String status,
                                 String description, String bizType, String content,
                                 int spId, String reqOrderId, long userId);

    void insertSmsDeliveryRecord(Long orgId, SendChannelEnum channelEnum,
                                 String sid, String areaCode, String mobile, String status, String description,
                                 String bizType, String content, int spId, int smsCount, String reqOrderId, long userId);

    void insertAwsSmsDeliveryRecord(Long orgId, String messageId, String areaCode, String mobile, String regionName, String bizTyp, String content, int spId);

    void updateDeliveryStatus(String messageId, String deliveryStatus, Long deliveriedAt, String description);

    void updateDeliveryStatus(String messageId, String deliveryStatus, Long deliveriedAt, String description, int smsCount);

    void updateTempMessageId(String tempMessageId, String realMessageId, String deliveryStatus, String description);

    List<DeliveryRecord> getRecords(long orgId, String mobile);

    void updateTempMessageIdExtra(String tempMessageId, String realMessageId, String deliveryStatus, String description, int countNum, String fee, String unit);

    int updateFee(String messageId,  String fee, String unit);

    SmsDeliveryRecordEntity getOneRecordByMobile(long orgId, String mobile);

    void updateCodeFeedbackStatus(String reqOrderId, boolean validResult, Long validTime);
}
