<template>
  <div class="register-page">
    <el-card class="register-card" shadow="always">
      <template #header>
        <div class="register-header">
          <h2>注册账号</h2>
          <p>高校学情预测分析系统 · 学生 / 家长自助注册</p>
        </div>
      </template>

      <el-radio-group v-model="form.registerType" class="entry-group" size="large">
        <el-radio-button value="STUDENT">学生注册</el-radio-button>
        <el-radio-button value="PARENT">家长注册</el-radio-button>
      </el-radio-group>

      <el-alert :closable="false" type="info" class="entry-desc" :title="verifyTip" />

      <el-form ref="formRef" :model="form" :rules="rules" size="large" label-position="top">
        <el-form-item label="学生学号" prop="studentNo">
          <el-input v-model="form.studentNo" placeholder="请输入学生学号" clearable />
        </el-form-item>
        <el-form-item label="学生姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入学生姓名（与学校档案一致）" clearable />
        </el-form-item>
        <el-form-item label="学生手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入学生手机号（与学校档案一致）" clearable />
        </el-form-item>

        <template v-if="form.registerType === 'PARENT'">
          <el-form-item label="家长姓名" prop="parentName">
            <el-input v-model="form.parentName" placeholder="请输入家长姓名" clearable />
          </el-form-item>
          <el-form-item label="与学生的关系" prop="relation">
            <el-radio-group v-model="form.relation">
              <el-radio value="FATHER">父亲</el-radio>
              <el-radio value="MOTHER">母亲</el-radio>
              <el-radio value="GUARDIAN">监护人</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="家长联系电话" prop="parentPhone">
            <el-input v-model="form.parentPhone" placeholder="请输入家长联系电话（选填）" clearable />
          </el-form-item>
        </template>

        <el-form-item label="设置登录密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="6-32 位密码" show-password clearable />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" show-password clearable />
        </el-form-item>

        <el-button type="primary" class="register-btn" :loading="loading" @click="handleRegister">
          注 册
        </el-button>
      </el-form>

      <div class="register-foot">
        <span>已有账号？</span>
        <el-link type="primary" :underline="false" @click="goLogin">返回登录</el-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register as registerApi } from '@/api/auth'

const router = useRouter()

const formRef = ref()
const loading = ref(false)
const form = reactive({
  registerType: 'STUDENT',
  studentNo: '',
  name: '',
  phone: '',
  parentName: '',
  relation: 'FATHER',
  parentPhone: '',
  password: '',
  confirmPassword: ''
})

const validateConfirm = (rule, value, callback) => {
  if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}
const validatePhone = (rule, value, callback) => {
  if (!value) { callback(); return }
  if (!/^1\d{10}$/.test(value.trim())) callback(new Error('请输入正确的 11 位手机号'))
  else callback()
}

const rules = computed(() => {
  const base = {
    studentNo: [{ required: true, message: '请输入学生学号', trigger: 'blur' }],
    name: [{ required: true, message: '请输入学生姓名', trigger: 'blur' }],
    phone: [
      { required: true, message: '请输入学生手机号', trigger: 'blur' },
      { validator: validatePhone, trigger: 'blur' }
    ],
    password: [
      { required: true, message: '请设置登录密码', trigger: 'blur' },
      { min: 6, max: 32, message: '密码长度需为 6-32 位', trigger: 'blur' }
    ],
    confirmPassword: [
      { required: true, message: '请再次输入密码', trigger: 'blur' },
      { validator: validateConfirm, trigger: 'blur' }
    ]
  }
  if (form.registerType === 'PARENT') {
    base.parentName = [{ required: true, message: '请输入家长姓名', trigger: 'blur' }]
    base.parentPhone = [{ validator: validatePhone, trigger: 'blur' }]
  }
  return base
})

const verifyTip = computed(() =>
  form.registerType === 'STUDENT'
    ? '按学校档案核验：学号 + 姓名 + 手机号须与在校档案一致，核验通过后即开通学生账号'
    : '按学生档案核验：填写学生的学号 + 姓名 + 手机号，核验通过后开通家长账号并绑定为该学生主监护人'
)

async function handleRegister() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const payload = {
        registerType: form.registerType,
        studentNo: form.studentNo.trim(),
        name: form.name.trim(),
        phone: form.phone.trim(),
        parentName: form.parentName?.trim(),
        relation: form.registerType === 'PARENT' ? form.relation : undefined,
        parentPhone: form.parentPhone?.trim(),
        password: form.password
      }
      const res = await registerApi(payload)
      ElMessage.success(res.message || '注册成功，请使用新账号登录')
      setTimeout(() => {
        router.push({ path: '/login', query: { type: form.registerType } })
      }, 1500)
    } catch (e) {
      ElMessage.error(e.message || '注册失败')
    } finally {
      loading.value = false
    }
  })
}

function goLogin() {
  router.push('/login')
}
</script>

<style scoped>
.register-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #2f6fed 0%, #1f2b48 100%);
}

.register-card {
  width: 440px;
  border-radius: 12px;
}

.register-header {
  text-align: center;
}

.register-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.register-header p {
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

.register-btn {
  width: 100%;
  margin-top: 4px;
}

.register-foot {
  margin-top: 14px;
  text-align: center;
  font-size: 13px;
  color: #606266;
}

@media (max-width: 768px) {
  .register-card {
    width: 92%;
  }
}
</style>
