package io.bhex.base.common.entity;

import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Data
@Table(name="tb_email_template")
public class EmailTemplate {
    @Id
    private Long id;

    private Long orgId;

    private String templateContent;

    private String language;

    private Timestamp created;

    private Timestamp updated;

}
