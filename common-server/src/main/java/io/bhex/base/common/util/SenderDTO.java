package io.bhex.base.common.util;

import com.google.common.collect.Lists;
import io.bhex.base.common.entity.SpInfo;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Data
public class SenderDTO {

    private Integer originTemplateId;

    private String originTemplateContent;

    private String tempMessageId;

    //原始签名信息
    private String originSignName;

    private String signName;

    private String orgName;

    private String scenario;

    private String emailSubject;

    private String targetTmplId;

    private boolean wholeSend;

    private MsgTypeEnum msgType;

    private SpInfo spInfo;

    private String antiPhishingCode; //邮件专用
    private String emailTemplate; //邮件专用

    private String pushUrl = ""; //push专用
    private String pushTitle = ""; //push专用

    private String language;
    private String businessType;

    public String getSignName(){
        if (signName == null) {
            return null;
        }
        if (!signName.contains("【") && !signName.contains("】")) {
            return "【" + signName + "】";
        }
        return signName;
    }

    public String getEmailSubject(){
        return emailSubject.replaceAll("~broker~", orgName);
    }

    public String getSendContent(List<String> params, Map<String, String> reqParam) {
        String orginTmpl = originTemplateContent != null ? originTemplateContent : "";
        String org = orgName != null ? orgName : "";

        String content = orginTmpl.replaceAll("~broker~", org);
        content = NoticeRenderUtil.render(content, params, reqParam).replaceAll("【", "[").replaceAll("】", "]");

        return content;
    }

    public String getEmailSendContent(List<String> params, Map<String, String> reqParam) {
        String orginTmpl = originTemplateContent != null ? originTemplateContent : "";
        String org = orgName != null ? orgName : "";

        String getEmailBeginTitle = LanguageConstants.getEmailBeginTitle(language).replaceAll("~broker~", org);


        String localEmailContent = localEmailTmpl.replace("#emailBeginTitle#", getEmailBeginTitle);
        if (StringUtils.isNotEmpty(antiPhishingCode)) {
            String antiPhishingCodeContent = antiPhishingCodeTmpl
                    .replace("#antiPhishingCodeHold#", LanguageConstants.getAntiPhishingHold(language))
                    .replace("#antiPhishingCode#", antiPhishingCode);
            localEmailContent = localEmailContent.replace("#antiPhishingCodeTmpl#", antiPhishingCodeContent);
        } else {
            localEmailContent = localEmailContent.replace("#antiPhishingCodeTmpl#", "");
        }

        String emailContent = orginTmpl.replaceAll("~broker~", org);
        if (Lists.newArrayList("", "GLOBAL", "PROMOTION").contains(businessType)) {
            emailContent = NoticeRenderUtil.render(emailContent, params, reqParam);
        } else {
            emailContent = NoticeRenderUtil.renderEmailContent(emailContent, params, reqParam);
        }

        localEmailContent = localEmailContent.replace("#emailContent#", emailContent);

        return emailTemplate.replace("#content#", localEmailContent).replaceAll("~broker~", org);
    }


    private static final String antiPhishingCodeTmpl =   "<td style=\"font-size: 12px;line-height:16px;text-align: right;\">" +
            "                                        <span style=\"padding:3px 3px 3px 8px;border: 1px solid #90adde; display: inline-block;border-radius: 3px;white-space: nowrap;\">#antiPhishingCodeHold#" +
            "                                            <span style=\"background: #e9eff9;padding: 0 4px;border-radius: 3px;\">#antiPhishingCode#</span>" +
            "                                        </span>" +
            "                                    </td>";

    private static final String localEmailTmpl = "<table style=\"border:0;margin:0 auto;border-spacing:0;border-collapse:collapse;font-size: 14px;line-height: 20px;\" width=\"100%\">" +
            "                                <tr>\n" +
            "                                    <td style=\"font-weight: bold;\">#emailBeginTitle#</td>" +

            "#antiPhishingCodeTmpl#" +

            "                                </tr>\n" +
            "                                <tr>\n" +
            "                                    <td style=\"margin:0;padding-top:40px;font-size: 14px;line-height: 20px;\" colspan=\"2\">" +
            "#emailContent#" +
            "                                    </td>\n" +
            "                                </tr>\n" +
            "                            </table>";


}
