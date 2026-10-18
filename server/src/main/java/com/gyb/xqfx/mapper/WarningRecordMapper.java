package com.gyb.xqfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gyb.xqfx.entity.WarningRecord;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface WarningRecordMapper extends BaseMapper<WarningRecord> {

    /**
     * 按预警等级统计数量（仅统计未解除的预警）
     */
    @Select("SELECT level AS level, COUNT(*) AS cnt " +
            "FROM warning_record " +
            "WHERE is_deleted = 0 AND status = 'ACTIVE' " +
            "GROUP BY level")
    List<Map<String, Object>> statByLevel();

    /**
     * 按班级统计预警人数
     */
    @Select("SELECT st.clazz_id AS clazzId, COUNT(*) AS cnt " +
            "FROM warning_record w JOIN student st ON st.id = w.student_id " +
            "WHERE w.is_deleted = 0 AND w.status = 'ACTIVE' " +
            "GROUP BY st.clazz_id")
    List<Map<String, Object>> countByClazz();
}
