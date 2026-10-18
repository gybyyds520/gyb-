<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>干预闭环工单</span>
          <el-button type="primary" size="small" @click="dialogVisible = true">新建工单</el-button>
        </div>
      </template>

      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="status" placeholder="全部" clearable style="width: 130px">
            <el-option label="待处理" value="OPEN" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe size="small">
        <el-table-column prop="ticketNo" label="工单号" width="200" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="studentName" label="姓名" width="90" />
        <el-table-column prop="interveneType" label="干预方式" width="100">
          <template #default="{ row }">{{ typeText(row.interveneType) }}</template>
        </el-table-column>
        <el-table-column prop="plan" label="帮扶计划" min-width="180" show-overflow-tooltip />
        <el-table-column prop="dueDate" label="截止日期" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CLOSED' ? 'success' : 'warning'" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="效果" width="100">
          <template #default="{ row }">
            <span v-if="row.effectEval">{{ effectText(row.effectEval) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="logCount" label="记录数" width="80" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openLog(row)">记录</el-button>
            <el-button link type="success" size="small" :disabled="row.status === 'CLOSED'" @click="openClose(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建干预工单" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="来源预警ID">
          <el-input-number v-model="form.warningId" :min="1" />
        </el-form-item>
        <el-form-item label="责任人ID">
          <el-input-number v-model="form.ownerUserId" :min="1" />
        </el-form-item>
        <el-form-item label="干预方式">
          <el-select v-model="form.interveneType">
            <el-option label="谈心谈话" value="TALK" />
            <el-option label="学业辅导" value="TUTORING" />
            <el-option label="作业督导" value="HOMEWORK" />
            <el-option label="心理疏导" value="PSYCHOLOGY" />
            <el-option label="家校联动" value="FAMILY" />
          </el-select>
        </el-form-item>
        <el-form-item label="帮扶计划">
          <el-input v-model="form.plan" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="logVisible" title="干预过程记录" width="600px">
      <el-timeline>
        <el-timeline-item v-for="(l, i) in logs" :key="i" :timestamp="String(l.actionDate)" type="primary">
          {{ l.actionType }}：{{ l.actionContent }}
        </el-timeline-item>
      </el-timeline>
      <el-divider />
      <el-input v-model="newLog" type="textarea" :rows="2" placeholder="新增一条帮扶记录" />
      <template #footer>
        <el-button @click="logVisible = false">关闭</el-button>
        <el-button type="primary" @click="submitLog">追加记录</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="closeVisible" title="关闭工单与效果评估" width="500px">
      <el-form :model="closeForm" label-width="90px">
        <el-form-item label="效果评估">
          <el-select v-model="closeForm.effectEval">
            <el-option label="明显改善" value="IMPROVED" />
            <el-option label="略有好转" value="SLIGHT" />
            <el-option label="无明显变化" value="NONE" />
            <el-option label="恶化" value="WORSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="结论">
          <el-input v-model="closeForm.conclusion" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" @click="submitClose">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { listTickets, createTicket, closeTicket, appendLog, listLogs } from '@/api/intervention'

const TYPE_TEXT = { TALK: '谈心谈话', TUTORING: '学业辅导', HOMEWORK: '作业督导', PSYCHOLOGY: '心理疏导', FAMILY: '家校联动' }
const STATUS_TEXT = { OPEN: '待处理', PROCESSING: '处理中', CLOSED: '已关闭' }
const EFFECT_TEXT = { IMPROVED: '明显改善', SLIGHT: '略有好转', NONE: '无明显变化', WORSE: '恶化' }

const typeText = (t) => TYPE_TEXT[t] || t
const statusText = (s) => STATUS_TEXT[s] || s
const effectText = (e) => EFFECT_TEXT[e] || e

const loading = ref(false)
const rows = ref([])
const status = ref('')
const dialogVisible = ref(false)
const logVisible = ref(false)
const closeVisible = ref(false)
const logs = ref([])
const newLog = ref('')
const currentId = ref(null)

const form = reactive({ warningId: 1, ownerUserId: 1, interveneType: 'TALK', plan: '' })
const closeForm = reactive({ effectEval: 'SLIGHT', conclusion: '' })

async function loadData() {
  loading.value = true
  try {
    rows.value = await listTickets({ status: status.value })
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  await createTicket({ ...form })
  ElMessage.success('工单创建成功')
  dialogVisible.value = false
  loadData()
}

async function openLog(row) {
  currentId.value = row.id
  logs.value = await listLogs(row.id)
  newLog.value = ''
  logVisible.value = true
}

async function submitLog() {
  if (!newLog.value.trim()) {
    ElMessage.warning('请输入记录内容')
    return
  }
  await appendLog(currentId.value, { actionType: 'ACTION', content: newLog.value, operatorId: 1 })
  ElMessage.success('已追加')
  newLog.value = ''
  logs.value = await listLogs(currentId.value)
  loadData()
}

function openClose(row) {
  currentId.value = row.id
  closeForm.effectEval = 'SLIGHT'
  closeForm.conclusion = ''
  closeVisible.value = true
}

async function submitClose() {
  await closeTicket(currentId.value, { ...closeForm })
  ElMessage.success('工单已关闭')
  closeVisible.value = false
  loadData()
}

onMounted(() => {
  loadData()
  window.addEventListener('xqfx:intervene', (e) => {
    form.warningId = e.detail.warningId
    dialogVisible.value = true
  })
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
