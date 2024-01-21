package io.bhex.base.common.entity;

import io.bhex.base.common.util.MD5Util;
import lombok.Data;

@Data
public class ZhuTongBaseModel {

    private String username;

    private String password;

    private Long tKey;

    public String getPassword() {
        return MD5Util.getMD5(MD5Util.getMD5(this.password) + tKey);
    }



}
