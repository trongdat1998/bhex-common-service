package io.bhex.base.common.service;

import io.bhex.base.common.SendMailRequest;
import io.bhex.base.common.entity.NoticeTemplate;
import io.bhex.base.common.entity.RouterConfigEntity;
import io.bhex.base.common.mapper.RouterConfigMapper;
import io.bhex.base.common.util.ChannelConstant;
import io.bhex.base.common.util.JsonUtil;
import io.bhex.base.common.util.MsgTypeEnum;
import io.bhex.base.common.util.NoticeTypeEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * router_type 1-禁用通道 2-通过邮箱后缀，优先使用某通道发送邮件  3-黑名单
 *
 */
@Slf4j
@Service
public class RouterConfigService {

    @Autowired
    private RouterConfigMapper routerConfigMapper;

    private static Set<String> forbidChannelSet = new HashSet<>();

    private static Map<String, SendMailRequest.Channel> emailSendChannelMap = new HashMap<>();

    private static Set<String> blackListSet = new HashSet<>();

    private static List<NoopConfig> noopConfigs = new ArrayList<>();

    private static Set<String> emailMxipBlackList = new HashSet<>();

    @Scheduled(initialDelay = 5_000, fixedRate = 60_000)
    private void loadConfigs(){
        Set<String> tempForbidChannelSet = new HashSet<>();
        Map<String, SendMailRequest.Channel> tempEmailSendChannelMap = new HashMap<>();
        Set<String> tempBlackListSet = new HashSet<>();
        List<NoopConfig> tempNoopConfigs = new ArrayList<>();
        Set<String> tempEmailMxipBlackList = new HashSet<>();

        List<RouterConfigEntity> configs = routerConfigMapper.getRouterConfigs();
        configs.stream().forEach(config -> {
            if (config.getRouterType() == ChannelConstant.RouterType.FORBID_CHANNEL.getValue()) {
                String value = config.getOrgId() + "-" + config.getRouterType()
                        + "-" + config.getBizType() + "-" + config.getChannel();
                tempForbidChannelSet.add(value);
            }
            else if (config.getRouterType() == ChannelConstant.RouterType.PRIORITY_CHANNEL.getValue()
                    && config.getBizType().equals(ChannelConstant.BIZ_TYPE_EMAIL)) {
                String[] emails = config.getConfig().split(",");
                for (String email : emails) {
                    SendMailRequest.Channel channel = config.getChannel().equals(ChannelConstant.SEND_CLOUD)
                            ? SendMailRequest.Channel.SEND_CLOUD : SendMailRequest.Channel.AWS;
                    tempEmailSendChannelMap.put(email, channel);
                }
            } else if (config.getRouterType() == ChannelConstant.RouterType.BLACK_LIST.getValue()){
                if (StringUtils.isNotEmpty(config.getConfig())) {
                    String[] values = config.getConfig().split(",");
                    for (String v : values) {
                        tempBlackListSet.add(config.getOrgId() + "-" + v);
                    }
                }
            } else if (config.getRouterType() == ChannelConstant.RouterType.NO_OP.getValue()){
                if (StringUtils.isNotEmpty(config.getConfig())) {
                    NoopConfig noopConfig = JsonUtil.defaultGson().fromJson(config.getConfig(), NoopConfig.class);
                    noopConfig.setOrgId(config.getOrgId());
                    noopConfig.setNoticeType(config.getBizType() == 1 ? NoticeTypeEnum.SMS : NoticeTypeEnum.EMAIL);
                    tempNoopConfigs.add(noopConfig);
                }
            } else if (config.getRouterType() == ChannelConstant.RouterType.EMAIL_MXIP_BLACK_LIST.getValue()){
                if (StringUtils.isNotEmpty(config.getConfig())) {
                    String[] values = config.getConfig().split(",");
                    for (String ip : values) {
                        tempEmailMxipBlackList.add(ip);
                    }
                }
            }
        });

        if (forbidChannelSet.size() != tempForbidChannelSet.size()) {
            log.info("change forbidChannel:{}", tempForbidChannelSet);
        }
        forbidChannelSet = tempForbidChannelSet;

        if (emailSendChannelMap.size() != tempEmailSendChannelMap.size()) {
            log.info("change emailSendChannelMap:{}", tempEmailSendChannelMap);
        }
        emailSendChannelMap = tempEmailSendChannelMap;

        if (tempBlackListSet.size() != blackListSet.size()) {
            log.info("change tempBlackListSet:{}", tempBlackListSet);
        }
        blackListSet = tempBlackListSet;

        noopConfigs = tempNoopConfigs;
        emailMxipBlackList = tempEmailMxipBlackList;
    }

    public boolean forbidChannel(Long orgId, boolean smsType, String channel){
        String key = "-1-" + (smsType ? ChannelConstant.BIZ_TYPE_SMS : ChannelConstant.BIZ_TYPE_EMAIL) + "-" + channel;
        if (channel.equalsIgnoreCase(ChannelConstant.AWS)) {
            //aws是所有券商公用的
            return forbidChannelSet.contains(0 + key);
        }
        return forbidChannelSet.contains(orgId + key);
    }

    public SendMailRequest.Channel getPriorityChannelForEmail(long orgId, String email){
        return emailSendChannelMap.get(email.split("@")[1].toLowerCase());
    }

    public boolean inBlackList(long orgId, String receiver) {
        boolean inBlackList = blackListSet.contains(orgId + "-" + receiver);
        if (!inBlackList) {
            inBlackList = blackListSet.contains(0 + "-" + receiver);
        }
        //log.warn("org:{} receiver:{} inBlackList：{} ", orgId, receiver, inBlackList);
        return inBlackList;
    }

    public Set<String> getEmailMxipBlackList() {
        return emailMxipBlackList;
    }

    public boolean orgInNoopConfig(long orgId, String nationalCode, String email, NoticeTemplate noticeTemplate) {

        Optional<NoopConfig> globalNoopConfigOptional = noopConfigs.stream()
                .filter(config -> config.getOrgId() == 0)
                .filter(config -> config.getNoticeType() == NoticeTypeEnum.valueOf(noticeTemplate.getNoticeType()))
                .findAny();
        if (globalNoopConfigOptional.isPresent()) {
            NoopConfig noopConfig = globalNoopConfigOptional.get();
            boolean inNoopConfig = orgInNoopConfig(noopConfig, 0, nationalCode, email, noticeTemplate);
            if (inNoopConfig) {
                return true;
            }
        }


        Optional<NoopConfig> noopConfigOptional = noopConfigs.stream()
                .filter(config -> config.getOrgId() == orgId)
                .filter(config -> config.getNoticeType() == NoticeTypeEnum.valueOf(noticeTemplate.getNoticeType()))
                .findAny();
        if (!noopConfigOptional.isPresent()) {
            return false;
        }

        NoopConfig noopConfig = noopConfigOptional.get();
        boolean inNoopConfig = orgInNoopConfig(noopConfig, orgId, nationalCode, email, noticeTemplate);
        if (inNoopConfig) {
            return true;
        }

        return false;
    }

    private boolean orgInNoopConfig(NoopConfig noopConfig, long orgId, String nationalCode, String email, NoticeTemplate noticeTemplate) {
        if (noopConfig.globalForbidden) {
            log.info("org:{} {} global forbidden", orgId, noticeTemplate.getNoticeType());
            return true;
        }

        if (noopConfig.getNoticeForbidden() != null && noopConfig.getNoticeForbidden().open && noticeTemplate.getMsgType().equals(MsgTypeEnum.NOTICE.name())) {
            List<String> scenarioes = noopConfig.getNoticeForbidden().getScenarioes();
            if (CollectionUtils.isEmpty(scenarioes)) {
                return true;
            }
            if (!CollectionUtils.isEmpty(scenarioes) && scenarioes.contains(noticeTemplate.getScenario())) {
                log.info("org:{} {} {} notice forbidden", orgId, noticeTemplate.getNoticeType(), noticeTemplate.getScenario());
                return true;
            }
        }

        if (noopConfig.getCodeForbidden() != null && noopConfig.getCodeForbidden().open && noticeTemplate.getMsgType().equals(MsgTypeEnum.VERIFY_CODE.name())) {
            List<String> scenarioes = noopConfig.getCodeForbidden().getScenarioes();
            if (CollectionUtils.isEmpty(scenarioes)) {
                return true;
            }
            if (!CollectionUtils.isEmpty(scenarioes) && scenarioes.contains(noticeTemplate.getScenario())) {
                log.info("org:{} {} {} verifycode forbidden", orgId, noticeTemplate.getNoticeType(), noticeTemplate.getScenario());
                return true;
            }
        }

        if (noopConfig.getForeignNoticeForbidden() != null && noopConfig.getForeignNoticeForbidden().open
                && StringUtils.isNotEmpty(nationalCode) && !nationalCode.equals("86")
                && noticeTemplate.getMsgType().equals(MsgTypeEnum.NOTICE.name())) {
            List<String> scenarioes = noopConfig.getForeignNoticeForbidden().getScenarioes();
            if (CollectionUtils.isEmpty(scenarioes)) {
                return true;
            }
            if (scenarioes.contains(noticeTemplate.getScenario())) {
                log.info("org:{} {} {} foreign notice forbidden", orgId, noticeTemplate.getNoticeType(), noticeTemplate.getScenario());
                return true;
            }
        }

        if (noopConfig.getForeignCodeForbidden() != null && noopConfig.getForeignCodeForbidden().open
                && StringUtils.isNotEmpty(nationalCode) && !nationalCode.equals("86")
                && noticeTemplate.getMsgType().equals(MsgTypeEnum.VERIFY_CODE.name())) {
            List<String> scenarioes = noopConfig.getForeignCodeForbidden().getScenarioes();
            if (CollectionUtils.isEmpty(scenarioes)) {
                return true;
            }
            if (scenarioes.contains(noticeTemplate.getScenario())) {
                log.info("org:{} {} {} foreig nverifycode forbidden", orgId, noticeTemplate.getNoticeType(), noticeTemplate.getScenario());
                return true;
            }
        }


        if (noopConfig.getForbiddenNoticeDomains() != null && noopConfig.getForbiddenNoticeDomains().open
                && !CollectionUtils.isEmpty(noopConfig.getForbiddenNoticeDomains().getDomains()) && StringUtils.isNotEmpty(email)
                && noticeTemplate.getMsgType().equals(MsgTypeEnum.NOTICE.name())) {
            boolean r = noopConfig.getForbiddenNoticeDomains().getDomains().stream()
                    .anyMatch(d -> email.toLowerCase().endsWith(d.toLowerCase()));
            if (r) {
                List<String> scenarioes = noopConfig.getForbiddenNoticeDomains().getScenarioes();
                if (CollectionUtils.isEmpty(scenarioes)) {
                    return true;
                }
                if (!CollectionUtils.isEmpty(scenarioes) && scenarioes.contains(noticeTemplate.getScenario())) {
                    log.info("org:{} {} {} {} notice domain forbidden", orgId, noticeTemplate.getNoticeType(), noticeTemplate.getScenario(), email);
                    return true;
                }
            }
        }


        if (noopConfig.getForbiddenCodeDomains() != null && noopConfig.getForbiddenCodeDomains().open
                && !CollectionUtils.isEmpty(noopConfig.getForbiddenCodeDomains().getDomains()) && StringUtils.isNotEmpty(email)
                && noticeTemplate.getMsgType().equals(MsgTypeEnum.VERIFY_CODE.name())) {
            boolean r = noopConfig.getForbiddenCodeDomains().getDomains().stream()
                    .anyMatch(d -> email.toLowerCase().endsWith(d.toLowerCase()));
            if (r) {
                List<String> scenarioes = noopConfig.getForbiddenCodeDomains().getScenarioes();
                if (CollectionUtils.isEmpty(scenarioes)) {
                    return true;
                }
                if (!CollectionUtils.isEmpty(scenarioes) && scenarioes.contains(noticeTemplate.getScenario())) {
                    log.info("org:{} {} {} {} verifycode domain forbidden", orgId, noticeTemplate.getNoticeType(), noticeTemplate.getScenario(), email);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {"global_forbidden":false,"foreign_notice_forbidden":{"open":true,"scenarioes":["bh","otc"]},"foreign_code_forbidden":{"open":true,"scenarioes":["bh","otc"]}, "notice_forbidden": {"open":true,"scenarioes":["bh","otc"]},"forbidden_notice_domains":{"open":true,"scenarioes":["bh","otc"],"domains":["qq.com","163.com"]}
     * }
     */
    @Data
    public static class NoopConfig {
        private long orgId;

        private NoticeTypeEnum noticeType;
        //p1
        private boolean globalForbidden;

        //p2
        private Node codeForbidden;
        private Node noticeForbidden;

        //p3 短信专用
        private Node foreignNoticeForbidden;
        private Node foreignCodeForbidden;

        //p3 邮件专用
        private Node forbiddenCodeDomains;
        private Node forbiddenNoticeDomains;

        @Data
        public static class Node {
            private boolean open;
            private List<String> scenarioes;
            private List<String> domains;
        }
    }


}
