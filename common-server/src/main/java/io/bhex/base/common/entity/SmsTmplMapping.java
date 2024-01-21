package io.bhex.base.common.entity;

import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Data
@Deprecated
@Table(name = "tb_sms_tmpl_mapping")
public class SmsTmplMapping {
    @Id
    private Integer id;
    private Long orgId;

    private String sourceChannel;
    private String sourceTmplId;
    private String targetChannel;
    private String targetTmplId;
    private String tmplContent;
    private Integer status; //0-不可用 1-可用
    private Timestamp created;
}
