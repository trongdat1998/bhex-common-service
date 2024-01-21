package io.bhex.base.common.service;

import io.bhex.base.common.util.InternationalEnum;
import io.bhex.base.common.util.MsgTypeEnum;

public interface SmsTemplateService {

    
    String create(String templateName, String templateContent, String signName,
                          MsgTypeEnum msgType, InternationalEnum internationalEnum,
                          String apiKey, String secretKey);

    boolean submitVerify(String templateId, String apiKey, String secretKey);

    boolean verifyied(String templateId, String apiKey, String secretKey);





}
