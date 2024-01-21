package io.bhex.base.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "tb_app_push_switch")
public class AppPushSwitch {
    @Id
    @GeneratedValue(generator="JDBC")
    private Long id;

    private Long orgId;

    private String switchType;

    private Integer status; //0-关 1-开

    private Timestamp createdAt;

    private Timestamp updatedAt;
}
