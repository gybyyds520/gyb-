package com.gyb.xqfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.College;
import com.gyb.xqfx.entity.Major;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CollegeMapper;
import com.gyb.xqfx.mapper.MajorMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.model.query.StudentQuery;
import com.gyb.xqfx.model.vo.StudentVO;
import com.gyb.xqfx.service.StudentService;
import com.gyb.xqfx.util.MaskUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学生服务实现
 */
@Service
@RequiredArgsConstructor
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {

    private final ClazzMapper clazzMapper;
    private final MajorMapper majorMapper;
    private final CollegeMapper collegeMapper;

    @Override
    public IPage<StudentVO> pageStudents(long current, long size, StudentQuery query, boolean masked,
                                         List<Long> studentIds) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        // 数据范围：限定可见学生（and 包裹，避免与 keyword 的 or 组合混淆优先级）
        if (studentIds != null && !studentIds.isEmpty()) {
            wrapper.and(w -> w.in(Student::getId, studentIds));
        }
        if (query != null) {
            wrapper.like(StringUtils.hasText(query.getKeyword()), Student::getStudentNo, query.getKeyword())
                    .or(StringUtils.hasText(query.getKeyword()))
                    .like(StringUtils.hasText(query.getKeyword()), Student::getName, query.getKeyword());
            wrapper.eq(query.getCollegeId() != null, Student::getCollegeId, query.getCollegeId());
            wrapper.eq(query.getMajorId() != null, Student::getMajorId, query.getMajorId());
            wrapper.eq(query.getClazzId() != null, Student::getClazzId, query.getClazzId());
            wrapper.eq(StringUtils.hasText(query.getGrade()), Student::getGrade, query.getGrade());
            wrapper.eq(StringUtils.hasText(query.getStatus()), Student::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(Student::getStudentNo);

        Page<Student> page = this.page(new Page<>(current, size), wrapper);

        // 批量加载组织名称，避免 N+1
        Map<Long, String> clazzNames = clazzMapper.selectBatchIds(distinctIds(page.getRecords(), Student::getClazzId))
                .stream().collect(Collectors.toMap(Clazz::getId, Clazz::getClazzName, (a, b) -> a));
        Map<Long, String> majorNames = majorMapper.selectBatchIds(distinctIds(page.getRecords(), Student::getMajorId))
                .stream().collect(Collectors.toMap(Major::getId, Major::getMajorName, (a, b) -> a));
        Map<Long, String> collegeNames = collegeMapper.selectBatchIds(distinctIds(page.getRecords(), Student::getCollegeId))
                .stream().collect(Collectors.toMap(College::getId, College::getCollegeName, (a, b) -> a));

        List<StudentVO> vos = page.getRecords().stream().map(s -> toVO(s, clazzNames, majorNames, collegeNames, masked))
                .collect(Collectors.toList());

        Page<StudentVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public Student getByStudentNo(String studentNo) {
        return this.getOne(new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo));
    }

    @Override
    public Map<String, Long> mapStudentNoToId(List<String> studentNos) {
        if (studentNos == null || studentNos.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Student> list = this.list(new LambdaQueryWrapper<Student>().in(Student::getStudentNo, studentNos));
        return list.stream().collect(Collectors.toMap(Student::getStudentNo, Student::getId, (a, b) -> a));
    }

    private StudentVO toVO(Student s, Map<Long, String> clazzNames,
                           Map<Long, String> majorNames, Map<Long, String> collegeNames, boolean masked) {
        StudentVO vo = new StudentVO();
        BeanUtils.copyProperties(s, vo);
        vo.setClazzName(clazzNames.get(s.getClazzId()));
        vo.setMajorName(majorNames.get(s.getMajorId()));
        vo.setCollegeName(collegeNames.get(s.getCollegeId()));
        if (masked) {
            vo.setPhone(MaskUtils.maskPhone(s.getPhone()));
            vo.setPhoneMasked(MaskUtils.maskPhone(s.getPhone()));
            vo.setMasked(true);
        }
        return vo;
    }

    /**
     * 提取列表中非空的关联 ID（去重）
     */
    private List<Long> distinctIds(List<Student> students, Function<Student, Long> idGetter) {
        List<Long> ids = students.stream().map(idGetter).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? List.of() : ids;
    }
}
