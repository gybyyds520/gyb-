<template>
  <!-- 个人中心卡片（移动端专属，所有角色通用）：头像 + 身份 + 统计速览 -->
  <div class="profile-card mobile-only">
    <div class="pc-blob pc-blob-1"></div>
    <div class="pc-blob pc-blob-2"></div>
    <div class="pc-top">
      <div class="pc-avatar">{{ avatarChar }}</div>
      <div class="pc-id">
        <div class="pc-name">{{ displayName }}<span class="pc-role">{{ roleText }}</span></div>
        <div class="pc-no">{{ sub }}</div>
      </div>
    </div>
    <div v-if="stats.length" class="pc-stats">
      <div v-for="s in stats" :key="s.label" class="pc-stat">
        <div class="pc-stat-v">{{ s.value ?? '-' }}</div>
        <div class="pc-stat-l">{{ s.label }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  /** 统计速览：[{ label, value }]，最多建议 4 项 */
  stats: { type: Array, default: () => [] },
  /** 身份副行（学号/工号等）；不传时默认显示账号 */
  sub: { type: String, default: '' }
})

const userStore = useUserStore()
const displayName = computed(() => userStore.realName || '用户')
const roleText = computed(() => ({
  STUDENT: '学生', PARENT: '家长', TEACHER: '教师', COUNSELOR: '辅导员',
  ADMIN: '管理员', SCHOOL_ADMIN: '校级管理员', COLLEGE_ADMIN: '院系管理员'
}[userStore.userType] || userStore.userType || '用户'))
const avatarChar = computed(() => (displayName.value || '学').charAt(0))
const sub = computed(() => props.sub || (userStore.username ? '账号：' + userStore.username : ''))
</script>

<style scoped>
.profile-card {
  position: relative;
  overflow: hidden;
  border-radius: 16px;
  padding: 18px 16px 14px;
  margin-bottom: 14px;
  color: #fff;
  background: linear-gradient(135deg, #2f4bd0 0%, #4f7cff 45%, #7c5cff 100%);
  box-shadow: 0 8px 24px rgba(79, 124, 255, 0.28);
}

.pc-blob {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.pc-blob-1 {
  width: 130px;
  height: 130px;
  background: rgba(0, 194, 255, 0.35);
  top: -50px;
  right: -30px;
}

.pc-blob-2 {
  width: 90px;
  height: 90px;
  background: rgba(124, 92, 255, 0.45);
  bottom: -30px;
  left: 30%;
}

.pc-top {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
}

.pc-avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  color: #4f7cff;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  box-shadow: 0 2px 8px rgba(31, 45, 105, 0.18);
  flex-shrink: 0;
}

.pc-id {
  min-width: 0;
}

.pc-name {
  font-size: 18px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}

.pc-role {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.35);
}

.pc-no {
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.9;
}

.pc-stats {
  position: relative;
  margin-top: 14px;
  display: flex;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.pc-stat {
  flex: 1;
  text-align: center;
  padding: 10px 0;
}

.pc-stat + .pc-stat {
  border-left: 1px solid rgba(255, 255, 255, 0.25);
}

.pc-stat-v {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
}

.pc-stat-l {
  margin-top: 3px;
  font-size: 11px;
  opacity: 0.85;
}
</style>
