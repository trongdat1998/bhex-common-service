package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.SpAccountInfo;
import io.bhex.base.common.entity.SpInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

@Component
@org.apache.ibatis.annotations.Mapper
public interface SpInfoMapper extends Mapper<SpInfo> {

    @Update("update tb_sp_info set status = 0 where access_key_id = #{username}")
    int disableZhuTongSpInfo(@Param("username") String username);
}
