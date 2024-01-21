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
@Table(name = "tb_app_push_record")
public class AppPushRecord {

    @Id
    @GeneratedValue(generator="JDBC")
    private Long id;

    private Long orgId;

    private String pushChannel; //HUWEI APNS


    private String reqOrderId = "";

    private String pushTokens;

    private String pushTitle;

    private String pushSummary;

    private String pushContent;

    private String pushUrl;

    private Integer pushUrlType;

    private String pushUrlData;

    private String customData; //自定义数据，统一为json格式

    private String appChannel; //app的发行通道 testflight appstore等

    private String messageId;

    private String bizType;

    private Integer spId;

    private Timestamp createdAt;

    private Timestamp updatedAt;

    private Integer status; //0-初始化 1-成功 2-push失败

    //private String clickTraceData; //追踪用户点击的链接参数

    private String pushResult;

}
