package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.EmailDeliveryRecordEntity;
import io.bhex.base.common.entity.SmsDeliveryRecordEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

/**
 * @Description:
 * @Date: 2019/2/1 下午1:23
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
@Component
@org.apache.ibatis.annotations.Mapper
public interface SmsDeliveryRecordMapper extends Mapper<SmsDeliveryRecordEntity> {

    @Update("update tb_sms_delivery_record set delivery_status=#{deliveryStatus},price=#{price}" +
            " where message_id=#{messageId} and channel='AWS' ")
    int updateAwsInfo(@Param("messageId") String messageId, @Param("deliveryStatus") String deliveryStatus,
                      @Param("price") BigDecimal price);

    @Update("update tb_sms_delivery_record set delivery_status=#{deliveryStatus},deliveried_at=#{deliveriedAt}," +
            "description=#{description} where message_id=#{messageId}")
    int updateDeliveryStatus(@Param("messageId") String messageId, @Param("deliveryStatus") String deliveryStatus,
                             @Param("deliveriedAt") Timestamp deliveriedAt, @Param("description") String description);

    @Update("update tb_sms_delivery_record set delivery_status=#{deliveryStatus},deliveried_at=#{deliveriedAt}," +
            "description=#{description},count_num=#{smsCount} where message_id=#{messageId}")
    int updateDeliveryStatusAndCount(@Param("messageId") String messageId, @Param("deliveryStatus") String deliveryStatus,
                             @Param("deliveriedAt") Timestamp deliveriedAt, @Param("description") String description, @Param("smsCount") int smsCount);

    @Update("update tb_sms_delivery_record set delivery_status=#{deliveryStatus}," +
            "description=#{description},message_id=#{realMessageId} where message_id=#{messageId}")
    int updateTempMessageId(@Param("messageId") String messageId, @Param("realMessageId") String realMessageId,
                            @Param("deliveryStatus") String deliveryStatus, @Param("description") String description);

    @Update("update tb_sms_delivery_record set delivery_status=#{deliveryStatus}," +
            "description=#{description},message_id=#{realMessageId},count_num=#{smsCount}" +
            ",price=#{fee},price_unit=#{feeUnit} where message_id=#{messageId}")
    int updateTempMessageIdExtra(@Param("messageId") String messageId, @Param("realMessageId") String realMessageId,
                            @Param("deliveryStatus") String deliveryStatus, @Param("description") String description,
                                 @Param("smsCount") int smsCount, @Param("fee") BigDecimal fee, @Param("feeUnit") String feeUnit);

    @Update("update tb_sms_delivery_record set price=#{fee},price_unit=#{feeUnit} where message_id=#{messageId}")
    int updateFee(@Param("messageId") String messageId, @Param("fee") BigDecimal fee, @Param("feeUnit") String feeUnit);

    @Select("select count(*) from tb_sms_delivery_record where org_id=#{orgId} and mobile=#{mobile} and biz_type=#{bizType}" +
            " and created_at between #{start} and #{end}  and delivery_status = 'SUCCESS' ")
    int countByCreated(@Param("orgId") long orgId,
                       @Param("mobile") String mobile,
                       @Param("bizType")  String bizType,
                       @Param("start") Timestamp start,
                       @Param("end") Timestamp end);

    @Select("select * from tb_sms_delivery_record where org_id=#{orgId} and mobile=#{mobile} and delivery_status = 'SUCCESS' limit 1")
    SmsDeliveryRecordEntity getOneRecordByMobile(@Param("orgId") long orgId, @Param("mobile") String mobile);


    @Select({"<script>" +
            "select * from tb_sms_delivery_record where mobile = #{mobile} " ,
            "<when test='orgId > 0'>",
            " AND org_id = #{orgId} ",
            "</when>",
            " order by id desc limit 100" ,
            "</script>"})
    List<SmsDeliveryRecordEntity> getRecords(@Param("orgId") long orgId, @Param("mobile") String mobile);

    @Select("select * from tb_sms_delivery_record where id > #{lastId} order by id asc limit #{limit}")
    List<SmsDeliveryRecordEntity> getRecordsByLastId(@Param("lastId") long lastId, @Param("limit") long limit);

    @Update("update tb_sms_delivery_record set code_feedback_time=#{codeFeedbackTime}," +
            "code_feedback_result=#{codeFeedbackResult} where req_order_id=#{reqOrderId}")
    int updateFeedbackStatus(@Param("codeFeedbackTime") Timestamp codeFeedbackTime,
                             @Param("codeFeedbackResult") Integer codeFeedbackResult,
                             @Param("reqOrderId") String reqOrderId);
}
