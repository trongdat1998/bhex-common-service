package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.EmailTemplate;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

@Component
@org.apache.ibatis.annotations.Mapper
public interface EmailTemplateMapper extends Mapper<EmailTemplate> {

    @Delete("delete from tb_email_template where org_id = #{orgId}")
    int deleteTemplates(@Param("orgId") long orgId);

    @Select("select * from tb_email_template where org_id = #{orgId}")
    List<EmailTemplate> queryTemplates(@Param("orgId") long orgId);

    @Select("select * from tb_email_template where org_id in (#{orgId},0) and language in (#{language},'en_US')")
    List<EmailTemplate> queryTemplateByLanguage(@Param("orgId") long orgId, @Param("language") String language);
}
