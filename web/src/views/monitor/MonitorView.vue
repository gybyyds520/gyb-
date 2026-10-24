<template>
  <div class="monitor">
    <div class="head">
      <div class="title">
        管理员监察中心
        <span class="badge">至高无上权限 · 可查看所有端全部信息与功能</span>
      </div>
      <div class="head-right">
        <el-switch v-model="autoRefresh" size="small" active-text="自动刷新" style="margin-right: 8px" />
        <el-button size="small" type="primary" :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- 三端 + 系统概览 -->
    <div class="kpi-row">
      <div class="kpi">
        <div class="kpi-title">学生端</div>
        <div class="kpi-line"><span>在校学生</span><b>{{ overview?.student?.studentCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>学习目标</span><b>{{ overview?.student?.goalCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>画像快照</span><b>{{ overview?.student?.profileCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>未读通知</span><b class="warn">{{ overview?.student?.unreadNotify ?? '-' }}</b></div>
      </div>
      <div class="kpi">
        <div class="kpi-title">教师端</div>
        <div class="kpi-line"><span>教师账号</span><b>{{ overview?.teacher?.teacherCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>待处理预警</span><b class="warn">{{ overview?.teacher?.activeWarning ?? '-' }}</b></div>
        <div class="kpi-line"><span>进行中工单</span><b>{{ overview?.teacher?.openTicket ?? '-' }}</b></div>
        <div class="kpi-line"><span>画像快照</span><b>{{ overview?.teacher?.snapshotCount ?? '-' }}</b></div>
      </div>
      <div class="kpi">
        <div class="kpi-title">家长端</div>
        <div class="kpi-line"><span>家长账号</span><b>{{ overview?.parent?.parentCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>回执总数</span><b>{{ overview?.parent?.feedbackCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>未读通知</span><b class="warn">{{ overview?.parent?.unreadNotify ?? '-' }}</b></div>
        <div class="kpi-line"><span style="opacity:0">·</span><b style="opacity:0">-</b></div>
      </div>
      <div class="kpi">
        <div class="kpi-title">系统端</div>
        <div class="kpi-line"><span>账号总数</span><b>{{ overview?.system?.userCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>审计日志</span><b>{{ overview?.system?.auditCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>AI 调用</span><b>{{ overview?.system?.aiCallCount ?? '-' }}</b></div>
        <div class="kpi-line"><span>成绩记录</span><b>{{ overview?.system?.scoreCount ?? '-' }}</b></div>
      </div>
    </div>

    <!-- AI 预警周报 -->
    <div class="panel weekly-panel">
      <div class="panel-title">
        AI 预警周报 · 本周全校预警态势智能解读
        <el-button size="small" type="primary" :loading="weeklyLoading" @click="loadWeeklyReport"
                   style="margin-left: 12px">{{ weeklyReport ? '重新生成' : '生成周报' }}</el-button>
      </div>
      <div v-if="weeklyReport" class="weekly-report">{{ weeklyReport }}</div>
      <el-empty v-else description="由本地大模型聚合本周预警趋势、等级/类型分布与学院排行，生成可汇报的文字周报" :image-size="50" />
    </div>

    <el-tabs v-model="activeTab" class="main-tabs">
      <!-- Tab 1：最新动态 -->
      <el-tab-pane label="最新动态" name="latest">
        <div class="panel">
          <div class="panel-title">各端最新动态</div>
          <el-tabs v-model="latestTab">
            <el-tab-pane label="预警" name="warnings">
              <el-table :data="latest?.warnings || []" size="small" border>
                <el-table-column prop="time" label="时间" width="110" />
                <el-table-column prop="subject" label="对象" />
                <el-table-column prop="content" label="类型/状态" width="150" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="帮扶工单" name="tickets">
              <el-table :data="latest?.tickets || []" size="small" border>
                <el-table-column prop="time" label="时间" width="110" />
                <el-table-column prop="subject" label="学生" />
                <el-table-column prop="content" label="状态" width="120" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="家长回执" name="feedbacks">
              <el-table :data="latest?.feedbacks || []" size="small" border>
                <el-table-column prop="time" label="时间" width="110" />
                <el-table-column prop="subject" label="回执对象" />
                <el-table-column prop="content" label="内容" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="站内消息" name="messages">
              <el-table :data="latest?.messages || []" size="small" border>
                <el-table-column prop="time" label="时间" width="110" />
                <el-table-column prop="subject" label="会话" />
                <el-table-column prop="content" label="内容" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="AI 调用" name="aiCalls">
              <el-table :data="latest?.aiCalls || []" size="small" border>
                <el-table-column prop="time" label="时间" width="110" />
                <el-table-column prop="subject" label="场景/模型" />
                <el-table-column prop="content" label="状态" width="140" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="审计日志" name="audits">
              <el-table :data="latest?.audits || []" size="small" border>
                <el-table-column prop="time" label="时间" width="110" />
                <el-table-column prop="subject" label="操作者·模块" />
                <el-table-column prop="content" label="参数" />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-tab-pane>

      <!-- Tab 2：全量记录 -->
      <el-tab-pane label="全量记录" name="records">
        <div class="panel">
          <div class="panel-title">
            全量记录（12 个维度 · 全部数据）
            <el-select v-model="recordDimension" style="width: 170px; margin-left: 12px" @change="loadRecords(1)">
              <el-option v-for="d in recordDims" :key="d.value" :value="d.value" :label="d.label" />
            </el-select>
          </div>
          <el-table :data="records?.records || []" size="small" border>
            <el-table-column prop="time" label="时间" width="120" />
            <el-table-column prop="subject" label="主体" min-width="180" />
            <el-table-column prop="content" label="维度/状态" width="160" />
            <el-table-column prop="extra" label="说明" min-width="140" />
          </el-table>
          <el-pagination class="pager" small background layout="total, prev, pager, next"
                         :total="records?.total || 0" :page-size="10"
                         :current-page="recordPage" @current-change="loadRecords" />
        </div>
      </el-tab-pane>

      <!-- Tab 3：账号全景 -->
      <el-tab-pane label="账号全景" name="accounts">
        <div class="account-row">
          <div class="panel account-list">
            <div class="panel-title">
              三端账号
              <el-radio-group v-model="accountRole" size="small" style="margin-left: 12px" @change="loadAccounts">
                <el-radio-button value="STUDENT">学生端</el-radio-button>
                <el-radio-button value="TEACHER">教师端</el-radio-button>
                <el-radio-button value="PARENT">家长端</el-radio-button>
              </el-radio-group>
            </div>
            <div class="account-items">
              <div v-for="a in accountList" :key="a.id" class="account-item"
                   :class="{ active: a.id === accountDetail?.id }" @click="loadAccount(a.id)">
                <div class="a-name">
                  {{ a.name }}
                  <el-button size="small" type="danger" text class="a-del"
                             @click.stop="onDeleteAccount(a)">删除</el-button>
                </div>
                <div class="a-sub">{{ a.no }} · {{ a.extra }}</div>
              </div>
              <el-empty v-if="!accountList.length" description="暂无账号" :image-size="50" />
            </div>
          </div>

          <div class="panel account-detail">
            <template v-if="accountDetail?.id">
              <div class="detail-head">
                {{ accountDetail.name }}
                <span class="detail-tag" :class="accountDetail.role">{{ roleText(accountDetail.role) }}</span>
                <span class="detail-sub">{{ accountDetail.no }} · {{ accountDetail.extra }}</span>
              </div>

              <!-- 学生全景 -->
              <template v-if="accountDetail.student?.studentName">
                <div class="p-grid">
                  <div class="p-cell"><div>成绩记录</div><b>{{ accountDetail.student.scoreCount ?? '-' }}</b></div>
                  <div class="p-cell"><div>平均分</div><b>{{ accountDetail.student.avgScore ?? '-' }}</b></div>
                  <div class="p-cell"><div>画像快照</div><b>{{ accountDetail.student.snapshotCount ?? '-' }}</b></div>
                  <div class="p-cell"><div>待处理预警</div><b class="warn">{{ accountDetail.student.activeWarning ?? '-' }}</b></div>
                  <div class="p-cell"><div>学习目标</div><b>{{ accountDetail.student.goalCount ?? '-' }}</b></div>
                  <div class="p-cell"><div>未读通知</div><b>{{ accountDetail.student.notifyUnread ?? '-' }}</b></div>
                  <div class="p-cell"><div>考勤记录</div><b>{{ accountDetail.student.attendanceCount ?? '-' }}</b></div>
                  <div class="p-cell"><div>行为记录</div><b>{{ accountDetail.student.behaviorCount ?? '-' }}</b></div>
                </div>
                <div v-if="accountDetail.student.latestPredictions?.length" class="d-block">
                  <div class="d-block-title">最新风险预测</div>
                  <el-table :data="accountDetail.student.latestPredictions" size="small" border>
                    <el-table-column prop="subject" label="预测结果" />
                    <el-table-column prop="content" label="学期" width="140" />
                    <el-table-column prop="time" label="时间" width="130" />
                  </el-table>
                </div>
              </template>

              <!-- 教师全景 -->
              <template v-else-if="accountDetail.role === 'TEACHER'">
                <div class="d-block">
                  <div class="d-block-title">
                    名下学生（{{ accountDetail.teacherStudents?.length || 0 }}） · 待处理预警
                    {{ accountDetail.stat?.activeWarning ?? 0 }} · 工单 {{ accountDetail.stat?.ticketCount ?? 0 }}
                  </div>
                  <el-table :data="accountDetail.teacherStudents || []" size="small" border max-height="420">
                    <el-table-column prop="subject" label="学生" min-width="160" />
                    <el-table-column prop="content" label="待处理预警" width="140" />
                    <el-table-column prop="extra" label="" />
                  </el-table>
                </div>
              </template>

              <!-- 家长全景 -->
              <template v-else-if="accountDetail.role === 'PARENT'">
                <div class="d-block">
                  <div class="d-block-title">
                    绑定子女（{{ accountDetail.parentChildren?.length || 0 }}） · 子女待处理预警合计
                    {{ accountDetail.stat?.activeWarning ?? 0 }} · 本人回执 {{ accountDetail.stat?.feedbackCount ?? 0 }}
                    · 未读通知 {{ accountDetail.stat?.notifyUnread ?? 0 }}
                  </div>
                  <el-table :data="accountDetail.parentChildren || []" size="small" border max-height="420">
                    <el-table-column prop="subject" label="子女" min-width="160" />
                    <el-table-column prop="content" label="预警情况" />
                    <el-table-column prop="extra" label="关系" width="100" />
                  </el-table>
                </div>
              </template>

              <el-empty v-else description="该账号无额外业务数据（管理岗）" :image-size="50" />
            </template>
            <el-empty v-else description="选择左侧任意账号，查看其全部信息与功能" :image-size="60" />
          </div>
        </div>
      </el-tab-pane>

      <!-- 激励卡管理（AI 绘图 · 本地 SD） -->
      <el-tab-pane label="激励卡管理" name="motivation">
        <div class="panel">
          <div class="panel-title">
            批量生成激励卡（AI 绘图 · 本地 SD）· 单次最多 20 人
            <el-button size="small" type="primary" :loading="motBatchLoading" @click="doBatchGenerate" style="margin-left: 12px">开始批量生成</el-button>
            <el-button size="small" :loading="motRecordsLoading" @click="loadMotivationRecords" style="margin-left: 8px">刷新记录</el-button>
          </div>
          <div style="display: flex; gap: 12px; flex-wrap: wrap; margin: 12px 0">
            <el-radio-group v-model="motBatchStyle">
              <el-radio-button label="ACADEMIC">学术蓝</el-radio-button>
              <el-radio-button label="STAR">星空金</el-radio-button>
              <el-radio-button label="NATURE">清新绿</el-radio-button>
            </el-radio-group>
            <el-input v-model="motStudentIds" placeholder="学生ID列表，逗号/换行分隔，如：14,15,16" style="width: 340px" />
          </div>
          <el-alert
            v-if="motBatchResult"
            :type="motBatchResult.failedCount ? 'warning' : 'success'"
            :closable="false"
            :title="`批量完成：成功 ${motBatchResult.successCount} / ${motBatchResult.total}${motBatchResult.failedCount ? '，失败 ' + motBatchResult.failedCount + '（见下表）' : ''}`"
          />
          <el-table v-if="motBatchResult && motBatchResult.succeeded?.length" :data="motBatchResult.succeeded" size="small" border style="margin-top: 10px">
            <el-table-column prop="studentId" label="学生ID" width="100" />
            <el-table-column prop="studentName" label="姓名" width="110" />
            <el-table-column prop="imageUrl" label="图片" min-width="240" show-overflow-tooltip />
          </el-table>
        </div>

        <div class="panel">
          <div class="panel-title">历史激励卡（全量记录）</div>
          <el-table :data="motRecords" size="small" border max-height="420">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="studentName" label="学生" width="110" />
            <el-table-column prop="style" label="风格" width="90">
              <template #default="{ row }">
                {{ { ACADEMIC: '学术蓝', STAR: '星空金', NATURE: '清新绿' }[row.style] || row.style }}
              </template>
            </el-table-column>
            <el-table-column prop="prompt" label="提示词" min-width="200" show-overflow-tooltip />
            <el-table-column label="生成时间" width="160">
              <template #default="{ row }">{{ formatMotTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="previewMotivation(row)">查看</el-button>
                <el-button link type="success" size="small" tag="a" :href="row.imageUrl" download>下载</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!motRecords.length" description="暂无激励卡记录" :image-size="50" />
        </div>

        <el-dialog v-model="motPreviewDialog" title="激励卡预览" width="440px" destroy-on-close>
          <img v-if="motPreviewUrl" :src="motPreviewUrl" style="width: 100%; border-radius: 8px" alt="激励卡" />
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { monitorOverview, monitorLatest, monitorRecords, monitorAccounts, monitorAccount, monitorDeleteAccount } from '@/api/monitor'
import { generateWeeklyReport } from '@/api/ai'
import { motivationRecords as motivationRecordsApi, motivationBatchGenerate } from '@/api/motivation'

const overview = ref(null)
const latest = ref(null)
const loading = ref(false)
const autoRefresh = ref(true)
const activeTab = ref('latest')
const latestTab = ref('warnings')

/** 激励卡管理 */
const motBatchStyle = ref('ACADEMIC')
const motStudentIds = ref('')
const motBatchLoading = ref(false)
const motBatchResult = ref(null)
const motRecords = ref([])
const motRecordsLoading = ref(false)
const motPreviewDialog = ref(false)
const motPreviewUrl = ref('')

function formatMotTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '-'
}

async function loadMotivationRecords() {
  motRecordsLoading.value = true
  try {
    motRecords.value = (await motivationRecordsApi()) || []
  } finally {
    motRecordsLoading.value = false
  }
}

async function doBatchGenerate() {
  const ids = motStudentIds.value
    .split(/[,，\n\r]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map(Number)
  if (!ids.length) {
    ElMessage.warning('请先输入学生ID（逗号或换行分隔）')
    return
  }
  motBatchLoading.value = true
  try {
    const res = await motivationBatchGenerate({ studentIds: ids, style: motBatchStyle.value })
    motBatchResult.value = res
    ElMessage.success(`批量生成完成：成功 ${res.successCount} / ${res.total}`)
    await loadMotivationRecords()
  } catch (e) {
    ElMessage.error(e?.message || '批量生成失败，请确认本地 SD 图像服务已启动（8001）')
  } finally {
    motBatchLoading.value = false
  }
}

function previewMotivation(row) {
  motPreviewUrl.value = row.imageUrl
  motPreviewDialog.value = true
}

// AI 周报
const weeklyReport = ref('')
const weeklyLoading = ref(false)
async function loadWeeklyReport() {
  weeklyLoading.value = true
  try {
    const res = await generateWeeklyReport()
    weeklyReport.value = res?.content || '（无内容）'
    ElMessage.success(`周报已生成（${res?.model || '本地模型'}）`)
  } catch (e) {
    ElMessage.error('周报生成失败：' + (e?.message || e))
  } finally {
    weeklyLoading.value = false
  }
}

// 全量记录
const recordDims = [
  { value: 'warning', label: '预警' },
  { value: 'ticket', label: '帮扶工单' },
  { value: 'feedback', label: '家长回执' },
  { value: 'message', label: '站内消息' },
  { value: 'notify', label: '通知' },
  { value: 'ai', label: 'AI 调用' },
  { value: 'audit', label: '审计日志' },
  { value: 'goal', label: '学习目标' },
  { value: 'prediction', label: '风险预测' },
  { value: 'score', label: '成绩' },
  { value: 'attendance', label: '考勤' },
  { value: 'behavior', label: '在线行为' }
]
const recordDimension = ref('warning')
const recordPage = ref(1)
const records = ref(null)

// 账号全景
const accountRole = ref('STUDENT')
const accountList = ref([])
const accountDetail = ref(null)

let timer = null

async function loadOverview() {
  overview.value = await monitorOverview()
}
async function loadLatest() {
  latest.value = await monitorLatest()
}
async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadLatest(), loadMotivationRecords()])
  } finally {
    loading.value = false
  }
}

async function loadRecords(page) {
  recordPage.value = page || 1
  records.value = await monitorRecords({
    dimension: recordDimension.value,
    current: recordPage.value,
    size: 10
  })
}

async function loadAccounts() {
  accountList.value = await monitorAccounts(accountRole.value)
  accountDetail.value = null
}

async function loadAccount(id) {
  accountDetail.value = await monitorAccount(id)
}

async function onDeleteAccount(a) {
  try {
    await ElMessageBox.confirm(
      `确定删除该${roleText(accountRole.value)}账号「${a.name}（${a.no || '无账号'}）」？删除后其登录权限与关联业务数据将一并移除，此操作不可恢复。`,
      '至高无上权限 · 删除账号',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await monitorDeleteAccount(a.id)
  ElMessage.success('账号已删除')
  if (accountDetail.value?.id === a.id) {
    accountDetail.value = null
  }
  await loadAccounts()
}

const roleText = (r) => ({ STUDENT: '学生', TEACHER: '教师', PARENT: '家长', ADMIN: '管理员' })[r] || r

onMounted(async () => {
  await loadAll()
  await loadAccounts()
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>

<style scoped>
.monitor {
  padding: 16px;
  box-sizing: border-box;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.title {
  font-size: 18px;
  font-weight: 700;
}

.badge {
  font-size: 12px;
  color: #e64340;
  background: #fef0ef;
  border: 1px solid #fbc4c2;
  border-radius: 12px;
  padding: 2px 10px;
  margin-left: 10px;
  font-weight: 500;
}

.head-right {
  display: flex;
  align-items: center;
}

.weekly-panel {
  margin-bottom: 12px;
}

.weekly-report {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 13px;
  max-height: 340px;
  overflow: auto;
  background: #f8fafc;
  border: 1px solid #e5e9f0;
  border-radius: 6px;
  padding: 12px 14px;
  color: #2c3e50;
}

.kpi-row {
  display: flex;
  gap: 12px;
  margin-bottom: 4px;
}

.kpi {
  flex: 1;
  background: #fff;
  border: 1px solid #e6e8eb;
  border-radius: 10px;
  padding: 12px 16px;
}

.kpi-title {
  font-size: 14px;
  font-weight: 700;
  color: #1f2329;
  margin-bottom: 8px;
}

.kpi-line {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #646a73;
  line-height: 1.9;
}

.kpi-line b {
  color: #1f2329;
}

.kpi-line b.warn {
  color: #e64340;
}

.panel {
  background: #fff;
  border: 1px solid #e6e8eb;
  border-radius: 10px;
  padding: 12px 16px;
  box-sizing: border-box;
}

.panel-title {
  font-size: 14px;
  font-weight: 700;
  color: #1f2329;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
}

.pager {
  margin-top: 10px;
  justify-content: flex-end;
}

.account-row {
  display: flex;
  gap: 12px;
}

.account-list {
  width: 300px;
  min-width: 300px;
  max-height: 560px;
}

.account-items {
  max-height: 500px;
  overflow-y: auto;
}

.account-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid transparent;
  margin-bottom: 6px;
  transition: all .15s;
}

.account-item:hover {
  background: #f5f7fa;
}

.account-item.active {
  background: #eef4ff;
  border-color: #3370ff;
}

.a-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.a-del {
  opacity: 0;
  transition: opacity .15s;
}

.account-item:hover .a-del {
  opacity: 1;
}

.a-sub {
  font-size: 12px;
  color: #646a73;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-detail {
  flex: 1;
  min-width: 0;
  min-height: 560px;
}

.detail-head {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 12px;
}

.detail-tag {
  font-size: 12px;
  border-radius: 4px;
  padding: 1px 8px;
  margin-left: 8px;
  color: #fff;
}

.detail-tag.STUDENT {
  background: #3370ff;
}

.detail-tag.TEACHER {
  background: #e6a23c;
}

.detail-tag.PARENT {
  background: #00b42a;
}

.detail-tag.ADMIN {
  background: #e64340;
}

.detail-sub {
  font-size: 12px;
  color: #646a73;
  font-weight: 400;
  margin-left: 8px;
}

.p-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.p-cell {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 8px;
  text-align: center;
}

.p-cell div {
  font-size: 12px;
  color: #646a73;
}

.p-cell b {
  font-size: 18px;
  color: #1f2329;
}

.p-cell b.warn {
  color: #e64340;
}

.d-block {
  margin-bottom: 12px;
}

.d-block-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 6px;
}

:deep(.el-table) {
  font-size: 12px;
}
</style>
