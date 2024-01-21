package io.bhex.base.common.service.impl;

import io.bhex.base.common.MessageReply;
import io.bhex.base.common.entity.UserAntiPhishingCode;
import io.bhex.base.common.mapper.UserAntiPhishingCodeMapper;
import io.bhex.base.common.util.AESUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Slf4j
@Service
public class AntiPhishingCodeService {

    @Autowired
    private UserAntiPhishingCodeMapper antiPhishingCodeMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    public static final String ANTI_CODE_KEY = "ANTICODE-%s-%s";

    public static final String DEFAULT_SALT = "AntiPhishingServiceFromHbtc";

    public String getAntiFishCode(long orgId, long userId) {
        if (orgId == 0 || userId == 0) {
            return "";
        }

        UserAntiPhishingCode userAntiPhishingCode = antiPhishingCodeMapper.getAntiPhishingCode(orgId, userId);
        if (userAntiPhishingCode == null) {
            return "";
        }

        byte[] decryptFrom = AESUtil.parseHexStr2Byte(userAntiPhishingCode.getAntiPhishingCode());
        byte[] antiPhishingCode = AESUtil.decrypt(decryptFrom, userAntiPhishingCode.getSalt() + DEFAULT_SALT);
        return new String(antiPhishingCode);
    }

    public MessageReply editAntiPhishingCode(long orgId, long userId, String antiPhishingCode) {
        //String key = String.format(ANTI_CODE_KEY, orgId + "", userId + "");
        //redisTemplate.delete(key);
        String salt = RandomStringUtils.randomAlphanumeric(6);
        byte[] encryptResult = AESUtil.encrypt(antiPhishingCode, salt + DEFAULT_SALT);
        String encryptResultStr = AESUtil.parseByte2HexStr(encryptResult);

        UserAntiPhishingCode userAntiPhishingCode = antiPhishingCodeMapper.getAntiPhishingCode(orgId, userId);
        if (userAntiPhishingCode == null) {
            userAntiPhishingCode = new UserAntiPhishingCode();
            userAntiPhishingCode.setOrgId(orgId);
            userAntiPhishingCode.setUserId(userId);
            userAntiPhishingCode.setAntiPhishingCode(encryptResultStr);
            userAntiPhishingCode.setSalt(salt);
            userAntiPhishingCode.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            userAntiPhishingCode.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            antiPhishingCodeMapper.insertSelective(userAntiPhishingCode);
        } else {
            userAntiPhishingCode.setSalt(salt);
            userAntiPhishingCode.setAntiPhishingCode(encryptResultStr);
            userAntiPhishingCode.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            antiPhishingCodeMapper.updateByPrimaryKeySelective(userAntiPhishingCode);
        }
        
        return MessageReply.newBuilder().setSuccess(true).build();
    }
}
