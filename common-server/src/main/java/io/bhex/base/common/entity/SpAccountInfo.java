package io.bhex.base.common.entity;

import lombok.Data;
import lombok.ToString;

import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

/**
 * @Description: 服务提供商信息
 * @Date: 2018/12/31 下午6:42
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
@Data
@Deprecated
@Table(name="tb_sp_account_info")
@ToString(exclude="secretKey")
public class SpAccountInfo {

    @Id
    private Long id;

    //服务类型  email sms voice-sms
    private String spType;

    //AWS SEND_CLOUD TENCENT
    private String channel;

    //0-否 1-是 2-backup
    private Integer defaultChannel;

    private Long orgId;

    /** 券商名字，此处是英文，发送回调会使用，url无法用中文*/
    private String orgAlias;

    private String accessKeyId;

    private String secretKey;

    //额外描述信息，使用json格式
    private String extraInfo;

    //1-ok 0-disable
    private int enable;

    private String requestUrl;

    private Timestamp created;

    private Timestamp updated;


}
