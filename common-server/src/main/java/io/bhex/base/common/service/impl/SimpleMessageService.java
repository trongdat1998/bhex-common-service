package io.bhex.base.common.service.impl;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.*;
import io.bhex.base.common.entity.*;
import io.bhex.base.common.entity.EmailTemplate;
import io.bhex.base.common.mapper.*;
import io.bhex.base.common.service.SmsTemplateService;
import io.bhex.base.common.util.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SimpleMessageService  implements ApplicationContextAware {

    public static List<SpInfo> spInfos = new ArrayList<>();
    private static Map<Long,BrokerInfo> brokerInfoMap = new HashMap<>();
    private static Map<String,NoticeTemplate>  noticeTemplateMap = new HashMap<>();
    private static Map<String,SmsTmplateMapping>  smsTemplateMap = new HashMap<>();
    private static Map<String, RouterConfigEntity> routerConfigMap = new HashMap<>();

    @Autowired
    private SpInfoMapper spInfoMapper;
    @Autowired
    private BrokerInfoMapper brokerInfoMapper;
    @Autowired
    private NoticeTemplateMapper noticeTemplateMapper;
    @Autowired
    private SmsTmplMapMapper smsTmplMapMapper;
    @Autowired
    private RouterConfigMapper routerConfigMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private BrokerInfoService brokerInfoService;

    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;
    @Resource
    private AntiPhishingCodeService antiPhishingCodeService;


    private Map<SendChannelEnum, SmsTemplateService> templateServiceMap = new HashMap<>();
    private ApplicationContext ctx;
    @PostConstruct
    private void init(){
        templateServiceMap.put(SendChannelEnum.SEND_CLOUD, ctx.getBean(SendCloudSmsTemplateService.class));
        //templateServiceMap.put(SendChannelEnum.TENCENT, ctx.getBean(TencentSmsTemplateService.class));
    }

//    private static final List<String> SMS_INTL_CHANNELS = Lists.newArrayList(SendChannelEnum.AWS.name(), SendChannelEnum.TWILIO.name(),
//            SendChannelEnum.ZHUTONG_INTL.name(), SendChannelEnum.ZHIXIN_INTL.name());

    @PostConstruct
    @Scheduled(initialDelay = 120_000, fixedRate = 121_000)
    private void loadSpInfo(){
        Example example = new Example(SpInfo.class);
        example.orderBy("position").desc();
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("status", EnableStatusEnum.ENABLED.getValue());


        List<SpInfo> _spInfos = new ArrayList<>();
        List<SpInfo> _emainInfos = new ArrayList<>();
        spInfoMapper.selectByExample(example).forEach(s -> {
            try {
                byte[] decryptFrom = AESUtil.parseHexStr2Byte(s.getSecretKey());
                byte[] secretKey = AESUtil.decrypt(decryptFrom, s.getChannel()+s.getAccessKeyId());
                s.setSecretKey(new String(secretKey));
                if (s.getNoticeType().equals(NoticeTypeEnum.SMS.name())) {
                    _spInfos.add(s);
                } else {
                    if (s.getWeight() == 0 || s.getWeight() >= 1000) {
                        _emainInfos.add(s);
                    } else {
                        for (int i = 0; i < s.getWeight(); i++) {
                            _emainInfos.add(s);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("error load spinfo : {}", s.getId(), e);
            }

        });
        Collections.shuffle(_emainInfos);
        _spInfos.addAll(_emainInfos);
        spInfos = _spInfos;
        log.info("reload sp info end");
    }

    @PostConstruct
    @Scheduled(initialDelay = 120_000, fixedRate = 120_000)
    private void loadBrokerInfo(){
        Example example = new Example(BrokerInfo.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("status", EnableStatusEnum.ENABLED.getValue());

        Map<Long,BrokerInfo> _brokerInfoMap = new HashMap<>();
        List<BrokerInfo> brokerInfos = brokerInfoMapper.selectByExample(example);
        brokerInfos.forEach(b->{
            _brokerInfoMap.put(b.getOrgId(), b);
        });
        brokerInfoMap = _brokerInfoMap;
        log.info("reload BrokerInfo end");
    }



    @PostConstruct
    @Scheduled(initialDelay = 95_000, fixedRate = 300_000)
    private void loadNoticeTemplates(){
        Example example = new Example(NoticeTemplate.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("status", EnableStatusEnum.ENABLED.getValue());

        Map<String,NoticeTemplate>  _noticeTemplateMap = new HashMap<>();
        List<NoticeTemplate> noticeTemplates = noticeTemplateMapper.selectByExample(example);
        noticeTemplates.forEach(n->{
            String key = buildNoticeTemplateKey(n.getOrgId(), n.getNoticeType(), n.getBusinessType(), n.getLanguage());
            _noticeTemplateMap.put(key, n);
        });
        noticeTemplateMap = _noticeTemplateMap;
        log.info("reload NoticeTemplate end");
    }

    @PostConstruct
    @Scheduled(initialDelay = 600_000, fixedRate = 362_000)
    private void loadSmsTemplates(){
        Example example = new Example(SmsTmplateMapping.class).selectProperties("id","spId","signName","originTmplId",
                "targetChannel","targetTmplId");
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("verifyStatus", VerifyStatusEnum.SUCESS.getValue());

        Map<String,SmsTmplateMapping>  _smsTemplateMap = new HashMap<>();
        List<SmsTmplateMapping> smsTmplateMappings = smsTmplMapMapper.selectByExample(example);
        smsTmplateMappings.forEach(n -> {
            String key = buildSmsTemplateKey(n.getSpId() , n.getSignName() , n.getOriginTmplId());
            _smsTemplateMap.put(key, n);
        });
        smsTemplateMap = _smsTemplateMap;
        log.info("reload SmsTemplate end");
    }

    @PostConstruct
    @Scheduled(initialDelay = 120_000, fixedRate = 117_000)
    private void loadRouterConfigs(){

        Map<String,RouterConfigEntity>  _routerConfigMap = new HashMap<>();
        List<RouterConfigEntity> routerConfigs = routerConfigMapper.getRouterConfigs();
        routerConfigs.forEach(n->{
            String key = buildRouterConfigKey(n.getOrgId() , n.getRouterType() , n.getBizType(), n.getChannel());
            _routerConfigMap.put(key, n);
        });
        routerConfigMap = _routerConfigMap;
        log.info("reload RouterConfig end");
    }

    private String buildRouterConfigKey(long orgId, int routerType, int bizType, String channel){
        return orgId + "-" + routerType + "-" + bizType + "-" + channel;
    }

    private String buildNoticeTemplateKey(long orgId, String noticeType, String businessType, String language){
        return orgId + noticeType + businessType + language;
    }

    private String buildSmsTemplateKey(int spId, String signName, int originTmplId){
        return spId + signName + originTmplId;
    }

    public SenderDTO getSmsSendInfo(SimpleSMSRequest request){
        long orgId = request.getOrgId();

        Telephone telephone = request.getTelephone();
        String businessType = request.getBusinessType().equals("") ? "GLOBAL" : request.getBusinessType();
        String language = request.getLanguage().equals("") ? "en_US" : request.getLanguage();

        Combo2<SenderDTO,List<SpInfo>> combo2 = getSendChannel(orgId, NoticeTypeEnum.SMS, businessType, language);
        SenderDTO senderDTO = combo2.getV1();
        senderDTO.setTempMessageId("T_"+UUID.randomUUID().toString().replaceAll("-", ""));

        List<SpInfo> supportedSps = combo2.getV2();

        if (!telephone.getNationCode().equals("86") || businessType.equals("PROMOTION")) {
            //国处短信AWS/twillio, 营销短信走aws
//            Predicate<SpInfo> filterFunc = s -> {
//                if (businessType.equals("PROMOTION") && s.getChannel().equals(SendChannelEnum.AWS.name())) {
//                    return true;
//                }
//                if (!businessType.equals("PROMOTION")) {
//                    if (SMS_INTL_CHANNELS.contains(s.getChannel())) {
//                        return true;
//                    }
//                }
//                return false;
//            };
//
//            supportedSps = supportedSps.stream().filter(filterFunc).collect(Collectors.toList());
//            if (CollectionUtils.isEmpty(supportedSps)) {
//                throw new RuntimeException("no international channel");
//            }

            List<SpInfo> personalSps =  supportedSps.stream()
                    .filter(s -> s.getPosition() >= 1000)
                    .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(personalSps)) {
                supportedSps = Lists.newArrayList(personalSps);
            }


            long lastSpId = getCachedSpId(request.getTelephone().getMobile(), senderDTO.getScenario(), request.getBusinessType());
            supportedSps = filterSps(supportedSps, orgId, lastSpId);
            if (CollectionUtils.isEmpty(supportedSps)) {
                throw new RuntimeException("no international channel");
            }

            senderDTO.setWholeSend(true);
            senderDTO.setSpInfo(supportedSps.get(0));
            return senderDTO;
        } else { //国内短信
            long lastSpId = getCachedSpId(request.getTelephone().getMobile(), senderDTO.getScenario(), request.getBusinessType());
//            supportedSps = supportedSps.stream()
//                    .filter(s -> !SMS_INTL_CHANNELS.contains(s.getChannel()))
//                    .collect(Collectors.toList());

            List<SpInfo> personalSps =  supportedSps.stream()
                    .filter(s -> s.getPosition() >= 1000)
                    .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(personalSps)) {
                supportedSps = Lists.newArrayList(personalSps);
            }

            supportedSps = new ArrayList<>(filterSps(supportedSps, orgId, lastSpId));

            for (SpInfo sp : supportedSps) {

                int spid = sp.getId();
                String signName = senderDTO.getOriginSignName();
                if (sp.getChannel().equalsIgnoreCase(SendChannelEnum.ZHUTONG.name())) {
                    signName = "ZHUTONGSIGN";
                    spid = 0;
                }
                String key = buildSmsTemplateKey(spid, signName, senderDTO.getOriginTemplateId());
                if (smsTemplateMap.containsKey(key)) {
                    //走模板发送
                    SmsTmplateMapping smsTmplateMapping = smsTemplateMap.get(key);
                    senderDTO.setWholeSend(false);
                    senderDTO.setSpInfo(sp);
                    senderDTO.setTargetTmplId(smsTmplateMapping.getTargetTmplId());
                    return senderDTO;
                } else if (sp.getSupportWhole() == 1) {
                    //如果当前sp支持全量发送则退出走全局发送
                    senderDTO.setWholeSend(true);
                    break;
                }
            }

            if (senderDTO.isWholeSend()) {
                //全局发送
                Optional<SpInfo> optional = supportedSps.stream()
                        .filter(s -> s.getSupportWhole() == 1)
                        .findFirst();
                if (!optional.isPresent()) {
                    throw new RuntimeException("not supported whole send, no available channel");
                }
                senderDTO.setWholeSend(true);
                senderDTO.setSpInfo(optional.get());
                return senderDTO;
            }

            if (senderDTO.getSpInfo() == null) {
                throw new RuntimeException("no avaiable channel");
            }

            return senderDTO;
        }


    }

    /**
     * 验证码在10分钟内多次发送会切换通道来发送, sp中weight=0 或者 position=0 只用做备用通道
     * @param supportedSps
     * @param lastCachedSpId
     * @return
     */
    private List<SpInfo> filterSps(List<SpInfo> supportedSps, long orgId, long lastCachedSpId) {
        if (supportedSps.size() == 1) {
            return supportedSps;
        }
        List<SpInfo> availableSps = new ArrayList<>(supportedSps);

        boolean switchChannel = false;
        if (lastCachedSpId == 0) {
            //return availableSps;
        } else {
            Optional<SpInfo> lastSpOp = supportedSps.stream().filter(s -> s.getId() == lastCachedSpId).findFirst();
            if(lastSpOp.isPresent() && (lastSpOp.get().getPosition() < 1000 || lastSpOp.get().getWeight() < 1000)) { //1000及以上就不走备用通道了
                //通道切换
                List<SpInfo> tempSps = supportedSps.stream()
                        .filter(s -> s.getId() != lastCachedSpId)
                        .collect(Collectors.toList());
                if (CollectionUtils.isEmpty(tempSps)) {
                    //如果过滤完 没有可发送通道，那就算了
                    //return availableSps;
                } else {
                    switchChannel = true;
                    availableSps = tempSps;
                }
            }
        }


        if (!switchChannel) {
            //没有切换通道的话，用自己的通道发送
            List<SpInfo> tempSps = supportedSps.stream()
                    .filter(s -> s.getOrgId() == orgId)
                    .filter(s -> (s.getNoticeType().equals(NoticeTypeEnum.EMAIL.name()) && s.getWeight() > 10) ||
                            (s.getNoticeType().equals(NoticeTypeEnum.SMS.name()) &&  s.getPosition() > 10))
                    .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(tempSps)) {
                availableSps = tempSps;
            }
        }

        if (availableSps.size() >= 2) {
            //过滤掉 postion<10 或者 weight<10的通道
            List<SpInfo> tempSps = availableSps.stream()
                    .filter(s -> (s.getNoticeType().equals(NoticeTypeEnum.EMAIL.name()) && s.getWeight() > 10) ||
                            (s.getNoticeType().equals(NoticeTypeEnum.SMS.name()) &&  s.getPosition() > 10))
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(tempSps)) {
                //如果过滤完 没有可发送通道，那就算了
                return availableSps;
            } else {
                availableSps = tempSps;
            }
        }
        return availableSps;
    }

    private long getCachedSpId(String receiver, String scenario, String businessType) {
        String key = receiver + "-" + businessType;
//        if (Strings.nullToEmpty(scenario).startsWith("otc")) {
//            key = receiver + "-otc";
//        }
        String spIdObj = redisTemplate.opsForValue().get(key);
        long lastSpId = spIdObj == null ? 0 : Long.parseLong(spIdObj);
        return lastSpId;
    }

    public void cacheSendedSp(MsgTypeEnum msgType, String scenario, String receiver, String businessType, long spId) {
//        if (!msgType.equals(MsgTypeEnum.VERIFY_CODE) && !scenario.startsWith("otc")) {
//            return;
//        }
        if (!msgType.equals(MsgTypeEnum.VERIFY_CODE)) {
            return;
        }
        String key = receiver + "-" + businessType;
        if (Strings.nullToEmpty(scenario).startsWith("otc")) {
            key = receiver + "-otc";
        }
        redisTemplate.opsForValue().set(key, spId + "", 5, TimeUnit.MINUTES);
    }

    private long counter = 0;

    public SenderDTO getPushSendInfo(long orgId, String appId, String pushChannel, String reqBusinessType, String reqLanguage,  String reqAppChannel) {
        String businessType = reqBusinessType.isEmpty() ? "GLOBAL" : reqBusinessType;
        String language = reqLanguage.isEmpty() ? "en_US" : reqLanguage;

        Combo2<SenderDTO, List<SpInfo>> combo2 = getSendChannel(orgId, NoticeTypeEnum.PUSH, businessType, language,
                pushChannel, appId);
        SenderDTO senderDTO = combo2.getV1();
        senderDTO.setTempMessageId("T_"+UUID.randomUUID().toString().replaceAll("-", ""));

        List<SpInfo> supportedSps = combo2.getV2();
        if (CollectionUtils.isEmpty(supportedSps)) {
            return senderDTO;
        }
        if (pushChannel.equals(PushChannelEnum.APPLE.name())) {
            for (SpInfo spInfo : supportedSps) {
                String configInfo = spInfo.getConfigInfo();
                if (!StringUtils.isEmpty(configInfo)) {
                    JsonObject jo = JsonUtil.defaultGson().fromJson(configInfo, JsonElement.class).getAsJsonObject();
                    String appChannel = GsonObjectUtil.getAsString(jo, "app_channel");
                    if (!StringUtils.isEmpty(appChannel) && reqAppChannel.toUpperCase().contains(appChannel.toUpperCase())) {
                        senderDTO.setSpInfo(spInfo);
                        break;
                    }
                }
            }
        } else {
            senderDTO.setSpInfo(supportedSps.get(0));
        }
        return senderDTO;
    }
    public SenderDTO getApnsSendInfo(ApnsNotification request) {
        long orgId = request.getOrgId();
        String businessType = request.getBusinessType().isEmpty() ? "GLOBAL" : request.getBusinessType();
        String language = request.getLanguage().isEmpty() ? "en_US" : request.getLanguage();

        Combo2<SenderDTO,List<SpInfo>> combo2 = getSendChannel(orgId, NoticeTypeEnum.PUSH, businessType, language, PushChannelEnum.APNS.name(), request.getAppId());
        SenderDTO senderDTO = combo2.getV1();
        senderDTO.setTempMessageId("T_"+UUID.randomUUID().toString().replaceAll("-", ""));

        List<SpInfo> supportedSps = combo2.getV2();
        for (SpInfo spInfo : supportedSps) {
            String configInfo = spInfo.getConfigInfo();
            if (!StringUtils.isEmpty(configInfo)) {
                JsonObject jo = JsonUtil.defaultGson().fromJson(configInfo, JsonElement.class).getAsJsonObject();
                String appChannel = jo.get("app_channel").getAsString();
                if (appChannel != null && appChannel.equalsIgnoreCase(request.getChannel())) {
                    senderDTO.setSpInfo(spInfo);
                    break;
                }
            }
        }
        return senderDTO;
    }

    public SenderDTO getEmailSendInfo(SimpleMailRequest request) {

        long orgId = request.getOrgId();
        String email = request.getMail();
        String businessType = request.getBusinessType().equals("") ? "GLOBAL" : request.getBusinessType();
        String language = request.getLanguage().equals("") ? "en_US" : request.getLanguage();

        Combo2<SenderDTO,List<SpInfo>> combo2 = getSendChannel(orgId, NoticeTypeEnum.EMAIL, businessType, language);
        SenderDTO senderDTO = combo2.getV1();
        senderDTO.setTempMessageId("T_"+UUID.randomUUID().toString().replaceAll("-", ""));

        List<SpInfo> supportedSps = combo2.getV2();
        if (businessType.equals("PROMOTION")) {
            //营销邮件 统一走aws
            Optional<SpInfo> optional = supportedSps.stream()
                    .filter(s->s.getChannel().equals(SendChannelEnum.AWS.name()))
                    .findFirst();
            if (!optional.isPresent()) {
                throw new RuntimeException("promotion email, no available aws channel");
            }
            senderDTO.setWholeSend(true);
            senderDTO.setSpInfo(optional.get());
            return senderDTO;
        }

        //处理发送优先级问题，如 163优先用sendcloud发送
        Predicate<SpInfo> priorityFilterFunction = sp -> {
            String priorityKey = buildRouterConfigKey(orgId, ChannelConstant.RouterType.PRIORITY_CHANNEL.getValue(),
                    2, sp.getChannel());
            if (!routerConfigMap.containsKey(priorityKey)) {
                priorityKey = buildRouterConfigKey(0, ChannelConstant.RouterType.PRIORITY_CHANNEL.getValue(),
                        2, sp.getChannel());
            }
            if (routerConfigMap.containsKey(priorityKey)) {
                String emails = routerConfigMap.get(priorityKey).getConfig();
                for (String e : emails.split(",")) {
                    if (email.toLowerCase().endsWith(e.toLowerCase())) {
                        senderDTO.setSpInfo(sp);
                        return true;
                    }
                }
            }
            return false;
        };
        List<SpInfo> _supportedSps = supportedSps.stream()
                .filter(priorityFilterFunction)
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(_supportedSps)) {
            //如果过滤后没有可用的，那只能用原有的通道发送了
            supportedSps = _supportedSps;
        }

        long lastSpId = getCachedSpId(request.getMail(), senderDTO.getScenario(), request.getBusinessType());
        supportedSps = filterSps(supportedSps, orgId, lastSpId);

        int size = supportedSps.size();
        int index = (int)((counter++)%size);
        senderDTO.setSpInfo(supportedSps.get(index));
        senderDTO.setAntiPhishingCode(antiPhishingCodeService.getAntiFishCode(orgId, request.getUserId()));

        return senderDTO;
    }

    public NoticeTemplate getNoticeTemplate(long orgId, NoticeTypeEnum noticeType, String businessType, String language, String defaultLanguage) {
        businessType = businessType.equals("") ? "GLOBAL" : businessType;
        language = language.equals("") ? "en_US" : language;
        String noticeTmplKey = buildNoticeTemplateKey(orgId, noticeType.name(), businessType, language);
        if (!noticeTemplateMap.containsKey(noticeTmplKey)) {
            noticeTmplKey = buildNoticeTemplateKey(orgId, noticeType.name(), businessType, defaultLanguage);
        }

        if (!noticeTemplateMap.containsKey(noticeTmplKey)) {
            noticeTmplKey = buildNoticeTemplateKey(0, noticeType.name(), businessType, language);
        }
        if (!noticeTemplateMap.containsKey(noticeTmplKey)) {
            noticeTmplKey = buildNoticeTemplateKey(0, noticeType.name(), businessType, defaultLanguage);
        }

        if (!noticeTemplateMap.containsKey(noticeTmplKey)) {
            throw new RuntimeException("not supported businessType:" + businessType);
        }

        NoticeTemplate tmpl = noticeTemplateMap.get(noticeTmplKey);
        return tmpl;
    }

    private Combo2<SenderDTO, List<SpInfo>> getSendChannel(long theOrgId, NoticeTypeEnum noticeType, String businessType, String language) {
        return getSendChannel(theOrgId, noticeType, businessType, language, null, null);
    }
    private Combo2<SenderDTO, List<SpInfo>> getSendChannel(long theOrgId, NoticeTypeEnum noticeType, String businessType, String language, String channel, String appId){
        SenderDTO dto = new SenderDTO();
        dto.setLanguage(language);
        dto.setBusinessType(businessType);
        long orgId;
        if (theOrgId < 6000 && theOrgId > 300) {
            //hard code,此区间为交易所，用600 BHOP来发送
            orgId = 600;
        } else {
            orgId = theOrgId;
        }
        BrokerInfo brokerInfo = brokerInfoMap.get(orgId);
        if (brokerInfo != null) {

            NoticeTemplate tmpl = getNoticeTemplate(orgId, noticeType, businessType, language, brokerInfo.getSupportedLanguages().get(0));

            List<SpInfo> supportedSps = spInfos.stream()
                    //.filter(s -> (channel == null) || channel.equals(s.getChannel())) // 为PUSH推送增加channel查询
                    //.filter(s -> (appId == null) || appId.equals(s.getAppId()))
                    .filter(s -> s.getNoticeType().equals(noticeType.name()))
                    .filter(s -> s.getOrgId() == orgId || s.getOrgId() == 0)
                    .filter(s -> s.getEnv().equals(brokerInfo.getEnv()))
                    .filter(s -> s.getMsgType().equals(tmpl.getMsgType()) || s.getMsgType().equals(MsgTypeEnum.ALL.name()))
                    .filter(s -> !(tmpl.getWhole() == 1 && s.getSupportWhole() == 0)) //如tencent不支持全量发送
                    .collect(Collectors.toList());

            if (noticeType == NoticeTypeEnum.PUSH) {
                supportedSps = supportedSps.stream()
                        .filter(s -> Strings.nullToEmpty(s.getAppId()).equals(appId))
                        .filter(s -> Strings.nullToEmpty(channel).equals(s.getChannel()))
                        .filter(s -> s.getOrgId() == orgId)
                        .collect(Collectors.toList());
            }

            //如果有自己的专有通道，就不用公用的了
            if (noticeType != NoticeTypeEnum.SMS) {
                List<SpInfo> personalSps =  supportedSps.stream()
                        .filter(s -> s.getWeight() >= 1000 || s.getPosition() >= 1000)
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(personalSps)) {
                    supportedSps = Lists.newArrayList(personalSps);
                }
            }

            dto.setWholeSend(tmpl.getWhole() == 1);
            dto.setEmailSubject(!StringUtils.isEmpty(tmpl.getEmailSubject()) ? tmpl.getEmailSubject() : brokerInfo.getOrgName());
            dto.setScenario(tmpl.getScenario());
            dto.setOriginTemplateId(tmpl.getId());
            dto.setOriginTemplateContent(tmpl.getTemplateContent());
            dto.setOrgName(brokerInfo.getOrgName());
            dto.setSignName(brokerInfo.getSignName());
            dto.setOriginSignName(brokerInfo.getSignName());
            dto.setMsgType(MsgTypeEnum.valueOf(tmpl.getMsgType()));
            if (noticeType == NoticeTypeEnum.EMAIL) {
                EmailTemplate titleImage = brokerInfoService.getEmailTemplate(orgId, language);
                if (titleImage != null) {
                    dto.setEmailTemplate(titleImage.getTemplateContent());
                }
            } else if (noticeType == NoticeTypeEnum.PUSH) {
                dto.setPushTitle(tmpl.getPushTitle());
                dto.setPushUrl(tmpl.getPushUrl());
            }
            return new Combo2<>(dto, supportedSps);
        } else {
            //非券商发送
            if (!businessType.equals("PROMOTION") && !businessType.equals("GLOBAL")) {
                throw new RuntimeException("no broker info : " + orgId +" "+businessType);
            }

            dto.setWholeSend(true);
            dto.setEmailSubject("");
            dto.setScenario("");
            dto.setOriginTemplateId(0);
            dto.setOriginTemplateContent("{content}");
            dto.setOrgName("");
            dto.setSignName("");

            List<SpInfo> supportedSps = spInfos.stream()
                    .filter(s-> s.getNoticeType().equals(noticeType.name()))
                    .filter(s -> s.getEnv().equals("BHOP"))
                    .filter(s -> s.getOrgId() == orgId || s.getOrgId() == 0)
                    .filter(s -> s.getChannel().equals(SendChannelEnum.AWS.name()))
                    .collect(Collectors.toList());
            return new Combo2<>(dto, supportedSps);
        }

    }


//    //@Scheduled(initialDelay = 10_000, fixedRate = 7200_000)
//    private void syncSmsTmpl() {
////        if (!new BhexEnv().isBHEX()) {
////            return;
////        }
//        log.info("sync sms template");
//        List<SpInfo> sps = spInfos.stream().filter(s ->s.getCanSyncSmsTmpl() == 1).collect(Collectors.toList());
//        if (CollectionUtils.isEmpty(sps)) {
//            return;
//        }
//        List<NoticeTemplate> tmpls = noticeTemplateMap.values().stream()
//                .filter(n->n.getLanguage().equals("zh_CN"))
//                .filter(n->n.getNoticeType().equals("SMS"))
//                .filter(n->n.getWhole() == 0)
//                .collect(Collectors.toList());
//        //smsTemplateMap.values();
//        for (SpInfo sp : sps) {
//            BrokerInfo brokerInfo = brokerInfoMap.get(sp.getOrgId());
//            String signName = sp.getEnv().equalsIgnoreCase("bhex") ? "BHEX" : brokerInfo.getSignName();
//            String orgName = sp.getEnv().equalsIgnoreCase("bhex") ? "BHEX" : brokerInfo.getOrgName();
//
//            //按orgid倒序排队，这样自制化模板只同步自己设置的
//            List<NoticeTemplate> list = tmpls.stream()
//                    .sorted((a,b) -> (int)(b.getOrgId() - a.getOrgId()))
//                    .collect(Collectors.toList());
//            for (NoticeTemplate tmpl : list) {
//                if (smsTemplateMap.containsKey(buildSmsTemplateKey(sp.getId(), signName, tmpl.getId()))) {
//                    continue;
//                }
//                Example example = new Example(SmsTmplateMapping.class);
//                Example.Criteria criteria = example.createCriteria();
//                criteria.andEqualTo("spId", sp.getId());
//                criteria.andEqualTo("signName", signName);
//                criteria.andEqualTo("originTmplId", tmpl.getId());
//                criteria.andEqualTo("targetChannel", sp.getChannel());
//                if (smsTmplMapMapper.selectOneByExample(example) != null) {
//                    continue;
//                }
//
//                SmsTmplateMapping mapping = new SmsTmplateMapping();
//                mapping.setSpId(sp.getId());
//                mapping.setSignName(signName);
//                mapping.setOriginTmplId(tmpl.getId());
//                mapping.setTargetChannel(sp.getChannel());
//
//                mapping.setVerifyStatus(VerifyStatusEnum.INIT.getValue());
//                mapping.setCreated(new Timestamp(System.currentTimeMillis()));
//                mapping.setUpdated(new Timestamp(System.currentTimeMillis()));
//                mapping.setTargetTmplName(signName + "-" + tmpl.getBusinessType() + "-" + tmpl.getLanguage());
//
//                String tmplContent = tmpl.getTemplateContent().replaceAll("~broker~", orgName);
//
//                mapping.setTargetTmplContent(tmplContent);
//
//                SmsTemplateService smsTemplateService = templateServiceMap.get(SendChannelEnum.valueOf(sp.getChannel()));
//
//                String targetTmplId = smsTemplateService.create(mapping.getTargetTmplName(),
//                        mapping.getTargetTmplContent(),
//                        signName, MsgTypeEnum.valueOf(tmpl.getMsgType()),
//                        InternationalEnum.HOME, sp.getAccessKeyId(), sp.getSecretKey());
//                mapping.setTargetTmplId(targetTmplId);
//                boolean success = smsTemplateService.submitVerify(targetTmplId, sp.getAccessKeyId(), sp.getSecretKey());
//                if (success) {
//                    mapping.setVerifyStatus(VerifyStatusEnum.PROCESSING.getValue());
//                } else {
//                    log.error("ALERT submitTemplateVerify failed,spid:{} tml:{}", sp.getId(), targetTmplId);
//                }
//                smsTmplMapMapper.insertSelective(mapping);
//            }
//        }
//
//        Example example = new Example(SmsTmplateMapping.class);
//        Example.Criteria criteria = example.createCriteria();
//        criteria.andEqualTo("verifyStatus", VerifyStatusEnum.PROCESSING.getValue());
//        List<SmsTmplateMapping> mappings = smsTmplMapMapper.selectByExample(example);
//        for (SmsTmplateMapping m : mappings) {
//            Optional<SpInfo> optional = sps.stream().filter(s->s.getId() == m.getSpId()).findFirst();
//
//            if (!optional.isPresent()) {
//                continue;
//            }
//            SpInfo spInfo = optional.get();
//            SmsTemplateService smsTemplateService = templateServiceMap.get(SendChannelEnum.valueOf(spInfo.getChannel()));
//
//            boolean r = smsTemplateService.verifyied(m.getTargetTmplId(),
//                    spInfo.getAccessKeyId(), spInfo.getSecretKey());
//            if (r) {
//                m.setVerifyStatus(VerifyStatusEnum.SUCESS.getValue());
//            }
//            smsTmplMapMapper.updateByPrimaryKey(m);
//        }
//
//    }
//
//
//
//
//    @Scheduled(initialDelay = 5_000, fixedRate = 320_000)
//    public void loadZhutongBalance() {
//
//        boolean lock = RedisLockUtils.tryLock(redisTemplate, "loadZhutongBalance", 30_000);
//        if (!lock) {
//            log.info("loadZhutongBalance not get lock");
//            return;
//        }
//
//        List<SpInfo> zhutongIntlSps = spInfos.stream()
//                .filter(s -> s.getNoticeType().equals(NoticeTypeEnum.SMS.name()))
//                .filter(s -> s.getChannel().equals(SendChannelEnum.ZHUTONG_INTL.name()))
//                .collect(Collectors.toList());
//
//        if (!CollectionUtils.isEmpty(zhutongIntlSps)) {
//            Map<String, List<SpInfo>> group = zhutongIntlSps.stream().collect(Collectors.groupingBy(SpInfo::getAccessKeyId));
//            for (String username : group.keySet()) {
//                try {
//                    FormBody.Builder requestBuilder = new FormBody.Builder();
//                    requestBuilder.add("username", username);
//                    DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
//                    String tKey = dateFormat.format(new Date(System.currentTimeMillis() + 8*3600*1000));
//
//                    requestBuilder.add("tkey", tKey);
//                    requestBuilder.add("password", MD5Util.getMD5(MD5Util.getMD5(group.get(username).get(0).getSecretKey()) + tKey));
//
//                    Request smsRequest = new
//                            Request.Builder()
//                            .url("http://intl.zthysms.com/intBalance.do")
//                            .post(requestBuilder.build()).build();
//
//                    Response response = okHttpClient.newCall(smsRequest).execute();
//                    if (response.isSuccessful()) {
//                        String text = response.body().string();
//                        log.info("username:{} balance:{}", username, text);
//                        if (Double.parseDouble(text) < 1000) {
//                            log.error("username:{} balance:{} too low!!! please deposit!!!", username, text);
//                            spInfoMapper.disableZhuTongSpInfo(username);
//                        }
//                    }
//                } catch (Exception e) {
//                    log.info("error load balance", e);
//                }
//            }
//        }
//
//        List<SpInfo> zhutongSps = spInfos.stream()
//                .filter(s -> s.getNoticeType().equals(NoticeTypeEnum.SMS.name()))
//                .filter(s -> s.getChannel().equals(SendChannelEnum.ZHUTONG.name()))
//                .collect(Collectors.toList());
//
//        if (!CollectionUtils.isEmpty(zhutongSps)) {
//            Map<String, List<SpInfo>> group = zhutongSps.stream().collect(Collectors.groupingBy(SpInfo::getAccessKeyId));
//            for (String username : group.keySet()) {
//                try {
//                    ZhuTongBaseModel baseModel = new ZhuTongBaseModel();
//                    baseModel.setUsername(username);
//                    Long tKey = System.currentTimeMillis()/1000;
//                    baseModel.setTKey(tKey);
//                    baseModel.setPassword(group.get(username).get(0).getSecretKey());
//                    String data = JsonUtil.defaultGson().toJson(baseModel);
//
//                    RequestBody requestBody =
//                            RequestBody.create(MediaType.parse("application/json;charset=utf-8;"), data);
//                    Request smsRequest = new
//                            Request.Builder()
//                            .url("https://api.mix2.zthysms.com/v2/balance")
//                            .post(requestBody).build();
//
//                    Response response = okHttpClient.newCall(smsRequest).execute();
//                    if (response.isSuccessful()) {
//                        String text = response.body().string();
//                        JsonObject jo = JsonUtil.defaultGson().fromJson(text, JsonElement.class).getAsJsonObject();
//                        //JSONObject jo = JSON.parseObject(text);
//                        log.info("username:{} sumSms:{}", username, text);
//                        if (jo.get("code").getAsInt() == 200 && jo.get("sumSms").getAsInt() < 5000) {
//                            log.error("username:{} sumSms:{} too low!!! please deposit!!!", username, text);
//                            spInfoMapper.disableZhuTongSpInfo(username);
//                        }
//                    }
//                } catch (Exception e) {
//                    log.info("error load balance", e);
//                }
//            }
//        }
//    }

    public SpInfo getSpInfo(long id) {
        Optional<SpInfo> optional = spInfos.stream().filter(s -> s.getId()==id).findFirst();
        return optional.isPresent() ? optional.get() : null;
    }

    public List<SpInfo> getSpInfos() {
        return spInfos;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.ctx = applicationContext;
    }





}
