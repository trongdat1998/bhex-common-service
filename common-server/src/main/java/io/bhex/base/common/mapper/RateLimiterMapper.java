package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.RateLimiter;
import io.bhex.base.common.entity.RouterConfigEntity;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

/**
 * @Description:
 * @Date: 2019/2/1 下午1:23
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
@Component
@org.apache.ibatis.annotations.Mapper
public interface RateLimiterMapper extends Mapper<RateLimiter> {


}
