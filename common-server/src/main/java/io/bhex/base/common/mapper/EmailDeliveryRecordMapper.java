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
 * @Date: 2019/2/1 下午3:00
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
@Component
@org.apache.ibatis.annotations.Mapper
public interface EmailDeliveryRecordMapper extends Mapper<EmailDeliveryRecordEntity> {

    @Update("update tb_email_delivery_record set delivery_status=#{deliveryStatus} " +
            " where message_id=#{messageId} and channel='AWS' ")
    int updateAwsInfo(@Param("messageId") String messageId, @Param("deliveryStatus") String deliveryStatus,
                      @Param("price") BigDecimal price);

    @Update("update tb_email_delivery_record set delivery_status=#{deliveryStatus},deliveried_at=#{deliveriedAt}," +
            "description=#{description} where message_id=#{messageId}")
    int updateDeliveryStatus(@Param("messageId") String messageId, @Param("deliveryStatus") String deliveryStatus,
                             @Param("deliveriedAt") Timestamp deliveriedAt, @Param("description") String description);

    @Update("update tb_email_delivery_record set delivery_status=#{deliveryStatus}," +
            "description=#{description},message_id=#{realMessageId} where message_id=#{messageId}")
    int updateTempMessageId(@Param("messageId") String messageId, @Param("realMessageId") String realMessageId,
                             @Param("deliveryStatus") String deliveryStatus, @Param("description") String description);

    @Select("select count(*) from tb_email_delivery_record where  org_id=#{orgId} and email=#{email}  and biz_type=#{bizType} " +
            " and created_at between #{start} and #{end} and delivery_status = 'SUCCESS' ")
    int countByCreated(@Param("orgId") long orgId,
                       @Param("email") String email,
                       @Param("bizType")  String bizType,
                       @Param("start") Timestamp start,
                       @Param("end") Timestamp end);

    @Select({"<script>" +
            "select * from tb_email_delivery_record where email = #{email}" ,
            "<when test='orgId > 0'>",
            "AND org_id = #{orgId}",
            "</when>",
            " order by id desc limit 100" ,
            "</script>"})
    List<EmailDeliveryRecordEntity> getRecords(@Param("orgId") long orgId, @Param("email") String email);


    @Update("update tb_email_delivery_record set code_feedback_time=#{codeFeedbackTime}," +
            "code_feedback_result=#{codeFeedbackResult} where req_order_id=#{reqOrderId}")
    int updateFeedbackStatus(@Param("codeFeedbackTime") Timestamp codeFeedbackTime,
                             @Param("codeFeedbackResult") Integer codeFeedbackResult,
                             @Param("reqOrderId") String reqOrderId);
}
