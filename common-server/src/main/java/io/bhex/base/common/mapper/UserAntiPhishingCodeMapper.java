package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.UserAntiPhishingCode;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

@Component
@org.apache.ibatis.annotations.Mapper
public interface UserAntiPhishingCodeMapper extends Mapper<UserAntiPhishingCode> {

//    @Select("select anti_phishing_code from tb_user_anti_phishing_code where org_id = #{orgId} and user_id = #{userId}")
//    String getAntiPhishingCode(@Param("orgId") long orgId, @Param("userId") long userId);

    @Select("select * from tb_user_anti_phishing_code where org_id = #{orgId} and user_id = #{userId}")
    UserAntiPhishingCode getAntiPhishingCode(@Param("orgId") long orgId, @Param("userId") long userId);
}
