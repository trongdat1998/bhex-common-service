package io.bhex.base.common.mapper;

import io.bhex.base.common.entity.AppPushRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.common.Mapper;

import java.sql.Timestamp;
import java.util.List;

@Component
@org.apache.ibatis.annotations.Mapper
public interface AppPushRecordMapper  extends Mapper<AppPushRecord> {


    @Update("update tb_app_push_record set status = #{status} where id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") int status);


    @Select("select count(*) from tb_app_push_record where  org_id=#{orgId} and push_tokens=#{pushToken}  and biz_type=#{bizType} " +
            " and created_at between #{start} and #{end} and status = 1 ")
    int countByCreated(@Param("orgId") long orgId,
                       @Param("pushToken") String pushToken,
                       @Param("bizType")  String bizType,
                       @Param("start") Timestamp start,
                       @Param("end") Timestamp end);


    @Select("select * from tb_app_push_record where push_channel = #{pushChannel} and created_at between #{start} and #{end} and status = 0 limit 100")
    List<AppPushRecord> getUnPushedRecords(@Param("pushChannel") String pushChannel, @Param("start") Timestamp start, @Param("end") Timestamp end);


    @Select("select * from tb_app_push_record where push_channel = 'FCM' and biz_type = '' and created_at between #{start} and #{end} ")
    List<AppPushRecord> getFcmDeletedTopicRecords(@Param("start") Timestamp start, @Param("end") Timestamp end);

}
