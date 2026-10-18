<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="学生">
          <el-select v-model="studentId" filterable remote :remote-method="searchStudent" placeholder="输入学号或姓名"
                     style="width: 240px">
            <el-option v-for="s in students" :key="s.id" :label="s.studentNo + ' ' + s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="termId" placeholder="请选择" clearable style="width: 200px">
            <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :disabled="!studentId" @click="loadProfile">查看画像</el-button>
          <el-button type="success" :loading="generating" :disabled="!studentId" @click="doGenerate">重新生成</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="profile" :column="4" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="学号">{{ profile.studentNo }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ profile.studentName }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ profile.clazzName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="快照日期">{{ profile.snapshotDate || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-row :gutter="16" v-if="profile">
        <el-col :span="12">
          <div ref="radarRef" style="height: 360px"></div>
        </el-col>
        <el-col :span="12">
          <div ref="trendRef" style="height: 360px"></div>
        </el-col>
      </el-row>

      <el-empty v-else description="请选择学生查看画像" />

      <div v-if="profile" class="tag-area">
        <span class="tag-title">画像标签：</span>
        <el-tag v-for="t in profile.tags" :key="t.tagCode" size="small" :color="t.color"
                style="color:#fff; margin-right:6px">
          {{ t.tagName }}<span v-if="t.source === 'AUTO'">（自动）</span>
        </el-tag>
        <span v-if="!profile.tags || !profile.tags.length" class="no-tag">暂无标签</span>
      </div>

      <!-- 成长档案时间轴：画像快照 + 预警/干预/成绩/考勤事件 -->
      <div v-if="profile" class="timeline-area">
        <div class="timeline-title">成长档案时间轴（学生数字档案）</div>
        <el-empty v-if="!timelineItems.length" description="暂无档案事件" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="item in timelineItems"
            :key="item.key"
            :type="timelineType(item.eventType)"
            :hollow="item.eventType === 'SNAPSHOT'"
            :timestamp="formatTime(item.eventTime)"
            placement="top">
            <el-card shadow="never" class="timeline-card">
              <div class="timeline-head">
                <el-tag size="small" :type="timelineType(item.eventType)" effect="dark">{{ item.eventTypeText }}</el-tag>
                <span class="timeline-title-text">{{ item.title }}</span>
                <el-tag v-if="item.severity === 'SEVERE'" size="small" type="danger" effect="plain">严重</el-tag>
                <el-tag v-else-if="item.severity === 'ATTENTION'" size="small" type="warning" effect="plain">关注</el-tag>
              </div>
              <div class="timeline-detail">{{ item.detail }}</div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
      <!-- 偏科分析：强弱项对比 -->
      <div v-if="profile" class="timeline-area">
        <div class="timeline-title">
          偏科分析（强弱项对比）
          <el-tag v-if="bias" size="small" type="info" effect="plain" style="margin-left: 8px">
            对比基准：{{ baselineText(bias.baseline) }}
          </el-tag>
          <el-button size="small" type="warning" plain style="margin-left: 10px" :loading="biasAttrLoading" @click="doBiasAttribution">
            AI 归因解读
          </el-button>
        </div>
        <el-row v-if="bias" :gutter="16" class="bias-summary">
          <el-col :span="8">
            <div class="bias-card strong">强科 {{ bias.strongCount || 0 }} 门</div>
          </el-col>
          <el-col :span="8">
            <div class="bias-card weak">弱科 {{ bias.weakCount || 0 }} 门</div>
          </el-col>
          <el-col :span="8">
            <div class="bias-card neutral">偏科指数 {{ bias.biasIndex ?? 0 }}</div>
          </el-col>
        </el-row>
        <el-table v-if="bias && bias.items && bias.items.length" :data="bias.items" border stripe size="small">
          <el-table-column prop="courseName" label="课程" min-width="140" show-overflow-tooltip />
          <el-table-column prop="courseType" label="类型" width="100">
            <template #default="{ row }">{{ courseTypeText(row.courseType) }}</template>
          </el-table-column>
          <el-table-column prop="studentAvg" label="学生均分" width="90" />
          <el-table-column prop="baselineAvg" label="基准均分" width="90" />
          <el-table-column label="差值" width="90">
            <template #default="{ row }">
              <span :class="row.diff >= 0 ? 'diff-pos' : 'diff-neg'">
                {{ row.diff >= 0 ? '+' : '' }}{{ row.diff }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="rankPercent" label="百分位" width="90">
            <template #default="{ row }">{{ row.rankPercent ?? '-' }}%</template>
          </el-table-column>
          <el-table-column label="判定" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.verdict === 'STRONG' ? 'success' : (row.verdict === 'WEAK' ? 'danger' : 'info')" effect="plain">
                {{ verdictText(row.verdict) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="advice" label="建议" min-width="200" show-overflow-tooltip />
        </el-table>
        <el-empty v-else-if="bias" description="暂无课程成绩，无法进行偏科分析" :image-size="60" />
      </div>
    </el-card>

    <!-- 偏科 AI 归因 -->
    <el-dialog v-model="biasAttrVisible" title="偏科 AI 归因" width="640px">
      <div v-loading="biasAttrLoading" class="attr-body">
        <el-empty v-if="!biasAttrLoading && !biasAttrContent" description="点击「AI 归因解读」生成强弱项成因分析" :image-size="60" />
        <div v-if="biasAttrContent" class="attr-content">{{ biasAttrContent }}</div>
        <div v-if="biasAttrModel" class="attr-model">模型：{{ biasAttrModel }}</div>
      </div>
      <template #footer>
        <el-button @click="biasAttrVisible = false">关闭</el-button>
        <el-button type="primary" :loading="biasAttrLoading" @click="doBiasAttribution">重新生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getProfile, generateProfile, getSnapshots, getBias, biasAttribution } from '@/api/profile'
import { warningTimeline } from '@/api/warning'
import { pageStudents } from '@/api/student'
import { listBase } from '@/api/base'

const DIM_LABELS = ['学业水平', '学习投入', '行为表现', '发展潜力', '风险指数']
const EVENT_TYPE_TEXT = {
  SNAPSHOT: '画像快照',
  WARNING: '预警',
  INTERVENTION: '干预',
  SCORE: '成绩',
  ATTENDANCE: '考勤'
}
const EVENT_TYPE_TAG = {
  SNAPSHOT: 'primary',
  WARNING: 'danger',
  INTERVENTION: 'success',
  SCORE: 'danger',
  ATTENDANCE: 'warning'
}

const studentId = ref(null)
const termId = ref(null)
const students = ref([])
const terms = ref([])
const profile = ref(null)
const generating = ref(false)
const radarRef = ref(null)
const trendRef = ref(null)
const timelineItems = ref([])
const bias = ref(null)
const biasAttrVisible = ref(false)
const biasAttrLoading = ref(false)
const biasAttrContent = ref('')
const biasAttrModel = ref('')
let radarChart = null
let trendChart = null

const num = (v) => (v === null || v === undefined ? 0 : Number(v))
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-')
const timelineType = (t) => EVENT_TYPE_TAG[t] || 'info'
const timelineTypeText = (t) => EVENT_TYPE_TEXT[t] || t || '事件'
const baselineText = (b) => ({ CLAZZ: '同班同学', MAJOR: '同专业', ALL: '全校' })[b] || b || '-'
const verdictText = (v) => ({ STRONG: '强科', WEAK: '弱科', NORMAL: '均衡' })[v] || v || '-'
const courseTypeText = (t) => ({ PUBLIC: '公共课', CORE: '核心课', MAJOR: '专业课', ELECTIVE: '选修' })[t] || t || '-'

async function searchStudent(kw) {
  const data = await pageStudents({ current: 1, size: 20, keyword: kw })
  students.value = data.records
}

async function loadProfile() {
  profile.value = await getProfile(studentId.value, termId.value)
  renderCharts()
  await Promise.all([loadTimeline(), loadBias()])
}

/** 加载偏科分析 */
async function loadBias() {
  try {
    bias.value = await getBias(studentId.value, termId.value)
  } catch {
    bias.value = null
  }
}

/** 偏科 AI 归因 */
async function doBiasAttribution() {
  if (!studentId.value) return
  biasAttrLoading.value = true
  try {
    const res = await biasAttribution(studentId.value, termId.value)
    biasAttrContent.value = res?.content || '（无返回内容）'
    biasAttrModel.value = res?.model || ''
    biasAttrVisible.value = true
  } finally {
    biasAttrLoading.value = false
  }
}

/** 加载成长档案时间轴：画像快照 + 预警/干预/成绩/考勤事件，按时间倒序合并 */
async function loadTimeline() {
  timelineItems.value = []
  const [snapshots, events] = await Promise.all([
    getSnapshots(studentId.value),
    warningTimeline(studentId.value)
  ])
  const items = []
  ;(snapshots || []).forEach((s, i) => {
    items.push({
      key: 'SNAPSHOT-' + s.id,
      eventTime: s.snapshotDate ? s.snapshotDate + ' 00:00:00' : '',
      eventType: 'SNAPSHOT',
      eventTypeText: '画像快照',
      title: '画像快照（' + (i + 1) + '）',
      detail: `学业 ${num(s.academicScore)} · 投入 ${num(s.engagementScore)} · 行为 ${num(s.behaviorScore)} · 潜力 ${num(s.potentialScore)} · 风险 ${num(s.riskScore)}`,
      severity: num(s.riskScore) >= 70 ? 'SEVERE' : (num(s.riskScore) >= 45 ? 'ATTENTION' : 'NORMAL')
    })
  })
  ;(events || []).forEach((e) => {
    items.push({
      key: e.eventType + '-' + e.refId + '-' + e.eventTime,
      eventTime: e.eventTime,
      eventType: e.eventType,
      eventTypeText: e.eventTypeText || timelineTypeText(e.eventType),
      title: e.title,
      detail: e.detail || '',
      severity: e.severity || 'NORMAL'
    })
  })
  items.sort((a, b) => String(b.eventTime).localeCompare(String(a.eventTime)))
  timelineItems.value = items
}

async function doGenerate() {
  generating.value = true
  try {
    await generateProfile(studentId.value, termId.value)
    ElMessage.success('画像已生成')
    await loadProfile()
  } finally {
    generating.value = false
  }
}

function renderCharts() {
  nextTick(() => {
    if (!profile.value) return
    const values = [
      num(profile.value.academicScore),
      num(profile.value.engagementScore),
      num(profile.value.behaviorScore),
      num(profile.value.potentialScore),
      num(profile.value.riskScore)
    ]

    radarChart = radarChart || (radarRef.value && echarts.init(radarRef.value))
    radarChart && radarChart.setOption({
      title: { text: '五维画像', textStyle: { fontSize: 14 } },
      tooltip: {},
      radar: {
        indicator: DIM_LABELS.map((name) => ({ name, max: 100 })),
        radius: '65%'
      },
      series: [{
        type: 'radar',
        data: [{ value: values, name: '画像得分', areaStyle: { opacity: 0.2 } }]
      }]
    })

    const trend = profile.value.trend || {}
    trendChart = trendChart || (trendRef.value && echarts.init(trendRef.value))
    trendChart && trendChart.setOption({
      title: { text: '历史趋势', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      legend: { data: ['学业水平', '学习投入', '行为表现', '风险指数'], top: 24 },
      grid: { left: 40, right: 20, top: 70, bottom: 30 },
      xAxis: { type: 'category', boundaryGap: false, data: (trend.academic || []).map((_, i) => '第' + (i + 1) + '次') },
      yAxis: { type: 'value', max: 100 },
      series: [
        { name: '学业水平', type: 'line', smooth: true, data: (trend.academic || []).map(num) },
        { name: '学习投入', type: 'line', smooth: true, data: (trend.engagement || []).map(num) },
        { name: '行为表现', type: 'line', smooth: true, data: (trend.behavior || []).map(num) },
        { name: '风险指数', type: 'line', smooth: true, data: (trend.risk || []).map(num) }
      ]
    })
  })
}

watch(profile, renderCharts)

onMounted(async () => {
  terms.value = await listBase('term')
  if (terms.value.length) termId.value = terms.value[terms.value.length - 1].id
  await searchStudent('')
})
</script>

<style scoped>
.tag-area {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.tag-title {
  font-size: 14px;
  color: #606266;
  margin-right: 8px;
}

.no-tag {
  font-size: 13px;
  color: #909399;
}

.timeline-area {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.timeline-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.timeline-card {
  border-radius: 6px;
}

.timeline-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.timeline-title-text {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.timeline-detail {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  line-height: 1.6;
  word-break: break-all;
}

.bias-summary {
  margin-bottom: 10px;
}

.bias-card {
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  text-align: center;
  color: #fff;
}

.bias-card.strong { background: #67c23a; }
.bias-card.weak { background: #f56c6c; }
.bias-card.neutral { background: #409eff; }

.diff-pos { color: #67c23a; font-weight: 600; }
.diff-neg { color: #f56c6c; font-weight: 600; }

.attr-body {
  min-height: 200px;
  max-height: 480px;
  overflow-y: auto;
}

.attr-content {
  font-size: 14px;
  line-height: 1.9;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.attr-model {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
