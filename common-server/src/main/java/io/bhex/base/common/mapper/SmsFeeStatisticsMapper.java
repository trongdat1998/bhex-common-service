package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.BrokerInfo;
import io.bhex.base.common.entity.SmsFeeStatistics;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

@Component
@org.apache.ibatis.annotations.Mapper
public interface SmsFeeStatisticsMapper extends Mapper<SmsFeeStatistics> {

    @Select("select ifnull(max(last_record_id), 0) from tb_sms_fee_statistics")
    Long getMaxRecordId();

    @Select("select * from tb_sms_fee_statistics where org_id = #{orgId} and channel = #{channel} and statistics_time = #{statisticsTime} and price_unit = #{priceUnit} limit 1")
    SmsFeeStatistics getRecord(@Param("orgId") long orgId, @Param("channel") String channel, @Param("statisticsTime") long statisticsTime, @Param("priceUnit") String priceUnit);
}
