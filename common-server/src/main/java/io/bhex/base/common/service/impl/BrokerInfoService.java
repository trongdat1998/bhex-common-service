package io.bhex.base.common.service.impl;


import com.google.common.collect.Lists;
import io.bhex.base.common.CreateSmsSignRequest;
import io.bhex.base.common.EditEmailTemplateRequest;
import io.bhex.base.common.SmsSign;
import io.bhex.base.common.entity.BrokerInfo;
import io.bhex.base.common.entity.EmailTemplate;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.entity.ZhuTongBaseModel;
import io.bhex.base.common.mapper.BrokerInfoMapper;
import io.bhex.base.common.mapper.EmailTemplateMapper;
import io.bhex.base.common.mapper.SpInfoMapper;
import io.bhex.base.common.util.AESUtil;
import io.bhex.base.common.util.EnableStatusEnum;
import io.bhex.base.common.util.JsonUtil;
import io.bhex.base.common.util.SendChannelEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
public class BrokerInfoService {

    @Autowired
    private BrokerInfoMapper brokerInfoMapper;
    @Autowired
    private EmailTemplateMapper emailTemplateMapper;
    @Autowired
    private SpInfoMapper spInfoMapper;
    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;

    public void createSmsSign(CreateSmsSignRequest request) {
        long orgId = request.getOrgId();
        BrokerInfo brokerInfo = brokerInfoMapper.getBroker(orgId);

        if (brokerInfo != null) {
            if (!StringUtils.isEmpty(request.getOrgName())) {
                brokerInfo.setOrgName(request.getOrgName());
            }
            if (!StringUtils.isEmpty(request.getSignName()) && !request.getSignName().equals(brokerInfo.getSignName())) {
                brokerInfo.setSignName(request.getSignName());
                try {
                    createZhutongSign(request.getSignName());
                } catch (Exception e) {
                    log.error("createZhutongSign error", e);
                    throw new RuntimeException(e);
                }
            }
            if (!CollectionUtils.isEmpty(request.getLanguageList())) {
                brokerInfo.setLanguages(String.join(",", request.getLanguageList()));
            }
            brokerInfoMapper.updateByPrimaryKey(brokerInfo);
        } else {
            brokerInfo = new BrokerInfo();
            brokerInfo.setOrgId(orgId);
            brokerInfo.setOrgName(request.getOrgName());
            brokerInfo.setSignName(request.getSignName());
            brokerInfo.setLanguages(String.join(",", request.getLanguageList()));
            brokerInfo.setStatus(1);
            brokerInfo.setEnv("BHOP");
            brokerInfoMapper.insertSelective(brokerInfo);
            try {
                createZhutongSign(request.getSignName());
            } catch (Exception e) {
                log.error("createZhutongSign error", e);
                throw new RuntimeException(e);
            }

        }
    }

    public List<SmsSign> list(long orgId) {
        Example example = new Example(BrokerInfo.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("status", EnableStatusEnum.ENABLED.getValue());
        if (orgId > 0) {
            criteria.andEqualTo("orgId", orgId);
        }

        List<BrokerInfo> brokerInfos = brokerInfoMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(brokerInfos)) {
            return new ArrayList<>();
        }
        List<SmsSign> list = brokerInfos.stream().map(b ->
                SmsSign.newBuilder()
                        .setOrgId(b.getOrgId())
                        .setOrgName(b.getOrgName())
                        .setSignName(b.getSignName())
                        .addAllLanguage(Arrays.asList(b.getLanguages().split(",")))
                        .build()
        ).collect(Collectors.toList());
        return list;
    }

    @Transactional
    public void editEmailTemplate(EditEmailTemplateRequest request) {
        emailTemplateMapper.deleteTemplates(request.getOrgId());
        request.getEmailTemplateList().forEach(i -> {
            EmailTemplate image = new EmailTemplate();
            image.setOrgId(request.getOrgId());
            image.setLanguage(i.getLanguage());
            image.setTemplateContent(i.getTemplateContent());
            image.setCreated(new Timestamp(System.currentTimeMillis()));
            image.setUpdated(new Timestamp(System.currentTimeMillis()));
            emailTemplateMapper.insertSelective(image);
        });
    }

    public List<io.bhex.base.common.EmailTemplate> queryTitleImages(long orgId) {
        List<EmailTemplate> images = emailTemplateMapper.queryTemplates(orgId);
        if (CollectionUtils.isEmpty(images)) {
            return Lists.newArrayList();
        }
        return images.stream().map(i -> {
            io.bhex.base.common.EmailTemplate.Builder builder = io.bhex.base.common.EmailTemplate.newBuilder();
            BeanUtils.copyProperties(i, builder);
            return builder.build();
        }).collect(Collectors.toList());
    }

    public EmailTemplate getEmailTemplate(long orgId, String language) {
        List<EmailTemplate> templates = emailTemplateMapper.queryTemplateByLanguage(orgId, language);
        Optional<EmailTemplate> myTemplateOptional = templates.stream().filter(t -> t.getOrgId() == orgId).filter(t -> t.getLanguage().equals(language)).findFirst();
        if (myTemplateOptional.isPresent()) {
            return myTemplateOptional.get();
        }

        Optional<EmailTemplate> defaultTemplateOptional = templates.stream().filter(t -> t.getOrgId() == 0).filter(t -> t.getLanguage().equals(language)).findFirst();
        if (defaultTemplateOptional.isPresent()) {
            return defaultTemplateOptional.get();
        }

        if (!language.equals(Locale.US.toString())) {
            Optional<EmailTemplate> myUsTemplateOptional = templates.stream().filter(t -> t.getOrgId() == orgId).filter(t -> t.getLanguage().equals(Locale.US.toString())).findFirst();
            if (myUsTemplateOptional.isPresent()) {
                return myUsTemplateOptional.get();
            }
        }
        
        if (!language.equals(Locale.US.toString())) {
            Optional<EmailTemplate> defaultUsTemplateOptional = templates.stream().filter(t -> t.getOrgId() == 0).filter(t -> t.getLanguage().equals(Locale.US.toString())).findFirst();
            if (defaultUsTemplateOptional.isPresent()) {
                return defaultUsTemplateOptional.get();
            }
        }
        return null;
    }

    @Data
    private static class CreateSignModel extends ZhuTongBaseModel {
        private List<String> sign;
    }
    public void createZhutongSign(String signName)  throws IOException {
        Example example = new Example(SpInfo.class);
        example.orderBy("position").desc();
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("channel", SendChannelEnum.ZHUTONG.name())
                .andEqualTo("status", EnableStatusEnum.ENABLED.getValue());
        List<SpInfo> spInfos = spInfoMapper.selectByExample(example);
        Map<String, List<SpInfo>> groupMap = spInfos.stream().collect(Collectors.groupingBy(SpInfo::getAccessKeyId));

        for (String username : groupMap.keySet()) {
            String orginSecretKey = groupMap.get(username).get(0).getSecretKey();
            byte[] decryptFrom = AESUtil.parseHexStr2Byte(orginSecretKey);
            byte[] secretKey = AESUtil.decrypt(decryptFrom, SendChannelEnum.ZHUTONG.name() + username);

            String password =  new String(secretKey);
            CreateSignModel sendParam = new CreateSignModel();
            sendParam.setUsername(username);
            Long tKey = System.currentTimeMillis()/1000;
            sendParam.setTKey(tKey);
            sendParam.setPassword(password);
            sendParam.setSign(Arrays.asList("【" + signName + "】"));

            String data = JsonUtil.defaultGson().toJson(sendParam);
            String responseTxt = "";
            RequestBody requestBody =
                    RequestBody.create(MediaType.parse("application/json;charset=utf-8;"), data);
            Request request = new
                    Request.Builder()
                    .url("https://api.mix2.zthysms.com/sms/v1/sign")
                    .post(requestBody).build();
            Response response = okHttpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                String text = response.body().string();
                if (text != null) {
                    responseTxt = text;
                }
            }
            log.info("reply:{}", responseTxt);
        }
    }


}
