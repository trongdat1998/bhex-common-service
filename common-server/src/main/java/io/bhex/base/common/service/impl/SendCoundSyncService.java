package io.bhex.base.common.service.impl;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.entity.NoticeTemplate;
import io.bhex.base.common.entity.SmsTmplateMapping;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.mapper.*;
import io.bhex.base.common.util.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SendCoundSyncService {

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

    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;

    public JsonObject post(String apiKey, String secretKey, TreeMap<String, String> treeMap, String postUrl) throws IOException {
        treeMap.put("smsUser", apiKey);
        String signature = SendCloudSignUtil.md5Signature(treeMap, secretKey);
        treeMap.put("signature", signature);
        Iterator<String> iterator = treeMap.keySet().iterator();

        FormBody.Builder requestBuilder = new FormBody.Builder();
        while (iterator.hasNext()) {
            String key = iterator.next();
            requestBuilder.add(key, treeMap.get(key));
        }


        Response response = okHttpClient.newCall(new Request.Builder()
                .url(postUrl)
                .post(requestBuilder.build())
                .build())
                .execute();
        ResponseBody body = response.body();
        String result = body.string();
        log.info("create template res:{}", result);
        JsonObject jo = JsonUtil.defaultGson().fromJson(result, JsonElement.class).getAsJsonObject();
        return jo;
    }

    //@Scheduled(cron = "30 2/5 * * * ?")
    private void syncSign() {

        String signNames = "BHOP,FChain,BITREET,MtToken,COINFINITY,葡萄交易所,Bitrabbit,币牛,CoinFox,BBE,SCEX,BHOPB,LUDEX,BHUO,BANKX,比特未来,YLEX,Apeex,BTC100,YOOEX,Bitcoffee,TSC交易所,CoinerEX,jinniu,Vanilla,gtaex,BafeEx,Nutex,Bitai,HomiEx,BTNEX,HydaxEx,DeerDex,hejiaoyi,COCO&MClouds,KHMERS,BHEX,JAE,beex,Pbank,Asproex,CanBit,Libra,Boboo,btcoin100,HuoQiu,Coinasa,热币指数,ward,Bitribe,itiger,Convofit,DOEX,SKEX,Bisonex,NVEX,code1ex,BTCC,BMGlobal,Caitex,YingEX,FlashEx,ZooNet,BIFU,BTCCTEST,Mexo,PGEX,boboo,COCO,BTW,BTWTEST,BGOEX,BLDHEX,Bexfor,jokercoin,PCSEX,qmall,GEX,WinEX,FirstBi,BitHot,BKEX,PLAZAEX,HoldEX,Chiliz,StarEx,beex,Bidesk,lionex,WenX,FedEx,XCEX,BGX,WTB,BBEX,OakEx,BHEX,Dsdaq,HighBi,OLACITY,ZKR,BtcMEX,HANBITCO-GLOBAL,Panda,Wellbtc,ProEX,GlobalEx,upupex,ZG,Bitkan,wallex,BitBay,NYCEX,SHTEx,KEX,XthetaGlobal,Crius,ENCOREX,POEBTC";
        //String signNames = "BHOP";
        SpInfo spInfo = getOneSpInfo();
        if (spInfo == null) {
            return;
        }

        String[] arr = signNames.split(",");

        for (String signName : arr) {
            TreeMap<String, String> treeMap = new TreeMap<>();
            treeMap.put("signName", signName);
            treeMap.put("signType", "0");
            try {
                JsonObject jsonObject = post(spInfo.getAccessKeyId(), spInfo.getSecretKey(), treeMap, "https://www.sendcloud.net/smsapi/sign/save");
                log.info("{} jo:{}", signName, jsonObject);
            } catch (Exception e) {
                log.error("error create sign", e);
            }
        }
    }

//    @Scheduled(cron = "10 3/5 * * * ?")
//    public void deleteTmpl() {
//        SpInfo spInfo = getOneSpInfo();
//        List<SmsTmplateMapping> tmplateMappings = getSmsTmpls("HBTC");
//        for (SmsTmplateMapping mapping : tmplateMappings) {
//
//
//            TreeMap<String, String> treeMap = new TreeMap<>();
//            treeMap.put("smsUser", spInfo.getAccessKeyId());
//            treeMap.put("templateIdStr", mapping.getTargetTmplId());
//            try {
//                JSONObject jo = post(spInfo.getAccessKeyId(), spInfo.getSecretKey(), treeMap, "https://www.sendcloud.net/smsapi/deletesms");
//                if (jo != null && jo.getInteger("statusCode") != null && jo.getInteger("statusCode") == 200) {
//                    mapping.setVerifyStatus(VerifyStatusEnum.PROCESSING.getValue());
//                    smsTmplMapMapper.updateByPrimaryKey(mapping);
//                }
//            } catch (Exception e) {
//                log.error("verifySmsTmpl", e);
//            }
//
//        }
//    }

    private SpInfo getOneSpInfo() {
        Example example = new Example(SpInfo.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("status", EnableStatusEnum.ENABLED.getValue()).andEqualTo("id", 150)
                .andEqualTo("channel", "SEND_CLOUD").andEqualTo("noticeType", "SMS");
        List<SpInfo> sps = spInfoMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(sps)) {
            return null;
        }
        SpInfo spInfo = sps.get(0);
        byte[] decryptFrom = AESUtil.parseHexStr2Byte(spInfo.getSecretKey());
        byte[] secretKey = AESUtil.decrypt(decryptFrom, spInfo.getChannel() + spInfo.getAccessKeyId());
        spInfo.setSecretKey(new String(secretKey));
        return spInfo;
    }

    private List<SmsTmplateMapping> getSmsTmpls(String signName) {
        Example example = new Example(SmsTmplateMapping.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("signName", signName)
                .andEqualTo("targetChannel", "SEND_CLOUD");
        List<SmsTmplateMapping> tmpls = smsTmplMapMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(tmpls)) {
            return Lists.newArrayList();
        }
        return tmpls;
    }

    //@Scheduled(cron = "* 3/5 * * * ?")
    private void syncSmsTmpl() {
        for (String s : Lists.newArrayList( "BafeEx")) {
            submitSmsTmpl(s);
            verifySmsTmpl(s);
        }
    }


    private void verifySmsTmpl(String signName) {
        SpInfo spInfo = getOneSpInfo();
        List<SmsTmplateMapping> tmplateMappings = getSmsTmpls(signName);
        for (SmsTmplateMapping mapping : tmplateMappings) {
            if (mapping.getVerifyStatus() >= VerifyStatusEnum.PROCESSING.getValue()) {
                continue;
            }

            TreeMap<String, String> treeMap = new TreeMap<>();
            treeMap.put("smsUser", spInfo.getAccessKeyId());
            treeMap.put("templateIdStr", mapping.getTargetTmplId());
            try {
                JsonObject jo = post(spInfo.getAccessKeyId(), spInfo.getSecretKey(), treeMap, "https://www.sendcloud.net/smsapi/submitsms");
                if (jo != null && jo.get("statusCode") != null && jo.get("statusCode").getAsInt() == 200) {
                    mapping.setVerifyStatus(VerifyStatusEnum.PROCESSING.getValue());
                    smsTmplMapMapper.updateByPrimaryKey(mapping);
                }
            } catch (Exception e) {
                log.error("verifySmsTmpl", e);
            }

        }
    }

    private void submitSmsTmpl(String signName) {
        log.info("sync sms template");

        List<SmsTmplateMapping> tmplateMappings = getSmsTmpls(signName);
        List<String> tmplNames = tmplateMappings.stream()
                .map(t -> t.getSignName() + "-" + t.getOriginTmplId())
                .collect(Collectors.toList());
        SpInfo spInfo = getOneSpInfo();

        Example example = new Example(NoticeTemplate.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("status", EnableStatusEnum.ENABLED.getValue())
                .andEqualTo("noticeType", "SMS")
                .andEqualTo("msgType", "VERIFY_CODE")
                .andNotIn("businessType", Lists.newArrayList("LOGIN_SUCCESS","GLOBAL","PROMOTION"))
                //.andIn("language", Lists.newArrayList("zh_CN", "en_US"));
                .andIn("language", Lists.newArrayList("zh_CN"));
        List<NoticeTemplate> tmpls = noticeTemplateMapper.selectByExample(example);

        tmpls = tmpls.stream()
                .filter(t -> !tmplNames.contains(signName + "-" + t.getId()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(tmpls)) {
            return;
        }

        for (NoticeTemplate tmpl : tmpls) {
            try {
                SmsTmplateMapping mapping = new SmsTmplateMapping();
                mapping.setSpId(spInfo.getId());
                mapping.setSignName(signName);
                mapping.setOriginTmplId(tmpl.getId());
                mapping.setTargetChannel(spInfo.getChannel());

                mapping.setVerifyStatus(VerifyStatusEnum.INIT.getValue());
                mapping.setCreated(new Timestamp(System.currentTimeMillis()));
                mapping.setUpdated(new Timestamp(System.currentTimeMillis()));
                mapping.setTargetTmplName(signName + "-" + tmpl.getId());

                String tmplContent = NoticeRenderUtil.render2SendCloudTmpl(tmpl.getTemplateContent()).replaceAll("~broker~", "%broker%");

                mapping.setTargetTmplContent(tmplContent);

                TreeMap<String, String> treeMap = new TreeMap<>();
                treeMap.put("templateName", mapping.getTargetTmplName());
                treeMap.put("templateText", tmplContent);
                treeMap.put("signName", signName);
                treeMap.put("smsTypeStr", tmpl.getMsgType().equals(MsgTypeEnum.VERIFY_CODE.name()) ? "0" : "1");
                treeMap.put("msgType", "0");

                log.info("m:{}", treeMap);
                JsonObject jo = post(spInfo.getAccessKeyId(), spInfo.getSecretKey(), treeMap, "https://www.sendcloud.net/smsapi/addsms");
                if (jo != null && jo.get("statusCode") != null && jo.get("statusCode").getAsInt() == 200) {
                    mapping.setTargetTmplId(jo.getAsJsonObject("info").get("templateId").getAsString());
                    smsTmplMapMapper.insertSelective(mapping);
                }
            } catch (Exception e) {
                log.error("e", e);
            }

        }
    }



}
