package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.AppPushSwitch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

@Component
@org.apache.ibatis.annotations.Mapper
public interface AppPushSwitchMapper extends Mapper<AppPushSwitch> {


    @Select({"<script>" +
            "select * from tb_app_push_switch where 1 = 1 " ,
            "<when test='orgId > 0'>",
            "AND org_id = #{orgId}",
            "</when>",
            " order by id desc limit 100" ,
            "</script>"})
    List<AppPushSwitch> getPushSwitches(@Param("orgId") long orgId);

    @Select("select org_id,switch_type from tb_app_push_switch where status = 1")
    List<AppPushSwitch> getAllPushSwitches();

    @Select("select * from tb_app_push_switch where org_id = #{orgId} and switch_type = #{switchType}")
    AppPushSwitch getPushSwitch(@Param("orgId") long orgId, @Param("switchType") String switchType);
}
