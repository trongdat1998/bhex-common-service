package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.BrokerInfo;
import io.bhex.base.common.entity.NoticeTemplate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

@Component
@org.apache.ibatis.annotations.Mapper
public interface BrokerInfoMapper extends Mapper<BrokerInfo> {

    @Select("select * from tb_broker_info where org_id = #{orgId}")
    BrokerInfo getBroker(@Param("orgId") long orgId);
}
