package io.bhex.base.common.sender.v2;

import io.bhex.base.common.SimpleSMSRequest;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.service.impl.SimpleMessageService;
import io.bhex.base.common.util.*;
import io.bhex.base.env.BhexEnv;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ZhuTongIntlSender implements ISmsSender {
    public static Map<String, BigDecimal> PRICE_MAP = new HashMap<>();
    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;

    private String defaultBaseUrl = "http://intl.zthysms.com/";

    private String vpnBaseUrl = "https://zhutongintlsms.bhpc.cloud/";

    private String getSenderBaseUrl() {
        if (ChannelConstant.VPN_URL_VALID) {
            return vpnBaseUrl;
        }
        return defaultBaseUrl;
    }

    @Override
    public boolean send(Long orgId, SenderDTO senderDTO, SimpleSMSRequest request) throws IOException {
        SpInfo sp = senderDTO.getSpInfo();
        String mobile = request.getTelephone().getMobile();

        FormBody.Builder requestBuilder = new FormBody.Builder();
        requestBuilder.add("username", sp.getAccessKeyId());
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        String tKey = dateFormat.format(new Date(System.currentTimeMillis() + 8*3600*1000));
        requestBuilder.add("tkey", tKey);
        requestBuilder.add("password", MD5Util.getMD5(MD5Util.getMD5(sp.getSecretKey()) + tKey));
        requestBuilder.add("code", request.getTelephone().getNationCode());
        requestBuilder.add("mobile", mobile.startsWith("0") ? mobile.substring(1) : mobile);
        String content = "[" + senderDTO.getOriginSignName() + "] " + senderDTO.getSendContent(request.getParamsList(), request.getReqParamMap());
        requestBuilder.add("content", content);


        String responseTxt = "";

        Request smsRequest = new
                Request.Builder()
                .url(getSenderBaseUrl() + "intSendSms.do")
                .post(requestBuilder.build()).build();

        Response response = okHttpClient.newCall(smsRequest).execute();
        if (response.isSuccessful()) {
            String text = response.body().string();
            if (text != null) {
                responseTxt = text;
            }
        }

        String maskPhone = MaskUtil.maskMobile(mobile);
        log.info("sp:{} orgId:{} phone:{} content:{} result={}",
                sp.getId(), orgId, maskPhone,
                MaskUtil.maskValidateCode(content),
                responseTxt);

        String[] arr = responseTxt.split(",");
        if (arr[0].equals("1")) {
            String sid = arr[1];
            smsDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), sid, "request-200", "request success");
        } else {
            log.error("send sms failed mobile:{} res:{}", maskPhone, responseTxt);
            smsDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), senderDTO.getTempMessageId(), "request-failed", arr[1]);
        }

        PrometheusUtil.smsSendCounter(SendChannelEnum.ZHUTONG_INTL.name(), arr[0].equals("1") ? 200 : 400);

        return false;
    }



    @Scheduled(initialDelay = 5_000, fixedRate = 60_000)
    public void loadZhutongReport() {
        if (!new BhexEnv().isBHEXB()) {
            return;
        }
        boolean lock = RedisLockUtils.tryLock(redisTemplate, "loadZhutongReport", 30_000);
        if (!lock) {
            log.info("loadZhutongReport not get lock");
            return;
        }

        List<SpInfo> zhutongIntlSps = SimpleMessageService.spInfos.stream()
                .filter(s -> s.getNoticeType().equals(NoticeTypeEnum.SMS.name()))
                .filter(s -> s.getChannel().equals(SendChannelEnum.ZHUTONG_INTL.name()))
                .collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(zhutongIntlSps)) {
            Map<String, List<SpInfo>> group = zhutongIntlSps.stream().collect(Collectors.groupingBy(SpInfo::getAccessKeyId));
            for (String username : group.keySet()) {
                try {
                    FormBody.Builder requestBuilder = new FormBody.Builder();
                    requestBuilder.add("username", username);
                    DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
                    String tKey = dateFormat.format(new Date(System.currentTimeMillis() + 8*3600*1000));
                    requestBuilder.add("tkey", tKey);
                    requestBuilder.add("password", MD5Util.getMD5(MD5Util.getMD5(group.get(username).get(0).getSecretKey()) + tKey));

                    Request smsRequest = new
                            Request.Builder()
                            .url(getSenderBaseUrl() + "intReport.do")
                            .post(requestBuilder.build()).build();

                    Response response = okHttpClient.newCall(smsRequest).execute();
                    if (response.isSuccessful()) {
                        String text = response.body().string();
                        log.info("username:{} text:{}", username, text);
                        if (text.contains("27,no data")) {
                            return;
                        }
                        String[] lines = text.split(";");
                        for (String line : lines) {
                            String[] l = line.split(",");
                            String msgId = l[0];
                            String status = l[3];

                            long deliveriedAt = DateTime.parse(l[4],
                                    DateTimeFormat.forPattern("yyyyMMddHHmmss")).toDate().getTime() - 8*3600*1000;
                            smsDeliveryRecordService.updateDeliveryStatus(msgId, status.equals("1") ? "SUCCESS" : status,
                                    deliveriedAt, status);
                        }
                    }
                } catch (Exception e) {
                    log.info("error load balance", e);
                }
            }
        }
    }

    @PostConstruct
    private void loadPrice() {
        Arrays.stream(ZHUTONGINTL_PRICES.split("\n")).forEach(s -> {
            String[] arr = s.split("\t");
            PRICE_MAP.put(arr[0], new BigDecimal(arr[1]));
        });
        log.info("{}", PRICE_MAP);
    }


    private static String ZHUTONGINTL_PRICES = "1\t0.0927\n" +
            "1242\t0.1934\n" +
            "1246\t0.1576\n" +
            "1264\t0.2199\n" +
            "1268\t0.1841\n" +
            "1284\t0.6797\n" +
            "1345\t0.1762\n" +
            "1441\t0.2106\n" +
            "1473\t0.1934\n" +
            "1649\t0.1762\n" +
            "1664\t0.0874\n" +
            "1671\t0.6797\n" +
            "1684\t0.5445\n" +
            "1758\t0.3074\n" +
            "1767\t0.2106\n" +
            "1784\t0.3166\n" +
            "1787\t0.2901\n" +
            "1809\t0.3259\n" +
            "1868\t0.2636\n" +
            "1869\t0.1139\n" +
            "1876\t0.2106\n" +
            "20\t0.2027\n" +
            "211\t0.0874\n" +
            "212\t0.2809\n" +
            "213\t0.1232\n" +
            "216\t0.3961\n" +
            "218\t0.106\n" +
            "220\t0.0874\n" +
            "221\t0.3166\n" +
            "222\t0.2292\n" +
            "223\t0.1762\n" +
            "224\t0.2027\n" +
            "225\t0.1576\n" +
            "226\t0.1576\n" +
            "227\t0.1762\n" +
            "228\t0.0874\n" +
            "229\t0.1841\n" +
            "230\t0.2106\n" +
            "231\t0.0874\n" +
            "232\t0.0874\n" +
            "233\t0.0874\n" +
            "234\t0.1232\n" +
            "235\t0.1404\n" +
            "236\t0.4399\n" +
            "237\t0.0967\n" +
            "238\t0.1325\n" +
            "239\t0.0795\n" +
            "240\t0.053\n" +
            "241\t0.1325\n" +
            "242\t0.1762\n" +
            "243\t0.1762\n" +
            "244\t0.3074\n" +
            "245\t0.1669\n" +
            "248\t0.3511\n" +
            "249\t0.1232\n" +
            "250\t0.0967\n" +
            "251\t0.1576\n" +
            "252\t0.1404\n" +
            "253\t0.5273\n" +
            "254\t0.1404\n" +
            "255\t0.2027\n" +
            "256\t0.0874\n" +
            "257\t0.1139\n" +
            "258\t0.1762\n" +
            "260\t0.0795\n" +
            "261\t0.1934\n" +
            "262\t0.1497\n" +
            "263\t0.106\n" +
            "264\t0.1762\n" +
            "265\t0.3696\n" +
            "266\t0.106\n" +
            "267\t0.0874\n" +
            "268\t0.106\n" +
            "269\t0.0609\n" +
            "269\t0.0609\n" +
            "27\t0.0795\n" +
            "291\t0.1325\n" +
            "297\t1.0547\n" +
            "298\t0.0609\n" +
            "299\t0.1762\n" +
            "30\t0.4399\n" +
            "31\t0.6333\n" +
            "32\t0.73\n" +
            "33\t0.2994\n" +
            "34\t0.2199\n" +
            "350\t0.1669\n" +
            "351\t0.2464\n" +
            "352\t0.4306\n" +
            "353\t0.3604\n" +
            "354\t0.5273\n" +
            "355\t0.3776\n" +
            "356\t0.1325\n" +
            "357\t0.1841\n" +
            "358\t0.3604\n" +
            "359\t0.3339\n" +
            "36\t0.3961\n" +
            "370\t0.1576\n" +
            "371\t0.2106\n" +
            "372\t0.1139\n" +
            "373\t0.1762\n" +
            "374\t0.2636\n" +
            "375\t0.1841\n" +
            "376\t0.2636\n" +
            "377\t0.1325\n" +
            "378\t0.4399\n" +
            "380\t0.2636\n" +
            "381\t0.2729\n" +
            "382\t0.1576\n" +
            "385\t0.2106\n" +
            "386\t0.2464\n" +
            "387\t0.2544\n" +
            "389\t0.1325\n" +
            "39\t0.2901\n" +
            "40\t0.3166\n" +
            "41\t0.2544\n" +
            "420\t0.3431\n" +
            "421\t0.3869\n" +
            "423\t0.2199\n" +
            "43\t0.4836\n" +
            "44\t0.1841\n" +
            "45\t0.2199\n" +
            "46\t0.2636\n" +
            "47\t0.2636\n" +
            "48\t0.2636\n" +
            "49\t0.6148\n" +
            "500\t0.2371\n" +
            "501\t0.1139\n" +
            "502\t0.2729\n" +
            "503\t0.3259\n" +
            "504\t0.2636\n" +
            "505\t0.1404\n" +
            "506\t0.2371\n" +
            "507\t0.4664\n" +
            "508\t0.6797\n" +
            "509\t0.4664\n" +
            "51\t0.1232\n" +
            "52\t0.2371\n" +
            "53\t0.2636\n" +
            "54\t0.1762\n" +
            "55\t0.4041\n" +
            "56\t0.1139\n" +
            "57\t0.4399\n" +
            "58\t0.1232\n" +
            "590\t0.6797\n" +
            "591\t0.1325\n" +
            "592\t0.2027\n" +
            "593\t0.4399\n" +
            "594\t0.6797\n" +
            "595\t0.2464\n" +
            "597\t0.106\n" +
            "598\t0.3604\n" +
            "599\t0.6797\n" +
            "599\t0.6797\n" +
            "599\t0.6797\n" +
            "60\t0.2385\n" +
            "61\t0.2106\n" +
            "62\t0.1722\n" +
            "63\t0.0702\n" +
            "64\t0.3074\n" +
            "65\t0.2835\n" +
            "66\t0.0967\n" +
            "670\t0.0874\n" +
            "673\t0.106\n" +
            "674\t0.3829\n" +
            "675\t0.106\n" +
            "676\t0.1325\n" +
            "677\t0.1576\n" +
            "678\t0.1762\n" +
            "679\t0.2106\n" +
            "680\t0.106\n" +
            "682\t0.2133\n" +
            "685\t0.0609\n" +
            "686\t0.106\n" +
            "687\t0.3604\n" +
            "689\t0.3511\n" +
            "691\t0.0967\n" +
            "692\t0.6797\n" +
            "7\t0.1841\n" +
            "7\t0.2809\n" +
            "81\t0.2636\n" +
            "82\t0.0914\n" +
            "84\t0.2385\n" +
            "850\t0.6797\n" +
            "852\t0.359\n" +
            "853\t0.265\n" +
            "855\t0.742\n" +
            "856\t0.3272\n" +
            "880\t0.4399\n" +
            "886\t0.2027\n" +
            "90\t0.4399\n" +
            "91\t0.0437\n" +
            "92\t0.106\n" +
            "93\t0.3604\n" +
            "94\t0.1762\n" +
            "95\t0.6943\n" +
            "960\t0.1762\n" +
            "961\t0.1762\n" +
            "962\t0.1325\n" +
            "963\t0.1762\n" +
            "964\t0.2464\n" +
            "965\t0.3074\n" +
            "966\t0.106\n" +
            "967\t0.1325\n" +
            "968\t0.1841\n" +
            "971\t0.2199\n" +
            "972\t0.1576\n" +
            "973\t0.2106\n" +
            "974\t0.2464\n" +
            "975\t0.0702\n" +
            "976\t0.3511\n" +
            "977\t0.5273\n" +
            "98\t0.2106\n" +
            "992\t0.1576\n" +
            "993\t0.1139\n" +
            "994\t0.0967\n" +
            "995\t0.0874\n" +
            "996\t0.3339\n" +
            "998\t0.2199";


}
