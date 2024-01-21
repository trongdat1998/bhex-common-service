package io.bhex.base.common.service.impl;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.bhex.base.common.EditAppCertInfoRequest;
import io.bhex.base.common.GetAppCertInfosReply;
import io.bhex.base.common.GetPushSwitchesReply;
import io.bhex.base.common.entity.AppPushRecord;
import io.bhex.base.common.entity.AppPushSwitch;
import io.bhex.base.common.entity.SpInfo;
import io.bhex.base.common.mapper.AppPushRecordMapper;
import io.bhex.base.common.mapper.AppPushSwitchMapper;
import io.bhex.base.common.mapper.SpInfoMapper;
import io.bhex.base.common.sender.push.HuaweiSender;
import io.bhex.base.common.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AppPushService {
    @Autowired
    private AppPushRecordMapper appPushRecordMapper;
    @Autowired
    private AppPushSwitchMapper appPushSwitchMapper;
    @Autowired
    private SpInfoMapper spInfoMapper;
    @Autowired
    private HuaweiSender huaweiSender;


    private Set<String> switchSet = new HashSet<>();


    public long insertRecord(AppPushRecord record) {
        appPushRecordMapper.insertSelective(record);
        return record.getId();
    }

    public void editAppCertInfo(EditAppCertInfoRequest request) {
        String pushChannel = request.getPushChannel().toUpperCase();
        if (PushChannelEnum.valueOf(pushChannel) == PushChannelEnum.APPLE) {
            editApplePushSpInfo(request);
        } else {
            editHuaweiFcmSpInfo(request);
        }
    }

    private void editHuaweiFcmSpInfo(EditAppCertInfoRequest request) {
        String pushChannel = request.getPushChannel().toUpperCase();
        Example example = new Example(SpInfo.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("channel", pushChannel)
                .andEqualTo("orgId", request.getOrgId())
                .andEqualTo("noticeType", "PUSH")
                .andEqualTo("appId", request.getBundleId());

        List<SpInfo> spInfos = spInfoMapper.selectByExample(example);
        SpInfo spInfo;
        byte[] encryptResult = AESUtil.encrypt(request.getDeveloperSecretKey(), pushChannel + request.getDeveloperAppId());
        String encryptResultStr = AESUtil.parseByte2HexStr(encryptResult);
        if (CollectionUtils.isEmpty(spInfos)) {
            spInfo = new SpInfo();
            spInfo.setOrgId(request.getOrgId());
            spInfo.setChannel(pushChannel);
            spInfo.setNoticeType("PUSH");
            spInfo.setMsgType("ALL");
            spInfo.setPosition(1000);
            spInfo.setWeight(1);
            spInfo.setAccessKeyId(request.getDeveloperAppId());
            spInfo.setSecretKey(encryptResultStr);
            spInfo.setConfigInfo("");
            spInfo.setEnv("BHOP");
            spInfo.setSupportWhole(1);
            spInfo.setAppId(request.getBundleId());
            spInfo.setStatus(1);
            spInfo.setCanSyncSmsTmpl(0);
            spInfo.setCreated(new Timestamp(System.currentTimeMillis()));
            spInfo.setUpdated(new Timestamp(System.currentTimeMillis()));
            spInfo.setRequestUrl("");
            spInfoMapper.insertSelective(spInfo);
        } else {
            spInfo = spInfos.get(0);
            spInfo.setAccessKeyId(request.getDeveloperAppId());
            if (!spInfo.getSecretKey().equals(request.getDeveloperSecretKey())) {
                spInfo.setSecretKey(encryptResultStr);
            }
            spInfo.setUpdated(new Timestamp(System.currentTimeMillis()));
            spInfoMapper.updateByPrimaryKeySelective(spInfo);
        }
    }

    private void editApplePushSpInfo(EditAppCertInfoRequest request) {
        String pushChannel = request.getPushChannel().toUpperCase();
        Example example = new Example(SpInfo.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("channel", pushChannel)
                .andEqualTo("orgId", request.getOrgId())
                .andEqualTo("noticeType", "PUSH")
                .andEqualTo("appId", request.getBundleId());

        List<SpInfo> spInfos = spInfoMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(spInfos)) {
            spInfos = spInfos.stream()
                    .filter(s -> StringUtils.isNotEmpty(s.getConfigInfo()))
                    .filter(s -> {
                        JsonObject jo = JsonUtil.defaultGson().fromJson(s.getConfigInfo(), JsonElement.class).getAsJsonObject();
                        String appChannel = GsonObjectUtil.getAsString(jo, "app_channel");
                        return appChannel.equalsIgnoreCase(request.getAppChannel());
                    })
                    .collect(Collectors.toList());
        }

        SpInfo spInfo;
        String salt = RandomStringUtils.randomAlphanumeric(6);
        String accessKeyId = AESUtil.parseByte2HexStr(AESUtil.encrypt(request.getPassword(), "APPLE" + salt));
        Map<String, String> config = Maps.newHashMap();
        config.put("salt", salt);
        config.put("app_channel", request.getAppChannel());
        config.put("cert_type", "production");

        byte[] encryptResult = AESUtil.encrypt(request.getDeveloperSecretKey(), "APPLE" + accessKeyId);
        String encryptResultStr = AESUtil.parseByte2HexStr(encryptResult);

        if (CollectionUtils.isEmpty(spInfos)) {
            spInfo = new SpInfo();
            spInfo.setOrgId(request.getOrgId());
            spInfo.setChannel(request.getPushChannel().toUpperCase());
            spInfo.setNoticeType("PUSH");
            spInfo.setMsgType("ALL");
            spInfo.setPosition(1000);
            spInfo.setWeight(1);
            spInfo.setAccessKeyId(accessKeyId);
            spInfo.setSecretKey(encryptResultStr);
            spInfo.setConfigInfo(JsonUtil.defaultGson().toJson(config));
            spInfo.setEnv("BHOP");
            spInfo.setSupportWhole(1);
            spInfo.setAppId(request.getBundleId());
            spInfo.setStatus(1);
            spInfo.setCanSyncSmsTmpl(0);
            spInfo.setCreated(new Timestamp(System.currentTimeMillis()));
            spInfo.setUpdated(new Timestamp(System.currentTimeMillis()));
            spInfo.setRequestUrl("");
            spInfoMapper.insertSelective(spInfo);
        } else {
            spInfo = spInfos.get(0);
            if (spInfo.getSecretKey().equals(request.getDeveloperSecretKey())) {
                return;
            }
            spInfo.setSecretKey(encryptResultStr);
            spInfo.setAccessKeyId(accessKeyId);
            spInfo.setConfigInfo(JsonUtil.defaultGson().toJson(config));
            spInfo.setUpdated(new Timestamp(System.currentTimeMillis()));
            spInfoMapper.updateByPrimaryKeySelective(spInfo);
        }
    }

    public GetAppCertInfosReply getAppSpInfos(long orgId) {
        Example example = new Example(SpInfo.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("orgId", orgId)
                .andEqualTo("noticeType", "PUSH");

        List<SpInfo> spInfos = spInfoMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(spInfos)) {
            return GetAppCertInfosReply.getDefaultInstance();
        }
        List<GetAppCertInfosReply.AppCertInfo> list = spInfos.stream().map(s -> {
            GetAppCertInfosReply.AppCertInfo.Builder builder = GetAppCertInfosReply.AppCertInfo.newBuilder();

            builder.setBundleId(s.getAppId());
            builder.setDeveloperAppId(s.getAccessKeyId());
            builder.setDeveloperSecretKey(s.getSecretKey());
            builder.setPushChannel(s.getChannel());

            if (s.getChannel().equals("APPLE")) {
                JsonObject jo = JsonUtil.defaultGson().fromJson(s.getConfigInfo(), JsonElement.class).getAsJsonObject();
                String appChannel = GsonObjectUtil.getAsString(jo, "app_channel");
                builder.setAppChannel(appChannel);
                byte[] decryptFrom = AESUtil.parseHexStr2Byte(s.getAccessKeyId());
                byte[] password = AESUtil.decrypt(decryptFrom, s.getChannel() + GsonObjectUtil.getAsString(jo, "salt"));
                builder.setPassword(new String(password));
            }

            return builder.build();
        }).collect(Collectors.toList());
        return GetAppCertInfosReply.newBuilder().addAllAppCertInfo(list).build();

    }

    public void openSwitch(long orgId, String switchType, boolean open) {
        AppPushSwitch appPushSwitch = appPushSwitchMapper.getPushSwitch(orgId, switchType);
        if (appPushSwitch == null) {
            appPushSwitch = AppPushSwitch.builder()
                    .orgId(orgId)
                    .switchType(switchType)
                    .status(open ? 1 : 0)
                    .createdAt(new Timestamp(System.currentTimeMillis()))
                    .updatedAt(new Timestamp(System.currentTimeMillis()))
                    .build();
            appPushSwitchMapper.insertSelective(appPushSwitch);
        } else {
            appPushSwitch.setStatus(open ? 1 : 0);
            appPushSwitch.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            appPushSwitchMapper.updateByPrimaryKeySelective(appPushSwitch);
        }
    }

    public GetPushSwitchesReply getPushSwitches(long orgId) {
        List<AppPushSwitch> switches = appPushSwitchMapper.getPushSwitches(orgId);
        if (CollectionUtils.isEmpty(switches)) {
            return GetPushSwitchesReply.getDefaultInstance();
        }
        List<GetPushSwitchesReply.PushSwitch> result = switches.stream()
                .map(s -> {
                    GetPushSwitchesReply.PushSwitch.Builder builder = GetPushSwitchesReply.PushSwitch.newBuilder();
                    builder.setOrgId(s.getOrgId());
                    builder.setStatus(s.getStatus());
                    builder.setSwitchType(GetPushSwitchesReply.SwitchType.valueOf(s.getSwitchType()));
                    builder.setCreated(s.getCreatedAt().getTime());
                    builder.setUpdated(s.getUpdatedAt().getTime());
                    builder.setId(s.getId());
                    return builder.build();
                })
                .collect(Collectors.toList());
        return GetPushSwitchesReply.newBuilder()
                .addAllPushSwitch(result)
                .build();
    }


    public Combo2<Boolean, String> canSendPush(long orgId, boolean bizPush, String pushChannel) {
        if (PushChannelEnum.getByName(pushChannel) == null) {
            log.info("no channel : {}", pushChannel);
            return new Combo2<>(false, "no push channel");
        }

        boolean globalSwitchOpen = switchSet.contains(orgId + "ALL_SITE");
        if (!globalSwitchOpen) {
            log.info("no global switch : {}", orgId);
            return new Combo2<>(false, "no global switch");
        }

        if (!bizPush) {
            boolean customSwitchOpen = switchSet.contains(orgId + "CUSTOM");
            if (!customSwitchOpen) {
                log.info("no custom switch : {}", orgId);
                return new Combo2<>(false, "no custom switch");
            }
        }

        return new Combo2<>(true, "");
    }


    @PostConstruct
    @Scheduled(initialDelay = 120_000, fixedRate = 117_000)
    private void loadPushSwitched(){
        List<AppPushSwitch> list = appPushSwitchMapper.getAllPushSwitches();
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        Set<String> _switchSet = new HashSet<>();
        for (AppPushSwitch appPushSwitch : list) {
            _switchSet.add(appPushSwitch.getOrgId() + appPushSwitch.getSwitchType());
        }
        switchSet = _switchSet;
    }


}
