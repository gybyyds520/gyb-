package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.entity.NotifyMessage;
import com.gyb.xqfx.entity.SysRole;
import com.gyb.xqfx.entity.SysUserRole;
import com.gyb.xqfx.entity.WarningRecord;
import com.gyb.xqfx.mapper.NotifyMessageMapper;
import com.gyb.xqfx.mapper.SysRoleMapper;
import com.gyb.xqfx.mapper.SysUserRoleMapper;
import com.gyb.xqfx.mapper.WarningRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 站内通知服务：预警通知推送、已读管理
 *
 * <p>预警生成后按角色（管理员、辅导员）推送站内消息，保证预警信息及时触达责任人。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    /** 接收预警通知的角色 */
    private static final List<String> WARNING_ROLES = List.of("ADMIN", "COUNSELOR");

    private final NotifyMessageMapper notifyMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final com.gyb.xqfx.mapper.StudentMapper studentMapper;

    /**
     * 查询指定角色的用户ID集合
     */
    public Set<Long> resolveUserIds(List<String> roleCodes) {
        Set<Long> userIds = new HashSet<>();
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleCode, roleCodes));
        for (SysRole role : roles) {
            List<SysUserRole> refs = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, role.getId()));
            refs.forEach(r -> userIds.add(r.getUserId()));
        }
        return userIds;
    }

    /**
     * 为指定学期的待处理预警推送通知（同一预警同一用户只推一次）
     *
     * @return 新增通知条数
     */
    public int syncFromWarnings(Long termId) {
        List<WarningRecord> warnings = warningRecordMapper.selectList(
                new LambdaQueryWrapper<WarningRecord>()
                        .eq(WarningRecord::getStatus, "ACTIVE")
                        .eq(termId != null, WarningRecord::getTermId, termId));
        if (warnings.isEmpty()) {
            return 0;
        }
        Set<Long> userIds = resolveUserIds(WARNING_ROLES);
        if (userIds.isEmpty()) {
            log.warn("未找到可接收预警通知的用户，请检查角色配置");
            return 0;
        }

        List<NotifyMessage> toInsert = new ArrayList<>();
        for (WarningRecord w : warnings) {
            for (Long userId : userIds) {
                long exists = notifyMapper.selectCount(new LambdaQueryWrapper<NotifyMessage>()
                        .eq(NotifyMessage::getUserId, userId)
                        .eq(NotifyMessage::getBizType, "WARNING")
                        .eq(NotifyMessage::getBizId, w.getId()));
                if (exists > 0) {
                    continue;
                }
                NotifyMessage msg = new NotifyMessage();
                msg.setUserId(userId);
                msg.setMessageType("WARNING");
                // 分级推送：等级越高渠道越多，文案由"告状"转为"关怀"
                msg.setTitle(caringTitle(w.getWarningLevel(), studentNameOf(w.getStudentId())));
                msg.setContent(caringContent(w.getWarningLevel(), studentNameOf(w.getStudentId()),
                        w.getTriggerDetail()));
                msg.setBizType("WARNING");
                msg.setBizId(w.getId());
                msg.setChannel(channelOf(w.getWarningLevel()));
                msg.setIsRead(0);
                msg.setCreateTime(LocalDateTime.now());
                msg.setUpdateTime(LocalDateTime.now());
                msg.setIsDeleted(0);
                toInsert.add(msg);
            }
        }
        toInsert.forEach(notifyMapper::insert);
        return toInsert.size();
    }

    /**
     * 分级推送渠道：低等级仅站内信，高等级叠加短信与企业微信
     *
     * <p>行业实践表明，渠道单一会导致重要预警被忽视，渠道无差别又会造成打扰，
     * 因此按预警等级分级选择触达方式。</p>
     */
    public String channelOf(Integer level) {
        return switch (level == null ? 0 : level) {
            case 3 -> "IN_APP+SMS";
            case 4 -> "IN_APP+SMS+WECOM";
            default -> "IN_APP";
        };
    }

    /**
     * 关怀化通知标题：强调"关注与帮助"而非"通报问题"
     */
    public String caringTitle(Integer level, String studentName) {
        String name = studentName == null || studentName.isBlank() ? "该同学" : studentName;
        return switch (level == null ? 0 : level) {
            case 1 -> "【关注】" + name + "同学近期学业状态有轻微波动";
            case 2 -> "【关注】" + name + "同学近期学业出现值得关注的变化";
            case 3 -> "【预警】" + name + "同学学业风险较高，建议尽快联系";
            case 4 -> "【预警】" + name + "同学学业风险突出，请立即介入";
            default -> "【提示】" + name + "同学有一条新的学业提示";
        };
    }

    /**
     * 关怀化通知正文：给出建议动作与响应时限，原始信号附后供核查
     */
    public String caringContent(Integer level, String studentName, String rawDetail) {
        String name = studentName == null || studentName.isBlank() ? "该同学" : studentName;
        String action = switch (level == null ? 0 : level) {
            case 1 -> "建议记录关注，暂不需主动联系";
            case 2 -> "建议在一周内与同学谈心了解情况";
            case 3 -> "建议在 24 小时内联系同学并填写沟通记录";
            case 4 -> "请立即响应，并同步联系院系学工负责人";
            default -> "建议关注";
        };
        String detail = rawDetail == null || rawDetail.isBlank() ? "" : "（识别到的信号：" + rawDetail + "）";
        return name + "同学近期在学习状态方面出现一些值得关注的波动，" + action + "。" + detail;
    }

    private String studentNameOf(Long studentId) {
        if (studentId == null) {
            return null;
        }
        com.gyb.xqfx.entity.Student s = studentMapper.selectById(studentId);
        return s == null ? null : s.getName();
    }

    /**
     * 发送系统通知给单个用户
     */
    public void sendToUser(Long userId, String title, String content, String bizType, Long bizId) {
        NotifyMessage msg = new NotifyMessage();
        msg.setUserId(userId);
        msg.setMessageType("SYSTEM");
        msg.setTitle(title);
        msg.setContent(content);
        msg.setBizType(bizType);
        msg.setBizId(bizId);
        msg.setChannel("IN_APP");
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        msg.setUpdateTime(LocalDateTime.now());
        msg.setIsDeleted(0);
        notifyMapper.insert(msg);
    }

    public List<NotifyMessage> listByUser(Long userId, Boolean unreadOnly) {
        return notifyMapper.selectList(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .eq(unreadOnly != null && unreadOnly, NotifyMessage::getIsRead, 0)
                .orderByDesc(NotifyMessage::getId));
    }

    public long unreadCount(Long userId) {
        return notifyMapper.selectCount(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, 0));
    }

    /**
     * 标记已读
     *
     * @return 是否更新成功
     */
    public boolean markRead(Long id, Long userId) {
        NotifyMessage msg = notifyMapper.selectById(id);
        if (msg == null || !msg.getUserId().equals(userId)) {
            return false;
        }
        msg.setIsRead(1);
        msg.setReadTime(LocalDateTime.now());
        return notifyMapper.updateById(msg) > 0;
    }

    /** 全部标记已读 */
    public int markAllRead(Long userId) {
        List<NotifyMessage> unread = notifyMapper.selectList(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, 0));
        unread.forEach(m -> {
            m.setIsRead(1);
            m.setReadTime(LocalDateTime.now());
            notifyMapper.updateById(m);
        });
        return unread.size();
    }

    private static String levelText(Integer level) {
        if (level == null) {
            return "蓝色";
        }
        return switch (level) {
            case 4 -> "红色";
            case 3 -> "橙色";
            case 2 -> "黄色";
            default -> "蓝色";
        };
    }
}
