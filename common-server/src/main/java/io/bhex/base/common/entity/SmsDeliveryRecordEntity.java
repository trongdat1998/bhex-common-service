package io.bhex.base.common.entity;

import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * @Description:
 * @Date: 2018/12/6 下午2:16
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */

@Data
@Table(name = "tb_sms_delivery_record")
public class SmsDeliveryRecordEntity {
    @Id
    private Long id;
    private Long orgId;
    private String channel;
    private String regionName;
    private String nationCode;
    private String mobile;
    private String messageId;
    private String deliveryStatus;
    private BigDecimal price;
    private String priceUnit;
    private String content;
    private Timestamp createdAt;
    private Timestamp deliveriedAt;
    private String description;
    private String bizType;
    private Integer spId;
    private Integer countNum;

    private String reqOrderId = "";
    private Timestamp codeFeedbackTime = null;
    private Integer codeFeedbackResult = 0;
    private Long userId = 0L;
}
