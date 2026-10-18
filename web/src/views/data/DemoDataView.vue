<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span>演示数据生成</span>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        description="一键生成学院、专业、班级、学期、课程、教师、学生、成绩、考勤与在线学习行为数据，用于毕设演示与自动化测试，避免依赖真实校方数据。"
        style="margin-bottom: 16px"
      />

      <el-form inline>
        <el-form-item label="学生数量">
          <el-input-number v-model="studentCount" :min="10" :max="2000" :step="50" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="generating" @click="handleGenerate">生成数据</el-button>
          <el-button type="danger" plain :loading="clearing" @click="handleClear">清空数据</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="Object.keys(summary).length" :column="3" border style="margin-top: 16px">
        <el-descriptions-item v-for="(v, k) in summary" :key="k" :label="TABLE_LABELS[k] || k">
          {{ v }} 条
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateDemo, clearDemo } from '@/api/demo'

const TABLE_LABELS = {
  college: '学院',
  major: '专业',
  clazz: '班级',
  term: '学期',
  course: '课程',
  courseOffering: '开课',
  student: '学生',
  score: '成绩',
  attendance: '考勤',
  onlineBehavior: '在线行为'
}

const studentCount = ref(300)
const generating = ref(false)
const clearing = ref(false)
const summary = ref({})

async function handleGenerate() {
  generating.value = true
  try {
    summary.value = await generateDemo(studentCount.value)
    ElMessage.success('演示数据生成成功')
  } finally {
    generating.value = false
  }
}

async function handleClear() {
  await ElMessageBox.confirm('确认清空全部演示数据？该操作不可恢复。', '警告', { type: 'warning' })
  clearing.value = true
  try {
    await clearDemo()
    summary.value = {}
    ElMessage.success('演示数据已清空')
  } finally {
    clearing.value = false
  }
}
</script>
