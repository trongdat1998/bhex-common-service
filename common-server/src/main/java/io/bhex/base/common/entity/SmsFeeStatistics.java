package io.bhex.base.common.entity;

import lombok.Data;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;

@Data
@Table(name = "tb_sms_fee_statistics")
public class SmsFeeStatistics {
    @Id
    @GeneratedValue(generator = "JDBC")
    private Long id;

    private Long orgId;
    private String channel;
    private Long statisticsTime;
    private String priceUnit;

    private BigDecimal price;
    private Long count;
    private Long lastRecordId;
}
