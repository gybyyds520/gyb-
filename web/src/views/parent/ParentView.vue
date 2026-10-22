<template>
  <div class="page-container">
    <!-- 个人中心卡片（移动端专属，通用组件）：头像 + 身份 + 子女学情速览 -->
    <MobileProfileCard :stats="profileStats" sub="家长端 · 绑定子女学情" />

    <el-alert :closable="false" type="info" show-icon style="margin-bottom: 16px">
      <template #title>
        本页仅展示您已绑定子女的学业概况。其他学生信息、班级排名与学校内部处置流程不向家长开放；
        如对预警有疑问或需配合学校，请通过下方回执功能反馈。
      </template>
    </el-alert>

    <el-row :gutter="16">
      <el-col v-for="c in children" :key="c.studentId" :xs="24" :md="12">
        <el-card shadow="hover" class="child-card" :class="{ 'is-risk': c.riskFlag === 'RISK' }">
          <template #header>
            <div class="card-header">
              <span class="child-name">{{ c.studentName }}（{{ c.studentNo }}）</span>
              <el-button link type="primary" size="small" @click="selectChild(c)">查看详情</el-button>
            </div>
          </template>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="班级">{{ c.clazzName }}</el-descriptions-item>
            <el-descriptions-item label="学分绩点">
              <el-text :type="c.gpa >= 2 ? 'primary' : 'danger'">{{ c.gpa }}</el-text>
            </el-descriptions-item>
            <el-descriptions-item label="欠学分">
              <el-text :type="c.deficitCredit > 0 ? 'danger' : 'success'">{{ c.deficitCredit }}</el-text>
            </el-descriptions-item>
            <el-descriptions-item label="成绩趋势">
              {{ c.trendSlope > 0 ? '+' : '' }}{{ c.trendSlope }} 分/学期
            </el-descriptions-item>
            <el-descriptions-item label="进行中预警">
              <el-tag v-if="c.activeWarningCount" :type="c.topWarningLevel >= 3 ? 'danger' : 'warning'" size="small">
                {{ c.activeWarningCount }} 条
              </el-tag>
              <span v-else>无</span>
            </el-descriptions-item>
            <el-descriptions-item label="综合判定">
              <el-tag :type="riskTag(c.riskFlag)" size="small">{{ riskText(c.riskFlag) }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!children.length" description="尚未绑定子女，请联系辅导员开通家长账号" />

    <el-card v-if="detail" shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>{{ detail.studentName }} 的学业详情</span>
          <el-select v-model="termId" placeholder="全部学期" clearable size="small" style="width: 170px"
                     @change="loadDetail">
            <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
          </el-select>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :xs="12" :md="6">
          <el-card shadow="never" class="stat-card">
            <p class="stat-title">平均成绩</p>
            <p class="stat-value">{{ detail.avgScore }}</p>
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card shadow="never" class="stat-card">
            <p class="stat-title">课程门数</p>
            <p class="stat-value">{{ detail.scoreCount }}</p>
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card shadow="never" class="stat-card">
            <p class="stat-title">不及格门数</p>
            <p class="stat-value" :style="{ color: detail.failCount ? '#f56c6c' : '#67c23a' }">
              {{ detail.failCount }}
            </p>
          </el-card>
        </el-col>
        <el-col :xs="12" :md="6">
          <el-card shadow="never" class="stat-card">
            <p class="stat-title">缺勤次数</p>
            <p class="stat-value" :style="{ color: detail.absentCount >= 5 ? '#f56c6c' : '#303133' }">
              {{ detail.absentCount }}
            </p>
          </el-card>
        </el-col>
      </el-row>

      <el-divider content-position="left">学校提示</el-divider>
      <el-empty v-if="!detail.warnings?.length" description="学校暂无预警提示" :image-size="60" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="w in detail.warnings"
          :key="w.id"
          :type="levelColor(w.warningLevel)"
          :timestamp="formatTime(w.createTime)"
          placement="top"
        >
          <div class="warn-item">
            <el-tag :type="levelTag(w.warningLevel)" size="small">{{ levelText(w.warningLevel) }}</el-tag>
            <span class="warn-advice">{{ w.advice }}</span>
            <el-button link type="primary" size="small" @click="openFeedback(w)">回执</el-button>
          </div>
        </el-timeline-item>
      </el-timeline>

      <el-divider content-position="left">我的回执记录</el-divider>
      <el-table :data="detail.feedbacks || []" border stripe size="small">
        <el-table-column label="事项" width="100">
          <template #default="{ row }">{{ row.bizType === 'INTERVENTION' ? '帮扶措施' : '学业预警' }}</template>
        </el-table-column>
        <el-table-column label="回执" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="ackTag(row.ackStatus)">{{ ackText(row.ackStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="留言" min-width="160" show-overflow-tooltip />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- AI 学情解读（VIP2 会员功能） -->
    <el-card v-if="detail" shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>AI 学情解读</span>
          <el-button size="small" type="primary" :loading="aiLoading" @click="loadAiReport">
            生成解读（会员）
          </el-button>
        </div>
      </template>
      <div v-if="aiReport">
        <div class="practice-meta">
          <el-tag size="small" :type="aiReport.source === 'AI' ? 'success' : 'warning'">
            {{ aiReport.source === 'AI' ? '本地大模型生成' : '规则降级生成' }}
          </el-tag>
          <el-text v-if="aiReport.model" size="small" type="info">模型：{{ aiReport.model }}</el-text>
          <el-text v-if="aiReport.avgScore" size="small" type="info">平均分：{{ aiReport.avgScore }}</el-text>
        </div>
        <p class="ai-content">{{ aiReport.content }}</p>
      </div>
      <el-empty
        v-else
        description="开通会员后，可由本地大模型生成子女的学情解读与家庭陪伴建议"
        :image-size="60"
      />
    </el-card>

    <!-- 子女作业动态（作业帮式作业闭环家校联动） -->
    <el-card v-if="children.length" shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>子女作业动态</span>
          <el-button size="small" :loading="homeworkLoading" @click="loadParentHomework">刷新</el-button>
        </div>
      </template>
      <el-empty v-if="!homeworkList.length" description="暂未布置作业：教师布置后子女完成情况会显示在这里" :image-size="60" />
      <el-table v-else :data="homeworkList" border stripe size="small">
        <el-table-column prop="studentName" label="子女" width="110" />
        <el-table-column prop="title" label="作业" min-width="150" show-overflow-tooltip />
        <el-table-column prop="courseName" label="课程" min-width="90" />
        <el-table-column label="题数" width="70">
          <template #default="{ row }">{{ row.questionCount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.submitted" size="small" type="success">已提交</el-tag>
            <el-tag v-else-if="row.overdue" size="small" type="danger">已逾期</el-tag>
            <el-tag v-else size="small" type="warning">待完成</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="90">
          <template #default="{ row }">
            <span v-if="row.score !== null && row.score !== undefined">{{ row.score }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="150">
          <template #default="{ row }">{{ formatTime(row.deadline) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="feedbackDialog" title="家长回执" width="460px">
      <el-form label-width="80px">
        <el-form-item label="回执">
          <el-radio-group v-model="fbForm.ackStatus">
            <el-radio value="ACKNOWLEDGED">已阅知</el-radio>
            <el-radio value="SUPPORTED">愿意配合</el-radio>
            <el-radio value="OBJECTED">有异议</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="留言">
          <el-input v-model="fbForm.content" type="textarea" :rows="3" placeholder="请写给辅导员，便于家校协同" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { myChildren, childDetail, parentFeedback } from '@/api/portal'
import { parentHomework } from '@/api/homework'
import { childAiReport } from '@/api/vip'
import { listBase } from '@/api/base'
import MobileProfileCard from '@/components/MobileProfileCard.vue'

const router = useRouter()

const children = ref([])

/** 个人中心卡片：子女学情速览（绑定子女数 + 预警/风险汇总） */
const profileStats = computed(() => {
  const riskCount = children.value.filter((c) => c.riskFlag === 'RISK').length
  const warnCount = children.value.reduce((s, c) => s + (Number(c.activeWarningCount) || 0), 0)
  return [
    { label: '绑定子女', value: children.value.length },
    { label: '进行中预警', value: warnCount },
    { label: '风险关注', value: riskCount },
    { label: '子女成绩', value: children.value.length ? '已同步' : '-' }
  ]
})
const detail = ref(null)
const terms = ref([])
const termId = ref(null)
const currentStudentId = ref(null)

const aiReport = ref(null)
const aiLoading = ref(false)

const homeworkList = ref([])
const homeworkLoading = ref(false)

const feedbackDialog = ref(false)
const submitting = ref(false)
const fbForm = ref({ bizType: 'WARNING', bizId: null, ackStatus: 'ACKNOWLEDGED', content: '' })

function riskTag(flag) {
  return flag === 'RISK' ? 'danger' : flag === 'ATTENTION' ? 'warning' : 'info'
}

function riskText(flag) {
  return flag === 'RISK' ? '需重点关注' : flag === 'ATTENTION' ? '需关注' : '正常'
}

function levelText(level) {
  return ['未分级', '蓝色提示', '黄色关注', '橙色预警', '红色干预'][level || 0]
}

function levelTag(level) {
  return ['info', 'info', 'warning', 'warning', 'danger'][level || 0]
}

function levelColor(level) {
  return ['primary', 'primary', 'warning', 'warning', 'danger'][level || 0]
}

function ackText(s) {
  return { ACKNOWLEDGED: '已阅知', SUPPORTED: '愿意配合', OBJECTED: '有异议' }[s] || s
}

function ackTag(s) {
  return { ACKNOWLEDGED: 'info', SUPPORTED: 'success', OBJECTED: 'danger' }[s] || 'info'
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 16)
}

async function selectChild(c) {
  currentStudentId.value = c.studentId
  await loadDetail()
}

async function loadDetail() {
  if (!currentStudentId.value) return
  detail.value = await childDetail(currentStudentId.value, termId.value)
  aiReport.value = null
}

/** AI 学情解读：需要 VIP2；等级不足时引导前往会员中心 */
async function loadAiReport() {
  if (!currentStudentId.value) return
  aiLoading.value = true
  try {
    aiReport.value = await childAiReport(currentStudentId.value, termId.value)
  } catch (e) {
    ElMessage.warning(e.message || '该功能需要开通会员')
    router.push('/vip')
  } finally {
    aiLoading.value = false
  }
}

function openFeedback(w) {
  fbForm.value = { bizType: 'WARNING', bizId: w.id, ackStatus: 'ACKNOWLEDGED', content: '' }
  feedbackDialog.value = true
}

async function submitFeedback() {
  submitting.value = true
  try {
    await parentFeedback(currentStudentId.value, fbForm.value.bizType, fbForm.value.bizId,
      fbForm.value.ackStatus, fbForm.value.content)
    ElMessage.success('回执已提交，辅导员将看到您的反馈')
    feedbackDialog.value = false
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

async function loadParentHomework() {
  homeworkLoading.value = true
  try {
    homeworkList.value = await parentHomework()
  } catch (e) {
    ElMessage.warning(e.message || '作业动态加载失败')
  } finally {
    homeworkLoading.value = false
  }
}

onMounted(async () => {
  children.value = await myChildren(null)
  terms.value = await listBase('term')
  if (children.value.length === 1) {
    await selectChild(children.value[0])
  }
  loadParentHomework()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.practice-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.ai-content {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  color: #303133;
}

.child-card {
  margin-bottom: 16px;
}

.child-card.is-risk {
  border-left: 3px solid #f56c6c;
}

.child-name {
  font-weight: 600;
}

.stat-card {
  text-align: center;
}

.stat-title {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.stat-value {
  margin: 6px 0 0;
  font-size: 24px;
  font-weight: 700;
}

.warn-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.warn-advice {
  font-size: 13px;
  flex: 1;
}
</style>
