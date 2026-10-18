package com.gyb.xqfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gyb.xqfx.common.BusinessException;
import com.gyb.xqfx.common.ErrorCode;
import com.gyb.xqfx.entity.Clazz;
import com.gyb.xqfx.entity.CourseOffering;
import com.gyb.xqfx.entity.ImMessage;
import com.gyb.xqfx.entity.Score;
import com.gyb.xqfx.entity.Student;
import com.gyb.xqfx.entity.StudentParent;
import com.gyb.xqfx.entity.SysUser;
import com.gyb.xqfx.entity.Teacher;
import com.gyb.xqfx.mapper.ClazzMapper;
import com.gyb.xqfx.mapper.CourseOfferingMapper;
import com.gyb.xqfx.mapper.ImMessageMapper;
import com.gyb.xqfx.mapper.ScoreMapper;
import com.gyb.xqfx.mapper.StudentMapper;
import com.gyb.xqfx.mapper.StudentParentMapper;
import com.gyb.xqfx.mapper.SysUserMapper;
import com.gyb.xqfx.mapper.TeacherMapper;
import com.gyb.xqfx.security.SecurityUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 站内通讯：教师 ↔ 学生 ↔ 家长
 *
 * <p>通讯录按身份关系推导，不做全员可聊：</p>
 * <ul>
 *   <li>教师/辅导员：本人可见范围内的学生，以及这些学生的家长</li>
 *   <li>学生：本人家长、班级班主任与辅导员、本人选修课程的任课教师</li>
 *   <li>家长：本人子女，以及子女的班主任、辅导员与任课教师</li>
 * </ul>
 * 不在通讯录内的账号之间发送消息会被拒绝。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int MAX_CONTENT = 1000;
    private static final int DEFAULT_LIMIT = 100;

    private static final Set<String> STAFF_ROLES =
            Set.of("ADMIN", "SCHOOL_ADMIN", "COLLEGE_ADMIN", "COUNSELOR", "TEACHER");

    private final ImMessageMapper messageMapper;
    private final SysUserMapper userMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final StudentParentMapper studentParentMapper;
    private final CourseOfferingMapper offeringMapper;
    private final ScoreMapper scoreMapper;
    private final ClazzMapper clazzMapper;
    private final RoleService roleService;
    private final DataScopeService dataScopeService;
    private final NotifyService notifyService;

    // ==================== 通讯录 ====================

    /** 当前登录用户的通讯录 */
    public List<Contact> contacts() {
        return contactsOf(SecurityUtils.currentUserId());
    }

    /**
     * 推导某用户的通讯录：按身份关系给出可联系的账号
     */
    public List<Contact> contactsOf(Long userId) {
        if (userId == null) {
            return List.of();
        }
        Set<String> roles = roleService.roleCodesOf(userId);
        boolean isStudent = roles.contains("STUDENT");
        boolean isParent = roles.contains("PARENT");
        boolean isStaff = roles.stream().anyMatch(STAFF_ROLES::contains);

        Map<Long, Contact> map = new LinkedHashMap<>();
        if (isStaff) {
            collectForStaff(userId, map);
        }
        if (isStudent) {
            collectForStudent(userId, map);
        }
        if (isParent) {
            collectForParent(userId, map);
        }
        map.remove(userId);
        return new ArrayList<>(map.values());
    }

    /** 教师：可见范围内的学生 + 这些学生的家长 */
    private void collectForStaff(Long userId, Map<Long, Contact> out) {
        List<Long> studentIds = studentIdsOfStaff(userId);
        for (Long sid : studentIds) {
            Student s = studentMapper.selectById(sid);
            if (s == null) {
                continue;
            }
            Long uid = userIdOfStudent(s);
            if (uid != null) {
                out.putIfAbsent(uid, contact(uid, s.getName(), "STUDENT",
                        (s.getStudentNo() == null ? "" : s.getStudentNo()) + " 学生"));
            }
            // 该学生的家长
            for (StudentParent sp : parentLinksOf(sid)) {
                out.putIfAbsent(sp.getParentUserId(),
                        contact(sp.getParentUserId(), sp.getParentName(), "PARENT",
                                s.getName() + "的" + relationText(sp.getRelation())));
            }
        }
    }

    /** 学生：家长 + 班主任/辅导员 + 任课教师 */
    private void collectForStudent(Long userId, Map<Long, Contact> out) {
        Long studentId = roleService.studentIdOf(userId);
        if (studentId == null) {
            return;
        }
        Student me = studentMapper.selectById(studentId);
        String myName = me == null ? "本人" : me.getName();
        for (StudentParent sp : parentLinksOf(studentId)) {
            out.putIfAbsent(sp.getParentUserId(),
                    contact(sp.getParentUserId(), sp.getParentName(), "PARENT",
                            "我的" + relationText(sp.getRelation())));
        }
        for (Long teacherId : teacherIdsOfStudent(studentId)) {
            Teacher t = teacherMapper.selectById(teacherId);
            if (t == null || t.getUserId() == null) {
                continue;
            }
            out.putIfAbsent(t.getUserId(), contact(t.getUserId(), t.getName(), "TEACHER",
                    teacherLabel(teacherId, studentId, myName)));
        }
    }

    /** 家长：子女 + 子女的班主任/辅导员/任课教师 */
    private void collectForParent(Long userId, Map<Long, Contact> out) {
        for (Long sid : dataScopeService.childStudentIds(userId)) {
            Student s = studentMapper.selectById(sid);
            if (s == null) {
                continue;
            }
            Long uid = userIdOfStudent(s);
            if (uid != null) {
                out.putIfAbsent(uid, contact(uid, s.getName(), "STUDENT", "我的孩子"));
            }
            for (Long teacherId : teacherIdsOfStudent(sid)) {
                Teacher t = teacherMapper.selectById(teacherId);
                if (t == null || t.getUserId() == null) {
                    continue;
                }
                out.putIfAbsent(t.getUserId(), contact(t.getUserId(), t.getName(), "TEACHER",
                        teacherLabel(teacherId, sid, s.getName())));
            }
        }
    }

    /** 教师可见的学生ID（null 表示不限，此时取全部学生） */
    private List<Long> studentIdsOfStaff(Long userId) {
        List<Long> visible = dataScopeService.visibleStudentIds(userId);
        if (visible != null) {
            return visible;
        }
        return studentMapper.selectList(new LambdaQueryWrapper<Student>()
                        .eq(Student::getStatus, "ENROLLED"))
                .stream().map(Student::getId).toList();
    }

    /** 学生的家长绑定记录 */
    private List<StudentParent> parentLinksOf(Long studentId) {
        return studentParentMapper.selectList(new LambdaQueryWrapper<StudentParent>()
                .eq(StudentParent::getStudentId, studentId)
                .eq(StudentParent::getStatus, 1));
    }

    /**
     * 与学生相关的教师ID：班级班主任、辅导员，以及本人选修课程的任课教师
     */
    public Set<Long> teacherIdsOfStudent(Long studentId) {
        Set<Long> ids = new LinkedHashSet<>();
        Student s = studentMapper.selectById(studentId);
        if (s != null && s.getClazzId() != null) {
            Clazz c = clazzMapper.selectById(s.getClazzId());
            if (c != null) {
                if (c.getHeadTeacherId() != null) {
                    ids.add(c.getHeadTeacherId());
                }
                if (c.getCounselorId() != null) {
                    ids.add(c.getCounselorId());
                }
            }
        }
        List<Long> offeringIds = scoreMapper.selectList(
                        new LambdaQueryWrapper<Score>().eq(Score::getStudentId, studentId))
                .stream().map(Score::getCourseOfferingId)
                .filter(Objects::nonNull).distinct().toList();
        for (Long oid : offeringIds) {
            CourseOffering o = offeringMapper.selectById(oid);
            if (o != null && o.getTeacherId() != null) {
                ids.add(o.getTeacherId());
            }
        }
        return ids;
    }

    private Long userIdOfStudent(Student s) {
        if (s.getUserId() != null) {
            return s.getUserId();
        }
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStudentId, s.getId())
                .last("LIMIT 1"));
        return u == null ? null : u.getId();
    }

    private String teacherLabel(Long teacherId, Long studentId, String studentName) {
        Student s = studentMapper.selectById(studentId);
        if (s != null && s.getClazzId() != null) {
            Clazz c = clazzMapper.selectById(s.getClazzId());
            if (c != null) {
                if (teacherId.equals(c.getHeadTeacherId())) {
                    return studentName + "的班主任";
                }
                if (teacherId.equals(c.getCounselorId())) {
                    return studentName + "的辅导员";
                }
            }
        }
        return studentName + "的任课教师";
    }

    private String relationText(String relation) {
        if (relation == null) {
            return "家长";
        }
        return switch (relation) {
            case "FATHER" -> "父亲";
            case "MOTHER" -> "母亲";
            case "GUARDIAN" -> "监护人";
            default -> relation;
        };
    }

    private Contact contact(Long userId, String name, String role, String relation) {
        Contact c = new Contact();
        c.setUserId(userId);
        c.setName(name == null ? userMapper.selectById(userId).getRealName() : name);
        c.setRole(role);
        c.setRelation(relation);
        return c;
    }

    // ==================== 会话与消息 ====================

    /** 会话列表：按最近消息时间倒序，含未读数 */
    public List<Conversation> conversations() {
        return conversationsOf(SecurityUtils.currentUserId());
    }

    public List<Conversation> conversationsOf(Long me) {
        List<Conversation> list = new ArrayList<>();
        for (Map<String, Object> row : messageMapper.conversations(me)) {
            Long peerId = toLong(row.get("peerId"));
            if (peerId == null) {
                continue;
            }
            ImMessage last = messageMapper.lastMessage(me, peerId);
            Conversation c = new Conversation();
            c.setPeerId(peerId);
            SysUser peer = userMapper.selectById(peerId);
            c.setPeerName(peer == null ? "未知用户" : peer.getRealName());
            c.setPeerRole(peer == null ? null : peer.getUserType());
            c.setLastContent(last == null ? null : abbreviate(last.getContent()));
            c.setLastTime(last == null ? null : last.getCreateTime());
            c.setUnreadCount(toLong(row.get("unreadCount")) == null
                    ? 0 : toLong(row.get("unreadCount")).intValue());
            list.add(c);
        }
        return list;
    }

    /** 与某人的聊天记录（双向，按时间升序） */
    public List<MessageVO> history(Long peerUserId, Integer limit) {
        return historyOf(SecurityUtils.currentUserId(), peerUserId, limit);
    }

    public List<MessageVO> historyOf(Long me, Long peerUserId, Integer limit) {
        requireInContacts(me, peerUserId);
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 500);
        List<ImMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
                .and(w -> w.eq(ImMessage::getSenderId, me).eq(ImMessage::getReceiverId, peerUserId))
                .or(w -> w.eq(ImMessage::getSenderId, peerUserId).eq(ImMessage::getReceiverId, me))
                .orderByAsc(ImMessage::getCreateTime)
                .last("LIMIT " + size));
        List<MessageVO> list = new ArrayList<>();
        for (ImMessage m : rows) {
            list.add(toVO(m, me));
        }
        return list;
    }

    /**
     * 发送消息：接收人必须在通讯录内
     */
    @Transactional(rollbackFor = Exception.class)
    public MessageVO send(Long receiverUserId, String content) {
        return sendAs(SecurityUtils.currentUserId(), receiverUserId, content);
    }

    public MessageVO sendAs(Long senderUserId, Long receiverUserId, String content) {
        if (receiverUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择接收人");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息内容不能为空");
        }
        if (content.length() > MAX_CONTENT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息内容不能超过 " + MAX_CONTENT + " 字");
        }
        if (Objects.equals(senderUserId, receiverUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能给自己发消息");
        }
        requireInContacts(senderUserId, receiverUserId);

        ImMessage m = new ImMessage();
        m.setSenderId(senderUserId);
        m.setReceiverId(receiverUserId);
        m.setContent(content.trim());
        m.setIsRead(0);
        m.setCreateTime(LocalDateTime.now());
        m.setUpdateTime(LocalDateTime.now());
        m.setIsDeleted(0);
        messageMapper.insert(m);

        // 同步推送站内通知，接收人不在线也能看到提醒
        try {
            SysUser sender = userMapper.selectById(senderUserId);
            String from = sender == null ? "系统" : sender.getRealName();
            notifyService.sendToUser(receiverUserId, "来自 " + from + " 的消息",
                    abbreviate(content.trim()), "MESSAGE", m.getId());
        } catch (Exception e) {
            log.warn("消息通知推送失败：{}", e.getMessage());
        }
        return toVO(m, senderUserId);
    }

    /** 将与某人的会话标记为已读（只处理对方发给我的） */
    public int markRead(Long peerUserId) {
        return markReadOf(SecurityUtils.currentUserId(), peerUserId);
    }

    public int markReadOf(Long me, Long peerUserId) {
        requireInContacts(me, peerUserId);
        List<ImMessage> unread = messageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
                .eq(ImMessage::getSenderId, peerUserId)
                .eq(ImMessage::getReceiverId, me)
                .eq(ImMessage::getIsRead, 0));
        LocalDateTime now = LocalDateTime.now();
        for (ImMessage m : unread) {
            m.setIsRead(1);
            m.setReadTime(now);
            m.setUpdateTime(now);
            messageMapper.updateById(m);
        }
        return unread.size();
    }

    /** 未读消息总数 */
    public long unreadTotal() {
        return unreadTotalOf(SecurityUtils.currentUserId());
    }

    public long unreadTotalOf(Long userId) {
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<ImMessage>()
                .eq(ImMessage::getReceiverId, userId)
                .eq(ImMessage::getIsRead, 0));
        return count == null ? 0L : count;
    }

    /** 接收人是否在通讯录内 */
    public boolean canTalkTo(Long me, Long peerUserId) {
        return contactsOf(me).stream().anyMatch(c -> c.getUserId().equals(peerUserId));
    }

    private void requireInContacts(Long me, Long peerUserId) {
        if (!canTalkTo(me, peerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "对方不在你的通讯录中，无法发送消息");
        }
    }

    private MessageVO toVO(ImMessage m, Long me) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setSenderId(m.getSenderId());
        vo.setReceiverId(m.getReceiverId());
        vo.setContent(m.getContent());
        vo.setIsRead(m.getIsRead());
        vo.setCreateTime(m.getCreateTime());
        vo.setMine(Objects.equals(m.getSenderId(), me));
        SysUser sender = userMapper.selectById(m.getSenderId());
        vo.setSenderName(sender == null ? "未知" : sender.getRealName());
        SysUser receiver = userMapper.selectById(m.getReceiverId());
        vo.setReceiverName(receiver == null ? "未知" : receiver.getRealName());
        return vo;
    }

    private String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= 60 ? t : t.substring(0, 60) + "...";
    }

    private Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 内部类 ====================

    /** 通讯录条目 */
    @Data
    public static class Contact {
        private Long userId;
        private String name;
        /** STUDENT / TEACHER / PARENT */
        private String role;
        /** 关系说明，如「张明的父亲」「我的班主任」 */
        private String relation;
    }

    /** 会话列表条目 */
    @Data
    public static class Conversation {
        private Long peerId;
        private String peerName;
        private String peerRole;
        private String lastContent;
        private LocalDateTime lastTime;
        private Integer unreadCount;
    }

    /** 一条消息 */
    @Data
    public static class MessageVO {
        private Long id;
        private Long senderId;
        private String senderName;
        private Long receiverId;
        private String receiverName;
        private String content;
        private Integer isRead;
        private LocalDateTime createTime;
        /** 是否为当前登录用户发出 */
        private Boolean mine;
    }
}
