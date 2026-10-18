package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.model.query.StudentQuery;
import com.gyb.xqfx.model.vo.StudentVO;

import java.util.List;

/**
 * 学生服务
 */
public interface StudentService extends IService<Student> {

    /**
     * 分页查询学生（带组织名称，可选脱敏）
     *
     * @param current 当前页
     * @param size    每页条数
     * @param query   查询条件
     * @param masked  是否脱敏
     */
    IPage<StudentVO> pageStudents(long current, long size, StudentQuery query, boolean masked);

    /**
     * 根据学号查询
     */
    Student getByStudentNo(String studentNo);

    /**
     * 批量按学号查询，返回 学号 -> id 映射
     */
    java.util.Map<String, Long> mapStudentNoToId(List<String> studentNos);
}
