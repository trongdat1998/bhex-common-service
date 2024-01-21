package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.SpAccountInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

@Component
@Deprecated
@org.apache.ibatis.annotations.Mapper
public interface SpAccountInfoMapper extends Mapper<SpAccountInfo> {

    @Select("select * from tb_sp_account_info where channel = #{channel} and sp_type=#{spType}")
    List<SpAccountInfo> querySpInfos(@Param("channel") String channel, @Param("spType") String spType);

    @Select("select * from tb_sp_account_info where channel = #{channel}")
    List<SpAccountInfo> querySpInfosByChannel(@Param("channel") String channel);
}
