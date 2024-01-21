package io.bhex.base.common.config;

import org.apache.commons.lang3.StringUtils;

/**
 * @Description:
 * @Date: 2018/10/30 下午2:52
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
public class ContainerConfig {
    public static String getContainer(){
        String container = System.getProperty("container");
        if(StringUtils.isEmpty(container)){
            container = "bhex";
        }
        return container;
    }
}
