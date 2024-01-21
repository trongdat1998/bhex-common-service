///*
// ************************************
// * @项目名称: bhex-common-service
// * @文件名称: MailServiceGrpcImpl
// * @Date 2018/08/14
// * @Author will.zhao@bhex.io
// * @Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
// * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
// **************************************
// */
//package io.bhex.base.common.grpc;
//
//import io.bhex.base.common.*;
//import io.bhex.base.common.service.MailService;
//import io.bhex.base.common.service.RouterConfigService;
//import io.bhex.base.common.service.impl.LimiterService;
//import io.bhex.base.common.util.ChannelConstant;
//import io.bhex.base.common.util.Combo2;
//import io.bhex.base.grpc.annotation.GrpcService;
//import io.grpc.stub.StreamObserver;
//import lombok.extern.slf4j.Slf4j;
//
//import javax.annotation.Resource;
//import java.util.Arrays;
//import java.util.concurrent.CompletableFuture;
//
//@GrpcService
//@Slf4j
//@Deprecated
//public class MailServiceGrpcImpl extends MailServiceGrpc.MailServiceImplBase {
//
//    @Resource
//    private MailService mailService;
//    @Resource
//    private RouterConfigService routerConfigService;
//    @Resource
//    private LimiterService limiterService;
//
//    public static final String EMAIL_PATTERN = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])";
//
//    @Override
//    public void send(MailRequest request, StreamObserver<MailReply> responseObserver) {
//        boolean validated = request.getMail().toLowerCase().matches(EMAIL_PATTERN);
//        if(!validated){
//            log.info("error email:{}", request);
//            responseObserver.onNext(MailReply.newBuilder().setSuccess(false).build());
//            responseObserver.onCompleted();
//            return;
//        }
//        Combo2<Boolean, String> combo2 = limiterService.overLimitEmail(request.getOrgId(), request.getMail(), request.getBizType(), request.getParamsList());
//        if (combo2.getV1()) {
//            responseObserver.onNext(MailReply.newBuilder()
//                    .setSuccess(false).setMessage("over limiter")
//                    .build());
//            responseObserver.onCompleted();
//            return;
//        }
//        log.error("ALERT Deprecated req:{}", request);
//        try {
//            CompletableFuture.runAsync(() -> mailService.send(request));
//            responseObserver.onNext(MailReply.newBuilder().setSuccess(true).build());
//            responseObserver.onCompleted();
//        } catch (Exception e) {
//            responseObserver.onError(e);
//        }
//    }
//
//    @Override
//    public void sendMail(SendMailRequest request, StreamObserver<MailReply> responseObserver) {
//        boolean validated = request.getMail().toLowerCase().matches(EMAIL_PATTERN);
//        if(!validated){
//            log.info("error email:{}", request);
//            responseObserver.onNext(MailReply.newBuilder().setSuccess(false).build());
//            responseObserver.onCompleted();
//            return;
//        }
//        Combo2<Boolean, String> combo2 = limiterService.overLimitEmail(request.getOrgId(), request.getMail(), request.getBizType(), Arrays.asList());
//        if (combo2.getV1()) {
//            responseObserver.onNext(MailReply.newBuilder()
//                    .setSuccess(false).setMessage("over limiter")
//                    .build());
//            responseObserver.onCompleted();
//            return;
//        }
//        log.error("ALERT Deprecated req:{}", request);
//        boolean forbidAws = routerConfigService.forbidChannel(request.getOrgId(), false, ChannelConstant.AWS);
//        boolean forbidSendCloud = routerConfigService.forbidChannel(request.getOrgId(), false, ChannelConstant.SEND_CLOUD);
//        if (forbidAws && forbidSendCloud) {
//            log.error("ALERT: there is not any channels for send email!!!");
//            responseObserver.onNext(MailReply.newBuilder().setSuccess(false).build());
//            responseObserver.onCompleted();
//            return;
//        }
//
//        try {
//            CompletableFuture.runAsync(() -> mailService.send(request));
//            responseObserver.onNext(MailReply.newBuilder().setSuccess(true).build());
//            responseObserver.onCompleted();
//        } catch (Exception e) {
//            responseObserver.onError(e);
//        }
//    }
//}
