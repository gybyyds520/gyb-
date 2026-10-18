package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.model.query.ScoreQuery;
import com.gyb.xqfx.model.vo.ScoreVO;

/**
 * 成绩服务
 */
public interface ScoreService extends IService<Score> {

    /**
     * 分页查询成绩（含学生/课程/学期名称）
     *
     * @param studentIds 数据范围限定的学生ID（null 表示全量，由 DataScope 传入）
     */
    IPage<ScoreVO> pageScores(long current, long size, ScoreQuery query, java.util.List<Long> studentIds);
}
