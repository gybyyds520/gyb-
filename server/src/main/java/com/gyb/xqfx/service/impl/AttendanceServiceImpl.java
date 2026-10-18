package com.gyb.xqfx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gyb.xqfx.entity.Attendance;
import com.gyb.xqfx.mapper.AttendanceMapper;
import com.gyb.xqfx.service.AttendanceService;
import org.springframework.stereotype.Service;

/**
 * 考勤服务实现
 */
@Service
public class AttendanceServiceImpl extends ServiceImpl<AttendanceMapper, Attendance> implements AttendanceService {
}
