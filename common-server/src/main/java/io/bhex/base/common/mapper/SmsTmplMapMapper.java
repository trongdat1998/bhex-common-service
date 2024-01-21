package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.SmsTmplMapping;
import io.bhex.base.common.entity.SmsTmplateMapping;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;


/**
 * @Description:
 * @Date: 2019/1/28 下午5:54
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
@Component
@org.apache.ibatis.annotations.Mapper
public interface SmsTmplMapMapper extends Mapper<SmsTmplateMapping> {
}
