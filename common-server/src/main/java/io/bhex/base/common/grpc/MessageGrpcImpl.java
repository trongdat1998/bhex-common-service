package io.bhex.base.common.grpc;


import com.google.common.collect.Lists;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.protobuf.TextFormat;
import io.bhex.base.common.*;
import io.bhex.base.common.entity.AppPushRecord;
import io.bhex.base.common.entity.NoticeTemplate;
import io.bhex.base.common.entity.SmsDeliveryRecordEntity;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.sender.push.*;
import io.bhex.base.common.sender.v2.*;
import io.bhex.base.common.service.EmailDeliveryRecordService;
import io.bhex.base.common.service.RouterConfigService;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.service.impl.*;
import io.bhex.base.common.util.*;
import io.bhex.base.grpc.annotation.GrpcService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@GrpcService
public class MessageGrpcImpl extends MessageServiceGrpc.MessageServiceImplBase implements ApplicationContextAware {

    public static final String EMAIL_PATTERN = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])";
    private ApplicationContext ctx;
    @Autowired
    private SimpleMessageService messageService;
    @Resource
    private LimiterService limiterService;
    @Autowired
    private EmailMxInfoService emailMxInfoService;
    @Autowired
    private BrokerInfoService brokerInfoService;
    @Autowired
    private EmailDeliveryRecordService emailDeliveryRecordService;
    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private AntiPhishingCodeService antiPhishingCodeService;
    @Autowired
    private AppPushService appPushService;
    @Autowired
    private RouterConfigService routerConfigService;
    private Map<SendChannelEnum, ISmsSender> smsSenderMap = new HashMap<>();
    private Map<SendChannelEnum, IEmailSender> emailSenderMap = new HashMap<>();
    private Map<PushChannelEnum, IPushSender> pushSenderMap = new HashMap<>();
    private Map<Long, TaskExecutor> taskExecutorMap = new HashMap<>();

    @Autowired
    private ApnsSender apnsSender;

    private static final String PROCESSING_HASH_KEY = "message.processing";
    private PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    @Value("${verify-captcha:true}")
    private Boolean verifyCaptcha;

    @Value("${global-notify-type:1}")
    private Integer globalNotifyType;

    @PostConstruct
    public void init() {
        smsSenderMap.put(SendChannelEnum.TWILIO, ctx.getBean(TwilioSmsSender.class));
        smsSenderMap.put(SendChannelEnum.AWS, ctx.getBean(AwsSmsSenderV2.class));
        smsSenderMap.put(SendChannelEnum.SEND_CLOUD, ctx.getBean(SendCloudSmsSenderV2.class));
        smsSenderMap.put(SendChannelEnum.TENCENT, ctx.getBean(TencentSender.class));
        smsSenderMap.put(SendChannelEnum.ZHIXIN, ctx.getBean(ZhixinSmsSender.class));
        smsSenderMap.put(SendChannelEnum.ZHUTONG, ctx.getBean(ZhuTongSender.class));
        smsSenderMap.put(SendChannelEnum.ZHUTONG_INTL, ctx.getBean(ZhuTongIntlSender.class));
        smsSenderMap.put(SendChannelEnum.ZHIXIN_INTL, ctx.getBean(ZhixinIntlSmsSender.class));


        emailSenderMap.put(SendChannelEnum.AWS, ctx.getBean(AwsEmailSenderV2.class));
        emailSenderMap.put(SendChannelEnum.SEND_CLOUD, ctx.getBean(SendCloudEmailSenderV2.class));
        emailSenderMap.put(SendChannelEnum.TWILIO, ctx.getBean(TwilioEmailSender.class));
        emailSenderMap.put(SendChannelEnum.MAILGUN, ctx.getBean(MailGunSender.class));

        pushSenderMap.put(PushChannelEnum.APPLE, ctx.getBean(ApplePushSender.class));
        pushSenderMap.put(PushChannelEnum.HUAWEI, ctx.getBean(HuaweiSender.class));
        pushSenderMap.put(PushChannelEnum.FCM, ctx.getBean(FcmSender.class));
    }

    @Override
    public void sendSimpleMail(SimpleMailRequest theRequest, StreamObserver<MessageReply> observer) {
        try {
            SimpleMailRequest request = theRequest.toBuilder().setMail(theRequest.getMail().toLowerCase()).build();
            boolean validated = request.getMail().toLowerCase().matches(EMAIL_PATTERN);
            if (!validated) {
                log.info("error email:{}", request);
                observer.onNext(MessageReply.newBuilder().setSuccess(false).build());
                observer.onCompleted();
                return;
            }

            if (!verifyCaptcha) {
                observer.onNext(MessageReply.newBuilder().setSuccess(true).build());
                observer.onCompleted();
                return;
            }

            if (globalNotifyType == 2) {
                log.warn("The broker globalNotifyType only mobile!globalNotifyType={},email={}", globalNotifyType, request.getMail());
                observer.onNext(MessageReply.newBuilder().setSuccess(true).build());
                observer.onCompleted();
                return;
            }

            String resMsg = null;

            NoticeTemplate noticeTemplate = messageService.getNoticeTemplate(request.getOrgId(), NoticeTypeEnum.EMAIL, request.getBusinessType(), request.getLanguage(), "en_US");
            if (Lists.newArrayList("REGISTER", "BIND_EMAIL").contains(request.getBusinessType())
                    && routerConfigService.inBlackList(request.getOrgId(), request.getMail())) {
                resMsg = "BLACKLIST";
            }

            if (Lists.newArrayList("REGISTER", "BIND_EMAIL").contains(request.getBusinessType())  && emailMxInfoService.mxIpInBlackList(request.getMail())) {
                resMsg = "SUCCESS";
            }

            if (resMsg == null) {
                boolean inNoopConfig = routerConfigService.orgInNoopConfig(request.getOrgId(), null, request.getMail(), noticeTemplate);
                if (inNoopConfig) {
                    resMsg = "SUCCESS";
                }
            }

            if (resMsg == null) {
                Combo2<Boolean, String> combo2 = limiterService.overLimitEmail(request.getOrgId(),
                        request.getMail(), noticeTemplate.getScenario(), request.getParamsList(), request.getReqParamMap());
                if (combo2.getV1()) {
                    resMsg = "OverLimiter " + combo2.getV2();
                }
            }

            if (resMsg != null) {
                emailDeliveryRecordService.insertEmailDeliveryRecord(request.getOrgId(), SendChannelEnum.NO_OP,
                        "", request.getMail(), resMsg,
                        noticeTemplate.getScenario(),
                        request.getLanguage() + " " + request.getBusinessType(), 0, request.getReqOrderId(), request.getUserId());

                observer.onNext(MessageReply.newBuilder()
                        .setSuccess(true).setMessage("")
                        .build());
                observer.onCompleted();
                return;
            }

            SenderDTO senderDTO = messageService.getEmailSendInfo(request);
            if (!request.getEmailSubject().equals("")) {
                senderDTO.setEmailSubject(request.getEmailSubject());
            }
            log.info("sender org:{} type:{} language:{} sp:{}", request.getOrgId(),
                    request.getBusinessType(), request.getLanguage(), senderDTO.getSpInfo().getId());


            SpInfo spInfo = senderDTO.getSpInfo();
            SendChannelEnum sendChannel = SendChannelEnum.valueOf(spInfo.getChannel());
            IEmailSender emailSender = emailSenderMap.get(sendChannel);

            emailDeliveryRecordService.insertEmailDeliveryRecord(request.getOrgId(), sendChannel,
                    senderDTO.getTempMessageId(), request.getMail(), "NOTSENDED",
                    noticeTemplate.getScenario(), request.getLanguage() + " " + request.getBusinessType(),
                    senderDTO.getSpInfo().getId(), request.getReqOrderId(), request.getUserId());


            CompletableFuture.runAsync(() -> {
                String rc = MaskUtil.maskValidateCode(TextFormat.shortDebugString(request));
                try {
                    messageService.cacheSendedSp(senderDTO.getMsgType(), senderDTO.getScenario(), request.getMail(),
                            request.getBusinessType(), senderDTO.getSpInfo().getId());
                    if (request.getOrgId() == 6002 && request.getLanguage().equals("zh_CN")) {
                        senderDTO.setSignName("霍比特");
                        senderDTO.setEmailSubject(senderDTO.getEmailSubject().replace("HBTC", "霍比特"));
                    }
                    redisTemplate.opsForHash().increment(PROCESSING_HASH_KEY, senderDTO.getSpInfo().getId().toString(), 1L);
                    emailSender.send(request.getOrgId(), senderDTO, request);
                } catch (Exception e) {
                    if (!e.getMessage().contains("time") && !e.getMessage().contains("out")) {
                        log.error("error", e);
                    }
                    if (senderDTO.getMsgType() == MsgTypeEnum.VERIFY_CODE) {
                        log.error("send email error. param:{} msg:{}", rc, e.getMessage());
                        return;
                    } else {
                        log.warn("send email error. param:{} msg:{}", rc, e.getMessage());
                    }
                    try {
                        emailSender.send(request.getOrgId(), senderDTO, request);
                        log.warn("retry send email {} channel:{}", rc, senderDTO.getSpInfo().getChannel());
                    } catch (IOException e2) {
                        //retry fail
                        log.error("retry send email failed:{} channel:{} msg:{}", rc, senderDTO.getSpInfo().getChannel(), e2.getMessage());
                    }
                } finally {
                    redisTemplate.opsForHash().increment(PROCESSING_HASH_KEY, senderDTO.getSpInfo().getId().toString(), -1L);
                }
            }, newTaskExecutor(senderDTO.getSpInfo(), senderDTO.getSpInfo().getChannel(), NoticeTypeEnum.EMAIL));

            observer.onNext(MessageReply.newBuilder().setSuccess(true).build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("ALERT req:{} msg:{}", MaskUtil.maskValidateCode(TextFormat.shortDebugString(theRequest)), e.getMessage(), e);
            observer.onNext(MessageReply.newBuilder().setSuccess(false)
                    .setMessage(e.getMessage()).build());
            observer.onCompleted();
            return;
        }
    }

    @Override
    public void sendApnsNotification(ApnsNotification request, StreamObserver<MessageReply> responseObserver) {
        try {
            SenderDTO senderDTO = messageService.getApnsSendInfo(request);
            if (senderDTO.getSpInfo() == null) {
                log.info("send apns no spInfo, req:{}", MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)));
                responseObserver.onNext(MessageReply.newBuilder().setSuccess(false).setMessage("not supported").build());
                responseObserver.onCompleted();
                return;
            }
            CompletableFuture.runAsync( () -> {
                try {
                    apnsSender.send(senderDTO, request);
                } catch (Exception e) {
                    log.error("APNS Push send error req:{}", MaskUtil.maskValidateCode(request.toString()), e);
                }
            }, newTaskExecutor(senderDTO.getSpInfo(), senderDTO.getSpInfo().getChannel(), NoticeTypeEnum.PUSH));

            responseObserver.onNext(MessageReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("APNS Push send error req:{}", MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)), e);
            responseObserver.onNext(MessageReply.newBuilder().setSuccess(false)
                    .setMessage(e.getMessage()).build());
            responseObserver.onCompleted();
            return;
        }
    }

    @Override
    public void sendBusinessPush(SendBusinessPushRequest request, StreamObserver<MessageReply> responseObserver) {
        try {

            String pushChannel = request.getPushChannel().equalsIgnoreCase("apns") ? "APPLE" : request.getPushChannel().toUpperCase();
            SenderDTO senderDTO = messageService.getPushSendInfo(request.getOrgId(), request.getAppId(), pushChannel,
                    request.getBusinessType(), request.getLanguage(), request.getAppChannel());
            if (senderDTO.getSpInfo() == null) {
                log.info("sendBusinessPush no spInfo, req:{}", MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)));
                responseObserver.onNext(MessageReply.newBuilder().setSuccess(false).setMessage("not supported").build());
                responseObserver.onCompleted();
                return;
            }
            CompletableFuture.runAsync( () -> {
                try {
                    AppPushRecord record = AppPushRecord.builder()
                            .orgId(request.getOrgId())
                            .appChannel(request.getAppChannel())
                            .reqOrderId(request.getReqOrderId())
                            .pushTokens(String.join(",", request.getPushTokenList()))
                            .pushSummary("")
                            .pushTitle(senderDTO.getPushTitle())
                            .pushContent(senderDTO.getSendContent(Lists.newArrayList(), request.getReqParamMap()))
                            .pushUrl(senderDTO.getPushUrl())
                            .pushUrlType(2)
                            .pushUrlData(JsonUtil.defaultGson().toJson(request.getPushUrlDataMap()))
                            .pushChannel(pushChannel)
                            .messageId("")
                            .bizType(request.getBusinessType())
                            .spId(senderDTO.getSpInfo().getId())
                            .createdAt(new Timestamp(System.currentTimeMillis()))
                            .updatedAt(new Timestamp(System.currentTimeMillis()))
                            .status(0)
                            .build();
                    appPushService.insertRecord(record);

                    NoticeTemplate noticeTemplate = messageService.getNoticeTemplate(request.getOrgId(), NoticeTypeEnum.PUSH, request.getBusinessType(), request.getLanguage(), "en_US");
                    Combo2<Boolean, String> combo2 = limiterService.overLimitPush(request.getOrgId(),
                            request.getPushToken(0), noticeTemplate.getScenario(), Lists.newArrayList(), request.getReqParamMap());
                    if (combo2.getV1()) {
                        responseObserver.onNext(MessageReply.newBuilder().setSuccess(false).setMessage("OverLimiter " + combo2.getV2()).build());
                        responseObserver.onCompleted();
                        return;
                    }

                    pushSenderMap.get(PushChannelEnum.valueOf(pushChannel)).sendBusinessPush(record);
                } catch (Exception e) {
                    log.error("sendBusinessPush error req:{}", MaskUtil.maskValidateCode(request.toString()), e);
                }
            }, newTaskExecutor(senderDTO.getSpInfo(), senderDTO.getSpInfo().getChannel(), NoticeTypeEnum.PUSH));

            responseObserver.onNext(MessageReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("sendBusinessPush error req:{}", MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)), e);
            responseObserver.onNext(MessageReply.newBuilder().setSuccess(false)
                    .setMessage(e.getMessage()).build());
            responseObserver.onCompleted();
            return;
        }
    }

    @Override
    public void sendPush(SendPushRequest request, StreamObserver<MessageReply> responseObserver) {
        try {
            String pushChannel = request.getPushChannel().equalsIgnoreCase("apns") ? "APPLE" : request.getPushChannel().toUpperCase();
            Combo2<Boolean, String> combo2 = appPushService.canSendPush(request.getOrgId(), false, pushChannel);
            if (!combo2.getV1()) {
                responseObserver.onNext(MessageReply.newBuilder().setSuccess(false).setMessage(combo2.getV2()).build());
                responseObserver.onCompleted();
                return;
            }
            SenderDTO senderDTO = messageService.getPushSendInfo(request.getOrgId(), request.getAppId(), pushChannel,
                    "", "", request.getAppChannel());
            if (senderDTO.getSpInfo() == null) {
                log.info("sendPush no spInfo, req:{}", MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)));
                responseObserver.onNext(MessageReply.newBuilder().setSuccess(false).setMessage("not supported").build());
                responseObserver.onCompleted();
                return;
            }
            CompletableFuture.runAsync( () -> {
                try {
                    AppPushRecord record = AppPushRecord.builder()
                            .orgId(request.getOrgId())
                            .appChannel(request.getAppChannel())
                            .reqOrderId(request.getReqOrderId())
                            .pushTokens(String.join(",", request.getPushTokenList()))
                            .pushTitle(request.getTitle())
                            .pushContent(request.getContent())
                            .pushSummary(request.getSummary())
                            .pushUrl(request.getUrl())
                            .pushUrlType(request.getUrlType() == 1 ? 1 : 2)
                            .pushUrlData("{}")
                            .pushChannel(pushChannel)
                            .messageId("")
                            .bizType("")
                            .spId(senderDTO.getSpInfo().getId())
                            .createdAt(new Timestamp(System.currentTimeMillis()))
                            .updatedAt(new Timestamp(System.currentTimeMillis()))
                            .status(0)
                            .build();
                    appPushService.insertRecord(record);
                    pushSenderMap.get(PushChannelEnum.valueOf(pushChannel)).sendPush(record);
                } catch (Exception e) {
                    log.error("sendPush error req:{}", MaskUtil.maskValidateCode(request.toString()), e);
                }
            }, newTaskExecutor(senderDTO.getSpInfo(), senderDTO.getSpInfo().getChannel(), NoticeTypeEnum.PUSH));

            responseObserver.onNext(MessageReply.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("sendPush error req:{}", MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)), e);
            responseObserver.onNext(MessageReply.newBuilder().setSuccess(false)
                    .setMessage(e.getMessage()).build());
            responseObserver.onCompleted();
            return;
        }
    }

    @Override
    public void sendSimpleSMS(SimpleSMSRequest request, StreamObserver<MessageReply> observer) {
        try {

            Telephone telephone = request.getTelephone();
            boolean validated = validateTelephone(request.getOrgId(), telephone);
            if (!validated) {
                log.info("error telephone:{}", TextFormat.shortDebugString(request.getTelephone()));
                observer.onNext(MessageReply.newBuilder().setSuccess(false)
                        .setMessage("error phone number").build());
                observer.onCompleted();
                return;
            }

            if (!verifyCaptcha) {
                observer.onNext(MessageReply.newBuilder().setSuccess(true).build());
                observer.onCompleted();
                return;
            }


            if (globalNotifyType == 3) {
                String nationCode = telephone.getNationCode();
                String mobile = telephone.getMobile();
                log.warn("The broker globalNotifyType only email!globalNotifyType={},mobile={}", globalNotifyType, nationCode + mobile);
                observer.onNext(MessageReply.newBuilder().setSuccess(true).build());
                observer.onCompleted();
                return;
            }

            String resMsg = null;
            NoticeTemplate noticeTemplate = messageService.getNoticeTemplate(request.getOrgId(), NoticeTypeEnum.SMS, request.getBusinessType(), request.getLanguage(), "en_US");
            if (Lists.newArrayList("REGISTER", "BIND_MOBILE").contains(request.getBusinessType())
                    && routerConfigService.inBlackList(request.getOrgId(), request.getTelephone().getNationCode() + "" + request.getTelephone().getMobile())) {
                resMsg = "BLACKLIST";
            }

            if (resMsg == null) {
                boolean inNoopConfig = routerConfigService.orgInNoopConfig(request.getOrgId(), request.getTelephone().getNationCode(), null, noticeTemplate);
                if (inNoopConfig) {
                    resMsg = "SUCCESS";
                }
            }

            if (resMsg == null) {
                Combo2<Boolean, String> combo2 = limiterService.overLimitMobile(request.getOrgId(),
                        request.getTelephone().getMobile(), noticeTemplate.getScenario(), request.getParamsList(), request.getReqParamMap());
                if (combo2.getV1()) {
                    resMsg = "OverLimiter " + combo2.getV2();
                }
            }

            if (resMsg != null) {
                smsDeliveryRecordService.insertSmsDeliveryRecord(request.getOrgId(), SendChannelEnum.NO_OP, "",
                        telephone.getNationCode(), telephone.getMobile(), resMsg, "",
                        noticeTemplate.getScenario(), request.getLanguage() + " " + request.getBusinessType(),
                        0, request.getReqOrderId(), request.getUserId());

                observer.onNext(MessageReply.newBuilder()
                        .setSuccess(true).setMessage("")
                        .build());
                observer.onCompleted();
                return;
            }


            SenderDTO senderDTO = messageService.getSmsSendInfo(request);
            log.info("sender org:{} type:{} language:{} tmplid:{} sp:{}", request.getOrgId(),
                    request.getBusinessType(), request.getLanguage(),
                    senderDTO.getTargetTmplId(), senderDTO.getSpInfo().getId());

            if (!request.getSign().equals("")) {
                senderDTO.setSignName(request.getSign());
            }
            SendChannelEnum sendChannel = SendChannelEnum.valueOf(senderDTO.getSpInfo().getChannel());
            ISmsSender smsSender = smsSenderMap.get(sendChannel);

            smsDeliveryRecordService.insertSmsDeliveryRecord(request.getOrgId(), sendChannel, senderDTO.getTempMessageId(),
                    telephone.getNationCode(), telephone.getMobile(),  "NOTSENDED", "",
                    noticeTemplate.getScenario(), request.getLanguage() + " " + request.getBusinessType(),
                    senderDTO.getSpInfo().getId(), request.getReqOrderId(), request.getUserId());

            CompletableFuture.runAsync( () -> {
                String rc = MaskUtil.maskValidateCode(TextFormat.shortDebugString(request));
                try {
                    messageService.cacheSendedSp(senderDTO.getMsgType(), senderDTO.getScenario(), telephone.getMobile(),
                            request.getBusinessType(), senderDTO.getSpInfo().getId());
                    redisTemplate.opsForHash().increment(PROCESSING_HASH_KEY, senderDTO.getSpInfo().getId().toString(), 1L);
                    smsSender.send(request.getOrgId(), senderDTO, request);
                } catch (Exception e) {
                    if (!e.getMessage().contains("time") && !e.getMessage().contains("out")) {
                        log.error("error", e);
                    }
                    if (senderDTO.getMsgType() == MsgTypeEnum.VERIFY_CODE) {
                        log.error("send sms error param:{} msg:{}", rc, e.getMessage());
                        return;
                    } else {
                        log.warn("send sms error param:{} msg:{}", rc, e.getMessage());
                    }
                    try {
                        smsSender.send(request.getOrgId(), senderDTO, request);
                        log.warn("retry send sms {} channel:{} msg:{}", rc, senderDTO.getSpInfo().getChannel());
                    } catch (IOException e2) {
                        //retry fail
                        log.error("retry send sms failed:{} channel:{} msg:{}", rc, senderDTO.getSpInfo().getChannel(), e.getMessage());
                    }
                } finally {
                    redisTemplate.opsForHash().increment(PROCESSING_HASH_KEY, senderDTO.getSpInfo().getId().toString(), -1L);
                }
            }, newTaskExecutor(senderDTO.getSpInfo(), senderDTO.getSpInfo().getChannel(), NoticeTypeEnum.SMS));

            observer.onNext(MessageReply.newBuilder().setSuccess(true).build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("ALERT req:{} msg:{}", MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)), e.getMessage(), e);
            observer.onNext(MessageReply.newBuilder().setSuccess(false)
                    .setMessage(e.getMessage()).build());
            observer.onCompleted();
            return;
        }
    }



    private boolean validateTelephone(long orgId, Telephone telephone) {
        String nationCode = telephone.getNationCode();
        String mobile = telephone.getMobile();
        if (!StringUtils.isNumeric(nationCode) || !StringUtils.isNumeric(mobile)){
            return false;
        }
        if (nationCode.equals("86") && (mobile.length() != 11 || !mobile.startsWith("1"))) {
            return false;
        }
        Phonenumber.PhoneNumber phoneNumber = new Phonenumber.PhoneNumber()
                .setCountryCode(Integer.parseInt(nationCode))
                .setNationalNumber(Long.parseLong(mobile.trim()));
        boolean valid = phoneUtil.isValidNumber(phoneNumber);
        if (!valid) {
            log.warn("{} {} not valid.", nationCode, mobile);
            SmsDeliveryRecordEntity recordEntity = smsDeliveryRecordService.getOneRecordByMobile(orgId, mobile);
            if (recordEntity != null && !recordEntity.getNationCode().equals(nationCode)) {
                log.warn("{} {} nationCode not valid.", nationCode, mobile);
                return false;
            }
        }
        return true;
    }


    @Override
    public void createSmsSign(CreateSmsSignRequest request, StreamObserver<MessageReply> observer) {
        try {
            brokerInfoService.createSmsSign(request);
            observer.onNext(MessageReply.newBuilder().setSuccess(true).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onNext(MessageReply.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
            observer.onCompleted();
        }

    }

    @Override
    public void listSmsSigns(ListSmsSignsRequest request, StreamObserver<ListSmsSignsReply> observer) {
        List<SmsSign> list = brokerInfoService.list(request.getOrgId());
        observer.onNext(ListSmsSignsReply.newBuilder().addAllSmsSign(list).build());
        observer.onCompleted();
    }


    @Override
    public void editEmailTemplate(EditEmailTemplateRequest request, StreamObserver<MessageReply> observer) {
        brokerInfoService.editEmailTemplate(request);
        observer.onNext(MessageReply.newBuilder().build());
        observer.onCompleted();
    }

    @Override
    public void queryEmailTemplates(QueryEmailTemplatesRequest request, StreamObserver<QueryEmailTemplateReply> observer) {
        List<io.bhex.base.common.EmailTemplate> list = brokerInfoService.queryTitleImages(request.getOrgId());
        observer.onNext(QueryEmailTemplateReply.newBuilder().addAllEmailTemplate(list).build());
        observer.onCompleted();
    }



    @Override
    public void listDeliveryRecords(ListDeliveryRecordsRequest request, StreamObserver<ListDeliveryRecordsReply> observer) {
        String reciver = request.getReceiver().toLowerCase();
        List<DeliveryRecord> records;
        if (reciver.contains("@")) {
            records = emailDeliveryRecordService.getRecords(request.getOrgId(), reciver);
        } else {
            records = smsDeliveryRecordService.getRecords(request.getOrgId(), reciver);
        }
        observer.onNext(ListDeliveryRecordsReply.newBuilder().addAllRecord(records).build());
        observer.onCompleted();
    }


    @Override
    public void receiverInBlackList(ReceiverInBlackListRequest request, StreamObserver<ReceiverInBlackListReply> observer) {
        boolean inBlackList = false;
        String receiver = request.getReceiver();
        if (receiver.contains("@")) {
            inBlackList = emailMxInfoService.mxIpInBlackList(request.getReceiver());
        }
        observer.onNext(ReceiverInBlackListReply.newBuilder().setInBlackList(inBlackList).build());
        observer.onCompleted();
    }


    @Override
    public void editAntiPhishingCode(EditAntiPhishingCodeRequest request, StreamObserver<MessageReply> observer) {
        MessageReply reply = antiPhishingCodeService.editAntiPhishingCode(request.getOrgId(), request.getUserId(),
                request.getAntiPhishingCode());
        observer.onNext(reply);
        observer.onCompleted();
    }

    @Override
    public void codeFeedback(CodeFeedbackRequest request, StreamObserver<MessageReply> observer) {

        if (request.getType() == CodeFeedbackRequest.MessageType.EMAIL) {
            //newTaskExecutor(senderDTO.getSpInfo(), senderDTO.getSpInfo().getChannel(), NoticeTypeEnum.EMAIL);
            emailDeliveryRecordService.updateCodeFeedbackStatus(request.getReqOrderId(),
                    request.getValidResult(), request.getValidTime());
        } else {
            smsDeliveryRecordService.updateCodeFeedbackStatus(request.getReqOrderId(),
                    request.getValidResult(), request.getValidTime());
        }
        observer.onNext(MessageReply.newBuilder().setSuccess(true).build());
        observer.onCompleted();
    }

    @Override
    public void editAppCertInfo(EditAppCertInfoRequest request, StreamObserver<MessageReply> observer) {
        appPushService.editAppCertInfo(request);
        observer.onNext(MessageReply.newBuilder().setSuccess(true).build());
        observer.onCompleted();
    }

    @Override
    public void getAppCertInfos(GetAppCertInfosRequest request, StreamObserver<GetAppCertInfosReply> observer) {
        GetAppCertInfosReply reply = appPushService.getAppSpInfos(request.getOrgId());
        observer.onNext(reply);
        observer.onCompleted();
    }

    @Override
    public void editPushSwitch(EditPushSwitchRequest request, StreamObserver<MessageReply> observer) {
        appPushService.openSwitch(request.getOrgId(), request.getSwitchType().name(), request.getOpen());
        observer.onNext(MessageReply.newBuilder().setSuccess(true).build());
        observer.onCompleted();
    }

    @Override
    public void getPushSwitches(GetPushSwitchesRequest request, StreamObserver<GetPushSwitchesReply> observer) {
        observer.onNext(appPushService.getPushSwitches(request.getOrgId()));
        observer.onCompleted();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.ctx = applicationContext;
    }


    private Object lock = new Object();
    private TaskExecutor newTaskExecutor(SpInfo spInfo, String channel, NoticeTypeEnum type) {
        long spId = spInfo.getId();
        TaskExecutor cachedExecutor = taskExecutorMap.get(spId);
        if (cachedExecutor != null) {
            return cachedExecutor;
        }
        synchronized (lock) {
            if (taskExecutorMap.get(spId) != null) {
                return taskExecutorMap.get(spId);
            }
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.initialize();
            executor.setCorePoolSize(5);
            executor.setQueueCapacity(512);
            executor.setMaxPoolSize(20);
            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
            executor.setThreadNamePrefix(channel + "-" + type.name() + "-" + spId + "-Thread-");
            executor.setAwaitTerminationSeconds(8);
            executor.setWaitForTasksToCompleteOnShutdown(true);
            taskExecutorMap.put(spId, executor);
            log.info("newTaskExecutor id:{} type:{}", spId, type.name());
        }
        return taskExecutorMap.get(spId);
    }
}
