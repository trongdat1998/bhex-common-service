package io.bhex.base.common.entity;


import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Data
@Table(name="tb_notice_tmpl")
public class NoticeTemplate {

    @Id
    private Integer id;

    private Long orgId;

    //服务类型  email sms voice-sms
    private String noticeType;


    private String businessType;

    private String language;

    private String templateContent;

    private String emailSubject;

    private String pushTitle;
    private String pushUrl;

    //消息类型，对应 VERIFY_CODE NOTICE
    private String msgType;

    //0-非整体发送 要同步到发送渠道 模板，在map中存在
    //
    //1-整体发送
    private Integer whole;



    private String scenario;

    //1-ok 0-disable
    private Integer status;


    private Timestamp created;

}
