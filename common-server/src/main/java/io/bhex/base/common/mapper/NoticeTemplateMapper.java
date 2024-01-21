package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.NoticeTemplate;
import io.bhex.base.common.entity.SpInfo;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

@Component
@org.apache.ibatis.annotations.Mapper
public interface NoticeTemplateMapper extends Mapper<NoticeTemplate> {

}
