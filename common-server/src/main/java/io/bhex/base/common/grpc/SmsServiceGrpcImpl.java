///*
// ************************************
// * @项目名称: bhex-common-service
// * @文件名称: SmsServiceGrpcImpl
// * @Date 2018/08/14
// * @Author will.zhao@bhex.io
// * @Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
// * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
// **************************************
// */
//package io.bhex.base.common.grpc;
//
//import io.bhex.base.common.*;
//import io.bhex.base.common.service.RouterConfigService;
//import io.bhex.base.common.service.SmsService;
//import io.bhex.base.common.service.impl.LimiterService;
//import io.bhex.base.common.util.ChannelConstant;
//import io.bhex.base.common.util.Combo2;
//import io.bhex.base.grpc.annotation.GrpcService;
//import io.grpc.stub.StreamObserver;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//
//import javax.annotation.Resource;
//import java.util.Arrays;
//import java.util.concurrent.CompletableFuture;
//
//@GrpcService
//@Slf4j
//@Deprecated
//public class SmsServiceGrpcImpl extends SmsServiceGrpc.SmsServiceImplBase {
//
//    @Resource
//    private SmsService smsService;
//    @Resource
//    private RouterConfigService routerConfigService;
//    @Resource
//    private LimiterService limiterService;
//
//    @Override
//    public void send(SmsRequest request, StreamObserver<SmsReply> responseObserver) {
//        Telephone telephone = request.getTelephone();
//        boolean validated = validateTelephone(telephone);
//        if(!validated){
//            log.info("error telephone:{}", request.getTelephone());
//            responseObserver.onNext(SmsReply.newBuilder()
//                    .setSuccess(false).setMessage("error phone number")
//                    .build());
//            responseObserver.onCompleted();
//            return;
//        }
//
//        Combo2<Boolean, String> combo2 = limiterService.overLimitMobile(request.getOrgId(), request.getTelephone().getMobile(), request.getBizType(), request.getParamsList());
//        if (combo2.getV1()) {
//            responseObserver.onNext(SmsReply.newBuilder()
//                    .setSuccess(false).setMessage("over limiter")
//                    .build());
//            responseObserver.onCompleted();
//            return;
//        }
//        log.error("ALERT Deprecated req:{}", request);
//        boolean forbidAws = routerConfigService.forbidChannel(request.getOrgId(), true, ChannelConstant.AWS);
//        boolean forbidSendCloud = routerConfigService.forbidChannel(request.getOrgId(), true, ChannelConstant.SEND_CLOUD);
//        boolean forbidTencent = routerConfigService.forbidChannel(request.getOrgId(), true, ChannelConstant.TENCENT);
//
//        if (forbidAws && forbidSendCloud && forbidTencent) {
//            log.error("ALERT: there is not any channels for send sms!!!");
//            responseObserver.onNext(SmsReply.newBuilder().setSuccess(false).build());
//            responseObserver.onCompleted();
//            return;
//        }
//
//        try {
//            CompletableFuture.runAsync(() -> smsService.send(request));
//            responseObserver.onNext(SmsReply.newBuilder().setSuccess(true).build());
//            responseObserver.onCompleted();
//        } catch (Exception e) {
//            responseObserver.onError(e);
//        }
//    }
//
//    @Override
//    public void sendSms(SendSmsRequest request, StreamObserver<SmsReply> responseObserver) {
//        Telephone telephone = request.getTelephone();
//        boolean validated = validateTelephone(telephone);
//        if(!validated){
//            log.info("error telephone:{}", request.getTelephone());
//            responseObserver.onNext(SmsReply.newBuilder().setSuccess(false).setMessage("error phone number").build());
//            responseObserver.onCompleted();
//            return;
//        }
//        Combo2<Boolean, String> combo2 = limiterService.overLimitMobile(request.getOrgId(), request.getTelephone().getMobile(), request.getBizType(), Arrays.asList());
//        if (combo2.getV1()) {
//            responseObserver.onNext(SmsReply.newBuilder()
//                    .setSuccess(false).setMessage("over limiter")
//                    .build());
//            responseObserver.onCompleted();
//            return;
//        }
//        log.error("ALERT Deprecated req:{}", request);
//        try {
//            CompletableFuture.runAsync(() -> smsService.sendSms(request));
//            responseObserver.onNext(SmsReply.newBuilder().setSuccess(true).build());
//            responseObserver.onCompleted();
//        } catch (Exception e) {
//            responseObserver.onError(e);
//        }
//    }
//
//    private boolean validateTelephone(Telephone telephone){
//        String nationCode = telephone.getNationCode();
//        String mobile = telephone.getMobile();
//        return StringUtils.isNumeric(nationCode) && StringUtils.isNumeric(mobile);
//    }
//}
