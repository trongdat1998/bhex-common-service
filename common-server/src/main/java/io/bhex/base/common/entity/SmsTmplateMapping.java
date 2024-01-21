package io.bhex.base.common.entity;

import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Data
@Table(name = "tb_sms_tmpl_map")
public class SmsTmplateMapping {
    @Id
    private Integer id;

    private Integer spId;

    private String signName;

    private Integer originTmplId;

    private String targetChannel;
    private String targetTmplId;

    private String targetTmplContent;

    private String targetTmplName;

    private Integer verifyStatus; //0-init 1-申请中 2-成功 3-failed
    private String remark;
    private Timestamp updated;
    private Timestamp created;
}
