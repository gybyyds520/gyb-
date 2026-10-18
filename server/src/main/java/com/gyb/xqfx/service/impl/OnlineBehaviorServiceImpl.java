package com.gyb.xqfx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gyb.xqfx.entity.OnlineBehavior;
import com.gyb.xqfx.mapper.OnlineBehaviorMapper;
import com.gyb.xqfx.service.OnlineBehaviorService;
import org.springframework.stereotype.Service;

/**
 * 在线学习行为服务实现
 */
@Service
public class OnlineBehaviorServiceImpl extends ServiceImpl<OnlineBehaviorMapper, OnlineBehavior>
        implements OnlineBehaviorService {
}
