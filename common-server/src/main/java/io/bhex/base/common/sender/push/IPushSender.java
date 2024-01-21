package io.bhex.base.common.sender.push;

import io.bhex.base.common.entity.AppPushRecord;
import io.bhex.base.common.util.JsonUtil;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Map;

public interface IPushSender {
    boolean sendBusinessPush(AppPushRecord pushRecord);

    boolean sendPush(AppPushRecord pushRecord);

    default String getBiTag(AppPushRecord pushRecord) {
        String biTag = "";
        if (StringUtils.isEmpty(pushRecord.getBizType())) {
            biTag = "C" + pushRecord.getReqOrderId();
        } else {
            biTag = "B" + pushRecord.getReqOrderId();
        }
        return biTag;
    }

    default PushCustomDataDTO getUrlData(AppPushRecord pushRecord) {
//        {
//            "type": "1", // 1:跳转到H5，2：app内部页面跳转
//                "url": "https://www.baidu.com/",  // type = 1 必填
//                "page": "TABHOME"   // 参照九宫格page
//            "param":{
//            "name:" "test",
//                    "age:" "40",
//        }
//        }
        int urlType = pushRecord.getPushUrlType(); //1:跳转到H5，2：app内部页面跳转
        String pushUrl = pushRecord.getPushUrl();
        Map<String, String> urlParam = JsonUtil.defaultGson().fromJson(pushRecord.getPushUrlData(), Map.class);
        PushCustomDataDTO data = new PushCustomDataDTO();
        data.setType(urlType + "");
        if (urlType == 1) {
            data.setUrl(pushUrl);
        } else if (StringUtils.isEmpty(pushUrl)){
            data.setPage("tabHome");
        } else {
            String[] arr = pushUrl.split("&");
            for (String param : arr) {
                String[] paramArr = param.split("=");
                if (paramArr == null || paramArr.length < 2) {
                    //data.setPage("tabHome");
                } else if (paramArr[0].equalsIgnoreCase("page")) {
                    data.setPage(paramArr[1]);
                } else {
                    urlParam.put(paramArr[0], paramArr[1]);
                }
            }

        }
        data.setParam(JsonUtil.defaultGson().toJson(urlParam));
        data.setReqOrderId(getBiTag(pushRecord));
        return data;
    }
}
