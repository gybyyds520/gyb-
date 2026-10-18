<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item v-if="userStore.isStaff" label="学生">
          <el-select v-model="studentId" filterable remote :remote-method="searchStudent" placeholder="输入学号或姓名"
                     style="width: 220px">
            <el-option v-for="s in students" :key="s.id" :label="s.studentNo + ' ' + s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="termId" placeholder="全部学期" clearable style="width: 170px">
            <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="userStore.isStaff && !studentId" @click="loadGoals">查询</el-button>
          <el-button type="success" :disabled="userStore.isStaff && !studentId" @click="openDialog()">新建目标</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="userStore.isStudent" :closable="false" type="info" show-icon style="margin-bottom: 12px">
        <template #title>为自己设定学习目标（平均分 / 绩点 / 单科），系统将实时计算差距并生成资源推荐。</template>
      </el-alert>

      <el-table v-loading="loading" :data="goals" border stripe size="small">
        <el-table-column prop="goalTypeText" label="目标类型" width="110" />
        <el-table-column label="目标值" width="100">
          <template #default="{ row }"><b>{{ row.targetValue }}</b></template>
        </el-table-column>
        <el-table-column label="当前值" width="90">
          <template #default="{ row }">
            <span :class="row.gap > 0 ? 'diff-neg' : 'diff-pos'">{{ row.currentValue }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差距" width="80">
          <template #default="{ row }">
            <span :class="row.gap > 0 ? 'diff-neg' : 'diff-pos'">{{ row.gap > 0 ? '+' : '' }}{{ row.gap }}</span>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :stroke-width="10" :status="row.progress === 100 ? 'success' : ''" />
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="关联课程" width="120">
          <template #default="{ row }">{{ row.courseName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="截止日期" width="110">
          <template #default="{ row }">{{ row.deadline || '-' }}</template>
        </el-table-column>
        <el-table-column prop="note" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" size="small" type="success" plain @click="mark(row, 'ACHIEVED')">达成</el-button>
            <el-button v-if="row.status === 'ACTIVE'" size="small" type="warning" plain @click="mark(row, 'ABANDONED')">放弃</el-button>
            <el-button v-if="row.status !== 'ACTIVE'" size="small" @click="mark(row, 'ACTIVE')">恢复</el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !goals.length" description="暂无学习目标，点击「新建目标」开始设定" :image-size="60" />
    </el-card>

    <!-- 资源推荐 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>学习资源推荐</span>
          <el-button size="small" :loading="recLoading" :disabled="userStore.isStaff && !studentId" @click="loadRecs">
            重新生成
          </el-button>
        </div>
      </template>
      <el-empty v-if="!recs.length" description="基于偏科弱科与目标差距自动生成推荐" :image-size="60" />
      <el-row :gutter="12">
        <el-col v-for="r in recs" :key="r.courseId + r.resourceType" :xs="24" :md="12" :lg="8">
          <el-card shadow="hover" class="rec-card">
            <div class="rec-head">
              <el-tag size="small" :type="recTag(r.resourceType)" effect="dark">{{ r.resourceTypeText }}</el-tag>
              <el-tag v-if="r.courseName" size="small" type="info" effect="plain">{{ r.courseName }}</el-tag>
            </div>
            <div class="rec-title">{{ r.title }}</div>
            <div class="rec-reason">{{ r.reason }}</div>
            <div class="rec-action">建议：{{ r.action }}</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 新建/编辑目标 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑目标' : '新建目标'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="目标类型">
          <el-select v-model="form.goalType" style="width: 100%">
            <el-option label="平均分目标" value="AVG_SCORE" />
            <el-option label="绩点目标" value="GPA" />
            <el-option label="单科成绩目标" value="COURSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标值">
          <el-input-number v-model="form.targetValue" :min="0" :max="100" :precision="1" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.goalType === 'COURSE'" label="目标课程">
          <el-select v-model="form.courseId" filterable placeholder="选择课程" style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="form.termId" placeholder="全部学期（累计目标）" clearable style="width: 100%">
            <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="form.deadline" type="date" value-format="YYYY-MM-DD" placeholder="选填" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { listGoals, saveGoal, updateGoalStatus, deleteGoal, getRecommendations } from '@/api/goal'
import { pageStudents } from '@/api/student'
import { listBase } from '@/api/base'

const userStore = useUserStore()
const studentId = ref(null)
const termId = ref(null)
const students = ref([])
const terms = ref([])
const courses = ref([])
const goals = ref([])
const recs = ref([])
const loading = ref(false)
const recLoading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const form = ref({})

const statusTag = (s) => ({ ACTIVE: 'primary', ACHIEVED: 'success', ABANDONED: 'info' })[s] || 'info'
const recTag = (t) => ({ COURSE: 'primary', VIDEO: 'warning', PRACTICE: 'success', BOOK: 'info', COACH: 'danger' })[t] || 'info'

async function searchStudent(kw) {
  const data = await pageStudents({ current: 1, size: 20, keyword: kw })
  students.value = data.records
}

async function loadGoals() {
  loading.value = true
  try {
    goals.value = await listGoals(userStore.isStaff ? studentId.value : null, termId.value)
  } finally {
    loading.value = false
  }
}

async function loadRecs() {
  recLoading.value = true
  try {
    recs.value = await getRecommendations(userStore.isStaff ? studentId.value : null, termId.value)
  } finally {
    recLoading.value = false
  }
}

function openDialog(row) {
  form.value = row
    ? { ...row }
    : { id: null, goalType: 'AVG_SCORE', targetValue: 80, courseId: null, termId: termId.value, deadline: null, note: '', status: 'ACTIVE' }
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    await saveGoal(userStore.isStaff ? studentId.value : null, form.value)
    ElMessage.success('目标已保存')
    dialogVisible.value = false
    await loadGoals()
  } finally {
    saving.value = false
  }
}

async function mark(row, status) {
  await updateGoalStatus(row.id, status)
  ElMessage.success('状态已更新')
  await loadGoals()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除该目标？', '提示', { type: 'warning' })
  await deleteGoal(row.id)
  ElMessage.success('已删除')
  await loadGoals()
}

watch(studentId, (v) => {
  if (userStore.isStaff && v) {
    loadGoals()
    loadRecs()
  }
})

onMounted(async () => {
  terms.value = await listBase('term')
  if (!userStore.isStudent && terms.value.length) termId.value = terms.value[terms.value.length - 1].id
  courses.value = await listBase('course')
  if (userStore.isStaff) {
    await searchStudent('')
  } else {
    await loadGoals()
    await loadRecs()
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rec-card {
  margin-bottom: 12px;
  border-radius: 8px;
}

.rec-head {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}

.rec-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}

.rec-reason {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 6px;
}

.rec-action {
  font-size: 12px;
  color: #409eff;
  line-height: 1.6;
}

.diff-pos { color: #67c23a; font-weight: 600; }
.diff-neg { color: #f56c6c; font-weight: 600; }
</style>
