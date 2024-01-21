///*
// ************************************
// * @项目名称: bhex-common-service
// * @文件名称: EmailSender
// * @Date 2018/08/14
// * @Author will.zhao@bhex.io
// * @Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
// * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
// **************************************
// */
//package io.bhex.base.common.sender;
//
//import com.sun.mail.util.MailSSLSocketFactory;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.mail.javamail.JavaMailSenderImpl;
//import org.springframework.mail.javamail.MimeMessageHelper;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.PostConstruct;
//import javax.mail.internet.MimeMessage;
//
//import java.util.Properties;
//
//@Slf4j
//@Component
//@Deprecated
//public class EmailSender {
//
//    private static JavaMailSenderImpl mailSender;
//
//    /**
//     * --------------------邮件配置信息start-------------------
//     **/
//
//    @Value("${mail.server.host}")
//    private String mailServerHost;
//
//    @Value("${mail.server.port}")
//    private int mailServerPort;
//
//    @Value("${mail.server.timeout}")
//    private String timeout;
//
//    @Value("${mail.username}")
//    private String mailUsername;
//
//    @Value("${mail.password}")
//    private String mailPassword;
//
//    @Value("${mail.fromaddr}")
//    private String mailFromAddr;
//
//    /**
//     * --------------------邮件配置信息end--------------------
//     **/
//
//    @PostConstruct
//    public void initMailSender() {
//        mailSender = new JavaMailSenderImpl();
//
//        mailSender.setHost(mailServerHost);
//        mailSender.setPort(mailServerPort);
//        mailSender.setUsername(mailUsername);
//        mailSender.setPassword(mailPassword);
//        mailSender.setProtocol("smtp");
//        mailSender.setDefaultEncoding("UTF-8");
//
//        Properties p = new Properties();
//        p.setProperty("mail.smtp.auth", "true");
//        p.setProperty("mail.smtp.starttls.enable", "true");
//        p.setProperty("smtp.starttls.required", "true");
//        p.setProperty("mail.smtp.timeout", timeout);
//        try {
//            MailSSLSocketFactory sf = new MailSSLSocketFactory();
//            sf.setTrustAllHosts(true);
//            p.setProperty("mail.smtp.ssl.enable", "true");
//            p.put("mail.smtp.ssl.socketFactory", sf);
//        } catch (Exception e) {
//            log.error("no ssl socketFactory", e);
//        }
//        mailSender.setJavaMailProperties(p);
//    }
//
//    public boolean send(Long orgId, String to, String nickname, String sign, String subject, String content, boolean isHtml) {
//        MimeMessage message = mailSender.createMimeMessage();
//        try {
//            MimeMessageHelper helper = new MimeMessageHelper(message, true);
//            helper.setFrom(mailFromAddr, sign);
//            if (StringUtils.isBlank(nickname)) {
//                helper.setTo(to);
//            } else {
//                helper.addTo(to, nickname);
//            }
//            helper.setSubject(subject);
//            helper.setText(content, isHtml);
//            mailSender.send(message);
//            log.info("succ send simple mail orgId:{} to:{}", orgId, to);
//            return true;
//        } catch (Exception e) {
//            log.warn("fail send simple mail {}", to, e);
//            return false;
//        }
//    }
//}
