//package io.bhex.base.common.controller;
//
//import io.bhex.base.common.SimpleMailRequest;
//import io.bhex.base.common.SimpleSMSRequest;
//import io.bhex.base.common.Telephone;
//import io.bhex.base.common.sender.v2.SendCloudEmailSenderV2;
//import io.bhex.base.common.sender.v2.ZhixinSmsSender;
//import io.bhex.base.common.service.impl.SimpleMessageService;
//import io.bhex.base.common.util.MD5Util;
//import io.bhex.base.common.util.MaskUtil;
//import io.bhex.base.common.util.SenderDTO;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import javax.servlet.http.HttpServletRequest;
//import java.io.IOException;
//import java.util.Arrays;
//import java.util.concurrent.CompletableFuture;
//
//@Slf4j
//@RestController
//public class SenderRestController {
//
//    @Autowired
//    private SimpleMessageService messageService;
//    @Autowired
//    private ZhixinSmsSender zhixinSmsSender;
//    @Autowired
//    private SendCloudEmailSenderV2 sendCloudSender;
//
//    private String secret = "xcEDG%^#";
//
//    @RequestMapping(value = "/zhixin/sms_call")
//    public String smsCall(HttpServletRequest httpServletRequest){
//        long t = Long.parseLong(httpServletRequest.getParameter("t"));
//        if (Math.abs(System.currentTimeMillis() - t) > 60_000) {
//            return "error t";
//        }
//        String reqSign = httpServletRequest.getParameter("sign");
//        String sign = MD5Util.getMD5(httpServletRequest.getParameter("t") + secret);
//        log.info("sign:{}", sign);
//        if (!reqSign.equals(sign)) {
//            return "sign error";
//        }
//        SimpleSMSRequest request = SimpleSMSRequest.newBuilder()
//                .setTelephone(Telephone.newBuilder().setMobile(httpServletRequest.getParameter("mobile")).setNationCode(httpServletRequest.getParameter("nationCode")).build())
//                .setOrgId(Long.parseLong(httpServletRequest.getParameter("orgId")))
//                .addAllParams(Arrays.asList(new String[]{httpServletRequest.getParameter("content")}))
//                .setBusinessType("GLOBAL")
//                .setLanguage("zh_CN")
//                .build();
//
//        SenderDTO senderDTO = messageService.getSmsSendInfo(request);
//        log.info("sender org:{} type:{} language:{} tmplid:{} sp:{}", request.getOrgId(),
//                request.getBusinessType(), request.getLanguage(),
//                senderDTO.getTargetTmplId(), senderDTO.getSpInfo().getId());
//        if (!request.getSign().equals("")) {
//            senderDTO.setSignName(request.getSign());
//        }
//        CompletableFuture.runAsync(() ->
//                {
//                    String rc = MaskUtil.maskValidateCode(request.toString());
//                    try {
//                        zhixinSmsSender.send(request.getOrgId(), senderDTO, request);
//                    } catch (IOException e) {
//                        log.warn("send error");
//                    }
//                }
//        );
//        return "ok";
//    }
//
//    @RequestMapping(value = "/sc/email_call")
//    public String emailSend(HttpServletRequest request){
//        long t = Long.parseLong(request.getParameter("t"));
//        if (Math.abs(System.currentTimeMillis() - t) > 60_000) {
//            return "error t";
//        }
//        String reqSign = request.getParameter("sign");
//        String sign = MD5Util.getMD5(request.getParameter("t") + secret);
//        log.info("sign:{}", sign);
//        if (!reqSign.equals(sign)) {
//            return "sign error";
//        }
//        SimpleMailRequest mailRequest = SimpleMailRequest.newBuilder()
//                .setMail(request.getParameter("email"))
//                .setEmailSubject(request.getParameter("subject"))
//                .setOrgId(401L)
//                .addAllParams(Arrays.asList(new String[]{request.getParameter("content")}))
//                .setBusinessType("GLOBAL")
//                .setLanguage("zh_CN")
//                .build();
//
//        SenderDTO senderDTO = messageService.getEmailSendInfo(mailRequest);
//        senderDTO.setEmailSubject(request.getParameter("subject"));
//        senderDTO.setSignName(request.getParameter("subject"));
//        CompletableFuture.runAsync(() ->
//                {
//                    String rc = MaskUtil.maskValidateCode(request.toString());
//                    try {
//                        sendCloudSender.send(mailRequest.getOrgId(), senderDTO, mailRequest);
//                    } catch (IOException e) {
//                        log.warn("send error");
//                    }
//                }
//        );
//        return "ok";
//    }
//}
