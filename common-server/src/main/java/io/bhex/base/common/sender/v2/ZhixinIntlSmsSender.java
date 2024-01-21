package io.bhex.base.common.sender.v2;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.protobuf.TextFormat;
import io.bhex.base.common.SimpleSMSRequest;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.service.SmsDeliveryRecordService;
import io.bhex.base.common.util.*;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ZhixinIntlSmsSender  implements ISmsSender {

    public static Map<String, BigDecimal> PRICE_MAP = new HashMap<>();

    @Autowired
    private SmsDeliveryRecordService smsDeliveryRecordService;
    private PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;

    private String defaultBaseUrl = "http://sms.cxiang.net/";

    private String vpnBaseUrl = "https://zhixinintlsms.bhpc.cloud/";

    private static boolean VPN_URL_VALID = false;
    private String getSenderBaseUrl() {
        if (VPN_URL_VALID) {
            return vpnBaseUrl;
        }
        return defaultBaseUrl;
    }

    @Resource(name = "detectOkHttpClient")
    private OkHttpClient detectOkHttpClient;

    //@Scheduled(cron = "0/30 * * * * ?")
    public void detectWebUrl() {
        try {
            Request smsRequest = new
                    Request.Builder()
                    .url("https://zhixinintlsms.bhpc.cloud/")
                    .get().build();
            @Cleanup
            Response response = detectOkHttpClient.newCall(smsRequest).execute();
            if (response.code() == 404) {
                VPN_URL_VALID = true;
            }
        } catch (Exception e) {
            VPN_URL_VALID = false;
        }
    }


    @Override
    public boolean send(Long orgId, SenderDTO senderDTO, SimpleSMSRequest request) throws IOException {

        String mobile = request.getTelephone().getMobile();
        mobile = mobile.startsWith("0") ? mobile.substring(1) : mobile;
        mobile = "+" + request.getTelephone().getNationCode() + mobile;

        SpInfo sp = senderDTO.getSpInfo();
        Map<String, String> extraInfo = JsonUtil.defaultGson().fromJson(sp.getConfigInfo(), Map.class);
        String content = "【" + senderDTO.getOriginSignName() + "】 " + senderDTO.getSendContent(request.getParamsList(), request.getReqParamMap());

        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS");
        String merchantOrderId = dateFormat.format(new Date()) + "-" + orgId + "-"  + request.getBusinessType() + "-" + mobile;
        if (merchantOrderId.length() > 64) {
            //orderid限制在64以内
            merchantOrderId = merchantOrderId.substring(0, 63);
        }

        FormBody.Builder requestBuilder = new FormBody.Builder();
        requestBuilder.add("apikey", sp.getSecretKey());
        requestBuilder.add("mobile", mobile);
        requestBuilder.add("text", content);
        requestBuilder.add("uid", merchantOrderId);
        requestBuilder.add("callback_url", extraInfo.get("callback"));

        String responseTxt = "";

        Request smsRequest = new
                Request.Builder()
                .url(getSenderBaseUrl() + "v2/sms/single_send.json")
                .post(requestBuilder.build()).build();

        String maskPhone = MaskUtil.maskMobile(mobile);
        @Cleanup
        Response response = okHttpClient.newCall(smsRequest).execute();
        if (response.isSuccessful()) {
            String text = response.body().string();
            if (text != null) {
                responseTxt = text;
            }
        } else {
            Phonenumber.PhoneNumber phoneNumber = new Phonenumber.PhoneNumber()
                    .setCountryCode(Integer.parseInt(request.getTelephone().getNationCode()))
                    .setNationalNumber(Long.parseLong(request.getTelephone().getMobile().trim()));
            boolean valid = phoneUtil.isValidNumber(phoneNumber);
            log.error("ALERT sendsms from zhixin_intl failed. {} code:{} msg:{} {} valid:{}",
                    MaskUtil.maskValidateCode(TextFormat.shortDebugString(request)),
                    response.code(), response.message(), response.body(), valid);

            smsDeliveryRecordService.updateTempMessageIdExtra(senderDTO.getTempMessageId(), senderDTO.getTempMessageId(),
                    "request-200",  response.code() + " " + response.message(), 1,
                    "", "");
        }


        if (StringUtils.isEmpty(responseTxt)) {
            log.warn("ALERT sendsms from zhixin_intl failed. {}", maskPhone);
            return false;
        }

        log.info("sp:{} orgId:{} phone:{} content:{} result={}",
                senderDTO.getSpInfo().getId(), orgId, maskPhone,
                MaskUtil.maskValidateCode(content),
                responseTxt.replace(mobile, maskPhone));
        JsonObject jo = JsonUtil.defaultGson().fromJson(responseTxt, JsonElement.class).getAsJsonObject();
        //JSONObject jo = JSON.parseObject(responseTxt);
        if (GsonObjectUtil.getAsInt(jo,"code", -1) == 0) {
            smsDeliveryRecordService.updateTempMessageIdExtra(senderDTO.getTempMessageId(), GsonObjectUtil.getAsString(jo, "sid"),
                    "request-200",  GsonObjectUtil.getAsString(jo, "msg"), GsonObjectUtil.getAsInt(jo, "count", 1),
                    PRICE_MAP.getOrDefault(request.getTelephone().getNationCode(), BigDecimal.ZERO).multiply(new BigDecimal(GsonObjectUtil.getAsInt(jo, "count", 1))).toPlainString(), "CNY");
        } else {
            log.error("send sms failed:{} {}", maskPhone, responseTxt);

            smsDeliveryRecordService.updateTempMessageId(senderDTO.getTempMessageId(), senderDTO.getTempMessageId(),
                    "request-failed", GsonObjectUtil.getAsInt(jo,"code", -1) + "-" + GsonObjectUtil.getAsString(jo, "msg"));
        }
        PrometheusUtil.smsSendCounter(SendChannelEnum.ZHIXIN_INTL.name(), GsonObjectUtil.getAsString(jo,"code").equals("1") ? 200 : 400);

        return true;
    }

    @PostConstruct
    private void loadPrice() {
        Arrays.stream(ZHIXIN_PRICES.split("\n")).forEach(s -> {
            String[] arr = s.split("\t");
            PRICE_MAP.put(arr[0], new BigDecimal(arr[1]));
        });
        log.info("{}", PRICE_MAP);
    }


    private static String ZHIXIN_PRICES = "508\t0.5934\n" +
            "48\t0.3266\n" +
            "92\t0.3634\n" +
            "63\t0.1472\n" +
            "675\t0.41975\n" +
            "689\t1.3133\n" +
            "51\t0.40595\n" +
            "507\t0.5704\n" +
            "968\t0.59225\n" +
            "64\t0.9154\n" +
            "977\t0.42665\n" +
            "47\t0.5911\n" +
            "31\t0.9223\n" +
            "505\t0.5727\n" +
            "234\t0.4186\n" +
            "227\t0.71185\n" +
            "687\t1.3409\n" +
            "264\t0.4209\n" +
            "258\t0.2484\n" +
            "60\t0.2622\n" +
            "52\t0.3427\n" +
            "265\t0.40365\n" +
            "960\t0.12075\n" +
            "230\t0.3243\n" +
            "356\t0.2875\n" +
            "1664\t0.45195\n" +
            "222\t0.84525\n" +
            "596\t0.7061\n" +
            "1670\t0.115\n" +
            "853\t0.2875\n" +
            "976\t0.55315\n" +
            "95\t0.68885\n" +
            "223\t0.74405\n" +
            "389\t0.2392\n" +
            "261\t0.3036\n" +
            "382\t0.2829\n" +
            "373\t0.46575\n" +
            "377\t0.89585\n" +
            "212\t0.6463\n" +
            "218\t0.6187\n" +
            "371\t0.4577\n" +
            "352\t0.28635\n" +
            "370\t0.20355\n" +
            "266\t0.4646\n" +
            "231\t0.5336\n" +
            "94\t0.50485\n" +
            "423\t0.2921\n" +
            "1758\t0.3565\n" +
            "961\t0.35995\n" +
            "856\t0.322\n" +
            "7\t0.5589\n" +
            "1345\t0.44045\n" +
            "965\t0.35995\n" +
            "82\t0.13915\n" +
            "1869\t1.06145\n" +
            "269\t0.3105\n" +
            "686\t0.4025\n" +
            "855\t0.4048\n" +
            "996\t0.47495\n" +
            "254\t0.5244\n" +
            "81\t0.52325\n" +
            "962\t0.69\n" +
            "1876\t0.55315\n" +
            "263\t0.44275\n" +
            "260\t0.38525\n" +
            "39\t0.64975\n" +
            "354\t0.2622\n" +
            "98\t0.41975\n" +
            "964\t0.69\n" +
            "27\t0.25185\n" +
            "91\t0.1771\n" +
            "972\t0.483\n" +
            "353\t0.64975\n" +
            "62\t0.2806\n" +
            "269\t0.61755\n" +
            "36\t0.8119\n" +
            "967\t0.49795\n" +
            "509\t0.65205\n" +
            "385\t0.63135\n" +
            "504\t0.414\n" +
            "852\t0.3565\n" +
            "592\t0.5727\n" +
            "245\t0.77165\n" +
            "1671\t1.36045\n" +
            "502\t0.41515\n" +
            "30\t0.6371\n" +
            "240\t0.36915\n" +
            "590\t1.4743\n" +
            "224\t0.84525\n" +
            "220\t0.3105\n" +
            "299\t0.0966\n" +
            "350\t0.13455\n" +
            "233\t0.46575\n" +
            "594\t0.9269\n" +
            "995\t0.5612\n" +
            "1473\t0.4278\n" +
            "685\t0.3611\n" +
            "44\t0.322\n" +
            "241\t0.45655\n" +
            "33\t0.6118\n" +
            "298\t0.1541\n" +
            "679\t0.391\n" +
            "358\t0.7567\n" +
            "678\t0.48185\n" +
            "84\t0.37375\n" +
            "1284\t0.2093\n" +
            "1340\t0.5221\n" +
            "58\t0.35305\n" +
            "251\t0.36915\n" +
            "34\t0.67045\n" +
            "1784\t0.48875\n" +
            "998\t0.9039\n" +
            "598\t0.6555\n" +
            "20\t0.55315\n" +
            "372\t0.7383\n" +
            "1\t0.06555\n" +
            "593\t0.84295\n" +
            "213\t1.10745\n" +
            "256\t0.575\n" +
            "380\t0.5796\n" +
            "1809\t0.50485\n" +
            "1767\t0.4715\n" +
            "45\t0.26105\n" +
            "255\t0.36685\n" +
            "253\t0.87285\n" +
            "886\t0.3059\n" +
            "49\t0.78545\n" +
            "1868\t0.3174\n" +
            "90\t0.19895\n" +
            "676\t0.37375\n" +
            "216\t0.76475\n" +
            "993\t0.6486\n" +
            "670\t0.8786\n" +
            "420\t0.43585\n" +
            "992\t0.24035\n" +
            "357\t0.56925\n" +
            "66\t0.1748\n" +
            "599\t0.4761\n" +
            "228\t0.3427\n" +
            "238\t0.6854\n" +
            "53\t0.3726\n" +
            "235\t0.45195\n" +
            "506\t0.3772\n" +
            "1649\t0.3243\n" +
            "57\t0.3105\n" +
            "237\t0.54855\n" +
            "56\t0.5773\n" +
            "682\t0.3174\n" +
            "268\t0.27485\n" +
            "963\t0.6049\n" +
            "225\t0.8142\n" +
            "41\t0.55545\n" +
            "1721\t0.47495\n" +
            "242\t0.49795\n" +
            "503\t0.4048\n" +
            "236\t0.44275\n" +
            "243\t0.4485\n" +
            "239\t1.25465\n" +
            "211\t0.35765\n" +
            "597\t0.2737\n" +
            "1\t0.07015\n" +
            "252\t0.6256\n" +
            "221\t0.56695\n" +
            "378\t0.60145\n" +
            "232\t0.3335\n" +
            "421\t0.59685\n" +
            "501\t0.2599\n" +
            "375\t0.5796\n" +
            "386\t0.2599\n" +
            "267\t0.3726\n" +
            "65\t0.29095\n" +
            "46\t0.5221\n" +
            "975\t0.3036\n" +
            "249\t0.4807\n" +
            "1242\t0.3082\n" +
            "55\t0.2139\n" +
            "248\t0.42435\n" +
            "677\t0.77165\n" +
            "599\t0.47495\n" +
            "966\t0.22195\n" +
            "591\t0.51175\n" +
            "673\t0.1357\n" +
            "1441\t0.4324\n" +
            "229\t0.58765\n" +
            "257\t0.83145\n" +
            "973\t0.16675\n" +
            "359\t0.5819\n" +
            "250\t0.40595\n" +
            "226\t0.36915\n" +
            "7\t0.44735\n" +
            "32\t0.6946\n" +
            "880\t0.667\n" +
            "381\t0.26335\n" +
            "1246\t0.47725\n" +
            "387\t0.6785\n" +
            "40\t0.5865\n" +
            "994\t1.15345\n" +
            "297\t0.437\n" +
            "61\t0.44275\n" +
            "43\t0.66125\n" +
            "262\t1.57205\n" +
            "1684\t0.7245\n" +
            "54\t0.5589\n" +
            "244\t0.34615\n" +
            "374\t1.11205\n" +
            "355\t0.74865\n" +
            "1264\t0.46575\n" +
            "1268\t0.42435\n" +
            "93\t0.71645\n" +
            "971\t0.26795\n" +
            "376\t0.60375\n" +
            "247\t1.18795\n" +
            "974\t0.4094\n" +
            "595\t0.21275\n" +
            "680\t0.66585\n" +
            "351\t0.44275\n" +
            "970\t0.46115\n" +
            "1787\t0.4243";

}
