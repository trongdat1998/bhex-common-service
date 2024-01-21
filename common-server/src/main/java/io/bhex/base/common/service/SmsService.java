/*
 ************************************
 * @项目名称: bhex-common-service
 * @文件名称: SmsService
 * @Date 2018/08/14
 * @Author will.zhao@bhex.io
 * @Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
 **************************************
 */
package io.bhex.base.common.service;

import io.bhex.base.common.SendSmsRequest;
import io.bhex.base.common.SmsReply;
import io.bhex.base.common.SmsRequest;

public interface SmsService {

    String SMS_SIGN_DEFAULT = "Bhex";

    SmsReply send(SmsRequest request);

    SmsReply sendSms(SendSmsRequest request);

}
