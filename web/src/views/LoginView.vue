<template>
  <div class="login-page">
    <!-- 背景装饰光斑 -->
    <div class="bg-blob blob-1"></div>
    <div class="bg-blob blob-2"></div>
    <div class="bg-blob blob-3"></div>

    <div class="login-card glass-card">
      <div class="brand">
        <div class="brand-logo">学</div>
        <div>
          <h1 class="brand-title">高校学情预测分析系统</h1>
          <p class="brand-sub">基于 Ollama 本地大模型 · 学业预测与预警</p>
        </div>
      </div>

      <el-radio-group v-model="form.loginType" class="entry-group" size="large">
        <el-radio-button value="TEACHER">教师端</el-radio-button>
        <el-radio-button value="STUDENT">学生端</el-radio-button>
        <el-radio-button value="PARENT">家长端</el-radio-button>
      </el-radio-group>

      <div class="entry-desc">
        <div class="desc-title">{{ entry.title }}可使用的功能</div>
        <ul class="desc-list">
          <li v-for="(f, i) in entry.functions" :key="i">{{ f }}</li>
        </ul>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" :placeholder="entry.accountHint" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>

      <div class="register-entry">
        <span>没有账号？</span>
        <el-link type="primary" :underline="false" @click="goRegister">学生 / 家长自助注册</el-link>
      </div>

      <el-alert
        class="login-tip"
        type="warning"
        :closable="false"
        :description="entry.demoTip"
      />
    </div>

    <div class="platform-tip">Web · 桌面端 · 移动端 三端功能互通</div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { homePathOf } from '@/router'

const ENTRIES = {
  TEACHER: {
    title: '教师 / 辅导员 / 管理者',
    accountHint: '请输入工号或管理员账号',
    functions: [
      '学情总览、学生名单与成绩管理',
      '学业预测、学业预警与干预闭环',
      '学生画像、班级学情与试卷分析',
      '学业指标（GPA / 欠学分 / 趋势）',
      '预警治理与数据导出（按层级开放）'
    ],
    demoTip: '演示账号：admin / admin123（系统管理员，全部功能）；教师账号为工号，初始密码 123456'
  },
  STUDENT: {
    title: '在校学生',
    accountHint: '请输入学号',
    functions: [
      '我的学情：本人成绩、学业指标与趋势',
      '本人的学业预警提示与建议行动',
      '通知中心（仅本人通知）'
    ],
    demoTip: '演示账号：学号（如 2021001），初始密码 123456；学生端不提供他人数据、班级排名与干预工单'
  },
  PARENT: {
    title: '学生家长',
    accountHint: '请输入家长账号（学号 + P1）',
    functions: [
      '家校协同：已绑定子女的学业概况',
      '子女的预警提示与给家长的建议',
      '对学校预警与帮扶措施提交回执',
      '通知中心（仅本人通知）'
    ],
    demoTip: '演示账号：学号 + P1（如 2021001P1），初始密码 123456；家长端不提供其他学生与内部处置流程'
  }
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const initialType = ['STUDENT', 'PARENT'].includes(route.query.type) ? route.query.type : 'TEACHER'
const form = reactive({ username: 'admin', password: 'admin123', loginType: initialType })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const entry = computed(() => ENTRIES[form.loginType] || ENTRIES.TEACHER)

function goRegister() {
  router.push('/register')
}

async function handleLogin() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(form.username, form.password, form.loginType)
      ElMessage.success('登录成功')
      router.push(route.query.redirect || homePathOf(userStore))
    } catch (e) {
      ElMessage.error(e.message || '登录失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  overflow: hidden;
  background: linear-gradient(135deg, #2f4bd0 0%, #4f7cff 45%, #7c5cff 100%);
}

/* 背景光斑 */
.bg-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.45;
  pointer-events: none;
}
.blob-1 { width: 420px; height: 420px; background: #7c5cff; top: -120px; left: -80px; }
.blob-2 { width: 380px; height: 380px; background: #00c2ff; bottom: -100px; right: -60px; }
.blob-3 { width: 260px; height: 260px; background: #ff9d6b; top: 40%; right: 18%; opacity: 0.25; }

/* 玻璃拟态卡片 */
.glass-card {
  position: relative;
  z-index: 1;
  width: 430px;
  padding: 34px 34px 26px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px);
  box-shadow: 0 20px 50px rgba(20, 40, 120, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.6);
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
}

.brand-logo {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: 800;
  color: #fff;
  background: linear-gradient(135deg, #4f7cff, #7c5cff);
  box-shadow: 0 8px 20px rgba(79, 124, 255, 0.35);
  flex-shrink: 0;
}

.brand-title {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  color: #1f2a44;
}

.brand-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #8a94a6;
}

.entry-group {
  display: flex;
  width: 100%;
  margin-bottom: 14px;
}

.entry-group :deep(.el-radio-button) {
  flex: 1;
}

.entry-group :deep(.el-radio-button__inner) {
  width: 100%;
}

.entry-desc {
  margin-bottom: 16px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #f3f7ff;
  border: 1px solid #dfe9ff;
}

.desc-title {
  font-size: 12px;
  font-weight: 600;
  color: #4f7cff;
}

.desc-list {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.8;
  color: #5b6472;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 4px;
  border-radius: 10px;
  background: linear-gradient(90deg, #4f7cff, #7c5cff);
  border: none;
}

.login-btn:hover {
  background: linear-gradient(90deg, #3f6cf0, #6b4cf0);
}

.register-entry {
  margin-top: 14px;
  text-align: center;
  font-size: 13px;
  color: #606266;
}

.login-tip {
  margin-top: 14px;
}

.platform-tip {
  position: relative;
  z-index: 1;
  margin-top: 18px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  letter-spacing: 1px;
}

@media (max-width: 768px) {
  .login-card {
    width: 92%;
    max-width: 420px;
    padding: 26px 22px 20px;
  }
}
</style>
