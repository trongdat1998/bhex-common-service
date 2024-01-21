package io.bhex.base.common.entity;

import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Data
@Table(name="tb_user_anti_phishing_code")
public class UserAntiPhishingCode {

    @Id
    private Long id;

    private Long orgId;

    private Long userId;

    private String antiPhishingCode;

    private String salt;

    private Timestamp createdAt;

    private Timestamp updatedAt;

}
