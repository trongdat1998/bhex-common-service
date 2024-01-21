package io.bhex.base.common.test;

import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.util.AESUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SecretTest {

    private static final String BASE_SQL = "INSERT INTO `common_server`.`tb_sp_info`(`id`, `org_id`, `channel`, `notice_type`, `msg_type`, `position`, `weight`, `access_key_id`, `secret_key`, `config_info`, `request_url`, `env`, `support_whole`, `app_id`, `status`, `can_sync_sms_tmpl`, `created`, `updated`) VALUES ";

    private static String decryptSecretKey(SpInfo spInfo) {
        byte[] decryptFrom = AESUtil.parseHexStr2Byte(spInfo.getSecretKey());
        byte[] secretKey = AESUtil.decrypt(decryptFrom, spInfo.getChannel() + spInfo.getAccessKeyId());
        return secretKey != null ? new String(secretKey) : "";
    }

    private static String encryptSecretKey(SpInfo spInfo) {
        byte[] secretKey = AESUtil.encrypt(spInfo.getSecretKey(), spInfo.getChannel() + spInfo.getAccessKeyId());
        assert secretKey != null;
        return AESUtil.parseByte2HexStr(secretKey);
    }

    private static SpInfo getSpInfo(String channel, String noticeType, String accessKey, String secretKey) {
        SpInfo spInfo = new SpInfo();
        spInfo.setChannel(channel);
        spInfo.setNoticeType(noticeType);
        spInfo.setAccessKeyId(accessKey);
        spInfo.setSecretKey(secretKey);
        spInfo.setSecretKey(encryptSecretKey(spInfo));
        System.out.println("EncryptSecretKey: " + spInfo.getSecretKey());
        System.out.println("SecretKey: " + decryptSecretKey(spInfo));
        return spInfo;
    }

    private static void printAwsEmailSql(String accessKey, String secretKey, String fromAddress, String regionName) {
        SpInfo spInfo = getSpInfo("AWS", "EMAIL", accessKey, secretKey);
        String created = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").format(LocalDateTime.now());
        String updated = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
        String awsEmailFormat = "(%s, %s, '%s', '%s', 'ALL', 60, 1, '%s', '%s', '{\"from_address\":\"%s\",\"region_name\":\"%s\"}', '', 'BHOP', 1, NULL, 1, 0, '%s', '%s');";
        String awsEmailSql = BASE_SQL + String.format(awsEmailFormat, 5, 9001, spInfo.getChannel(), spInfo.getNoticeType(), spInfo.getAccessKeyId(), spInfo.getSecretKey(),
                fromAddress, regionName, created, updated);
        String awsSaasEmailSql = BASE_SQL + String.format(awsEmailFormat, 6, 0, spInfo.getChannel(), spInfo.getNoticeType(), spInfo.getAccessKeyId(), spInfo.getSecretKey(),
                fromAddress, regionName, created, updated);
        System.out.println("Aws Email Sql: " + awsEmailSql);
        System.out.println("Aws Email saas Sql: " + awsSaasEmailSql);
    }

    private static void printMailGunEmailSql(String secretKey, String from, String domain) {
        SpInfo spInfo = getSpInfo("MAILGUN", "EMAIL", "mailgun", secretKey);
        String created = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").format(LocalDateTime.now());
        String updated = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
        String awsEmailFormat = "(%s, %s, '%s', '%s', 'ALL', 60, 1, '%s', '%s', '{\"from\":\"%s\",\"domain\":\"%s\"}', '', 'BHOP', 1, NULL, 1, 0, '%s', '%s');";
        String awsEmailSql = BASE_SQL + String.format(awsEmailFormat, 7, 9001, spInfo.getChannel(), spInfo.getNoticeType(), spInfo.getAccessKeyId(), spInfo.getSecretKey(),
                from, domain, created, updated);
        String awsSaasEmailSql = BASE_SQL + String.format(awsEmailFormat, 8, 0, spInfo.getChannel(), spInfo.getNoticeType(), spInfo.getAccessKeyId(), spInfo.getSecretKey(),
                from, domain, created, updated);
        System.out.println("Mailgun Email Sql: " + awsEmailSql);
        System.out.println("Mailgun Email saas Sql: " + awsSaasEmailSql);
    }

    private static void printTwilioEmailSql(String secretKey, String from) {
        SpInfo spInfo = getSpInfo("TWILIO", "EMAIL", "sendgrid", secretKey);
        String twilioEmailFormat = "(%s, %s, '%s', '%s', 'ALL', 60, 1, '%s', '%s', '{\"from\":\"%s\"}', '', 'BHOP', 1, NULL, 1, 0, '%s', '%s');";
        String created = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss.SSS").format(LocalDateTime.now());
        String updated = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss").format(LocalDateTime.now());
        String twilioEmailSql = BASE_SQL + String.format(twilioEmailFormat, 9, 9001, spInfo.getChannel(), spInfo.getNoticeType(), spInfo.getAccessKeyId(), spInfo.getSecretKey(),
                from, created, updated);
        String twilioSaasEmailSql = BASE_SQL + String.format(twilioEmailFormat, 10, 0, spInfo.getChannel(), spInfo.getNoticeType(), spInfo.getAccessKeyId(), spInfo.getSecretKey(),
                from, created, updated);
        System.out.println("Twilio Email Sql: " + twilioEmailSql);
        System.out.println("Twilio Email saas Sql: " + twilioSaasEmailSql);
    }

    private static void printTwilioSmsSql(String accessKey, String secretKey, String messagingServiceId) {
        SpInfo spInfo = getSpInfo("TWILIO", "SMS", accessKey, secretKey);
        String statusCallback = "https://www.bit-e.com/notice/callback/sendgrid/smscallback";
        String twilioSmsFormat = "(%s, %s, '%s', '%s', 'ALL', 60, 1, '%s', '%s', '{\"StatusCallback\":\"%s\",\"MessagingServiceSid\":\"%s\"}', '', 'BHOP', 1, NULL, 1, 0, '%s', '%s');";
        String created = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss.SSS").format(LocalDateTime.now());
        String updated = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss").format(LocalDateTime.now());
        String twilioSmsSql = BASE_SQL + String.format(twilioSmsFormat, 501, 9001, spInfo.getChannel(), spInfo.getNoticeType(), spInfo.getAccessKeyId(), spInfo.getSecretKey(),
                statusCallback, messagingServiceId, created, updated);
        String twilioSaasSmsSql = BASE_SQL + String.format(twilioSmsFormat, 502, 0, spInfo.getChannel(), spInfo.getNoticeType(), spInfo.getAccessKeyId(), spInfo.getSecretKey(),
                statusCallback, messagingServiceId, created, updated);
        System.out.println("Twilio Sms Sql: " + twilioSmsSql);
    }

    public static void main(String[] args) throws Exception {
        //用于短信邮箱配置的加解密测试。对应的表为common_server.tb_sp_info
        SpInfo spInfo = new SpInfo();
        spInfo.setChannel("TWILIO");
        spInfo.setNoticeType("SMS");
        spInfo.setAccessKeyId("");
        spInfo.setSecretKey("SG.xxx.xxx");
        switch (spInfo.getChannel()) {
            case "AWS":
                if (spInfo.getNoticeType().equals("EMAIL")) {
                    String fromAddress = "noreply@xxx";
                    String regionName = "us-east-1";
                    printAwsEmailSql(spInfo.getAccessKeyId(), spInfo.getSecretKey(), fromAddress, regionName);
                }
                break;
            case "TWILIO":
                if (spInfo.getNoticeType().equals("SMS")) {
                    String messageServiceId = "xxx";
                    printTwilioSmsSql(spInfo.getAccessKeyId(), spInfo.getSecretKey(), messageServiceId);
                } else if (spInfo.getNoticeType().equals("EMAIL")) {
                    //即sendgrid
                    String from = "noreply@xxx";
                    printTwilioEmailSql(spInfo.getSecretKey(), from);
                }
                break;
            case "MAILGUN":
                if (!spInfo.getNoticeType().equals("EMAIL")) {
                    throw new Exception("Not Email!");
                }
                String from = "no-reply@xxx.xxx";
                String domain = "xxx.xxx";
                printMailGunEmailSql(spInfo.getSecretKey(), from, domain);
                break;
            default:
                System.out.println("No channel!");
        }
    }
}
