<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="login-header">
          <h2>高校学情预测分析系统</h2>
          <p>基于 Ollama 本地大模型 · 学业预测与预警</p>
        </div>
      </template>

      <el-radio-group v-model="form.loginType" class="entry-group" size="large">
        <el-radio-button value="TEACHER">教师端</el-radio-button>
        <el-radio-button value="STUDENT">学生端</el-radio-button>
        <el-radio-button value="PARENT">家长端</el-radio-button>
      </el-radio-group>

      <el-alert :closable="false" type="info" class="entry-desc">
        <template #title>
          <span class="desc-title">{{ entry.title }}可使用的功能</span>
          <ul class="desc-list">
            <li v-for="(f, i) in entry.functions" :key="i">{{ f }}</li>
          </ul>
        </template>
      </el-alert>

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

      <el-alert
        class="login-tip"
        type="warning"
        :closable="false"
        :description="entry.demoTip"
      />
    </el-card>
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
const form = reactive({ username: 'admin', password: 'admin123', loginType: 'TEACHER' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const entry = computed(() => ENTRIES[form.loginType] || ENTRIES.TEACHER)

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
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #2f6fed 0%, #1f2b48 100%);
}

.login-card {
  width: 420px;
  border-radius: 12px;
}

.login-header {
  text-align: center;
}

.login-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.login-header p {
  margin: 0;
  font-size: 12px;
  color: #909399;
}

.entry-group {
  display: flex;
  width: 100%;
  margin-bottom: 12px;
}

.entry-group :deep(.el-radio-button) {
  flex: 1;
}

.entry-group :deep(.el-radio-button__inner) {
  width: 100%;
}

.entry-desc {
  margin-bottom: 14px;
}

.desc-title {
  font-size: 12px;
}

.desc-list {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.7;
  color: #606266;
}

.login-btn {
  width: 100%;
}

.login-tip {
  margin-top: 14px;
}

@media (max-width: 768px) {
  .login-card {
    width: 92%;
  }
}
</style>
