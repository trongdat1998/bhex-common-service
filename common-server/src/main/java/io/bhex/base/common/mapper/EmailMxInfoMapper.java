package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.EmailMxInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

import java.sql.Timestamp;

@Component
@org.apache.ibatis.annotations.Mapper
public interface EmailMxInfoMapper extends Mapper<EmailMxInfo> {

    @Update("update tb_email_mx_info set status = 0, updated = #{now} where mx_ip = #{mxIp}")
    int disableMxIp(@Param("mxIp") String mxIp, @Param("now") Timestamp now);
}
