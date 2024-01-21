package io.bhex.base.common.entity;

import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.List;

/**
 * @Description: 券商基本信息
 * @Date: 2018/12/31 下午6:42
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
@Data
@Table(name="tb_broker_info")
public class BrokerInfo {

    @Id
    private Long id;

    private Long orgId;

    private String orgName;

    private String signName;

    private String languages;

    private String env;

    //1-ok 0-disable
    private Integer status;

    public List<String> getSupportedLanguages() {
        return Arrays.asList(languages.split(","));
    }

}
