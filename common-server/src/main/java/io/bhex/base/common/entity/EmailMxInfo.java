package io.bhex.base.common.entity;

import lombok.Data;

import javax.persistence.Table;
import java.sql.Timestamp;

@Data
@Table(name="tb_email_mx_info")
public class EmailMxInfo {

    private Long id;

    private String domain;

    private String mxIp;

    private String mxAddress;

    private Integer status;

    private Timestamp created;

    private Timestamp updated;
}
