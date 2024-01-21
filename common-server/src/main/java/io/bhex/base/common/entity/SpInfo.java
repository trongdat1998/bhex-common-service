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
@Table(name="tb_sp_info")
@ToString(exclude="secretKey")
public class SpInfo {


    @Id
    private Integer id;

    private Long orgId;

    //AWS SEND_CLOUD TENCENT
    private String channel;

    //服务类型  email sms voice-sms
    private String noticeType;

    //服务类型 VERIFY_CODE NOTICE MARKET
    private String msgType;

    //
    private Integer position;

    private Integer weight;

    private String accessKeyId;

    private String secretKey;

    //额外描述信息，使用json格式
    private String configInfo;

    private String requestUrl;

    /** BHEX BHOP*/
    private String env;

    //1-ok 0-disable
    private Integer status;

    private  Integer supportWhole;

    private String appId;

    private Integer canSyncSmsTmpl;

    private Timestamp created;

    private Timestamp updated;


}
