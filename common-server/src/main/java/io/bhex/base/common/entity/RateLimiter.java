package io.bhex.base.common.entity;

import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;


@Data
@Table(name = "tb_rate_limiter")
public class RateLimiter {
    @Id
    private Integer id;

    private Long orgId;

    //private String description;

    private Integer senderType;

    private String bizType;

    private String limiterKey;

    private Integer limiterValue;

    //单位秒
    private Integer intervalSeconds;

}
