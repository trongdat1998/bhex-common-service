package io.bhex.base.common.entity;

import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Data
@Table(name = "tb_router_config")
public class RouterConfigEntity {

    public static final Integer FORBID_CHANNEL = 1;

    @Id
    private Integer id;

    private Long orgId;

    private String description;

    private Integer routerType;

    private Integer bizType;

    private String channel;

    private String config;

    private Integer status; //0-不可用 1-可用

    private Timestamp created;

    private Timestamp updated;
}
