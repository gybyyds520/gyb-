<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-row :gutter="12" class="stat-row">
        <el-col v-for="lv in LEVELS" :key="lv.value" :span="6">
          <div class="stat-card" :class="'lv-' + lv.value" @click="filterByLevel(lv.value)">
            <div class="stat-value">{{ stats[lv.value] || 0 }}</div>
            <div class="stat-label">{{ lv.label }}预警</div>
          </div>
        </el-col>
      </el-row>

      <div class="type-row">
        <span class="type-title">类型分布：</span>
        <el-tag v-for="t in TYPE_LIST" :key="t.value" size="small" class="type-tag"
                :class="{ active: query.warningType === t.value }"
                :effect="query.warningType === t.value ? 'dark' : 'plain'"
                @click="filterByType(t.value)">
          {{ t.label }} {{ typeStats[t.value] || 0 }}
        </el-tag>
        <el-button link type="primary" size="small" style="margin-left: 8px" :icon="Setting" @click="openRules">
          预警规则
        </el-button>
      </div>

      <el-form inline style="margin-top: 12px">
        <el-form-item label="类型">
          <el-select v-model="query.warningType" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="t in TYPE_LIST" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="query.level" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="lv in LEVELS" :key="lv.value" :label="lv.label" :value="lv.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="待处理" value="ACTIVE" />
            <el-option label="已处理" value="HANDLED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="query.termId" placeholder="全部" clearable style="width: 200px">
            <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.masked">脱敏显示</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe size="small">
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="studentName" label="姓名" width="90" />
        <el-table-column prop="clazzName" label="班级" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTagType(row.warningType)" effect="plain">{{ typeText(row.warningType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="levelType(row.warningLevel)" size="small">{{ levelText(row.warningLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskProbability" label="风险概率" width="90" />
        <el-table-column prop="title" label="预警标题" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'danger' : 'success'" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openHandle(row)">处理</el-button>
            <el-button link type="success" size="small" @click="emitIntervene(row)">建单</el-button>
            <el-button link type="warning" size="small" @click="openAttribution(row)">AI归因</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @change="loadData"
      />
    </el-card>

    <!-- 预警 AI 归因 -->
    <el-dialog v-model="attrDialogVisible" title="预警 AI 归因" width="640px">
      <div v-loading="attrLoading" class="attr-body">
        <el-empty v-if="!attrLoading && !attrContent" description="点击「AI归因」生成因果链解读" :image-size="60" />
        <div v-if="attrContent" class="attr-content">{{ attrContent }}</div>
        <div v-if="attrModel" class="attr-model">模型：{{ attrModel }}</div>
      </div>
      <template #footer>
        <el-button @click="attrDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="attrLoading" :disabled="!attrRow" @click="doAttribution">重新生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" title="处理预警" width="480px">
      <el-form :model="handleForm" label-width="90px">
        <el-form-item label="处理方式">
          <el-select v-model="handleForm.status">
            <el-option label="已处理" value="HANDLED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人ID">
          <el-input-number v-model="handleForm.ownerUserId" :min="1" />
        </el-form-item>
        <el-form-item label="处理结果">
          <el-input v-model="handleForm.handleResult" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHandle">确定</el-button>
      </template>
    </el-dialog>

    <!-- 预警规则管理：规则维度类型 + 阈值配置 -->
    <el-dialog v-model="ruleDialogVisible" title="预警规则管理" width="900px">
      <el-form inline style="margin-bottom: 8px">
        <el-form-item>
          <el-button type="primary" :icon="Plus" @click="openRuleForm()">新增规则</el-button>
        </el-form-item>
        <el-form-item style="margin-left: auto">
          <span class="rule-hint">规则类型对应预警维度（学业/出勤/作业/挂科/进度/心理/行为等），命中后按维度记录预警</span>
        </el-form-item>
      </el-form>
      <el-table v-loading="ruleLoading" :data="rules" border stripe size="small">
        <el-table-column prop="ruleCode" label="规则编码" width="170" show-overflow-tooltip />
        <el-table-column prop="ruleName" label="规则名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTagType(row.warningType)" effect="plain">{{ typeText(row.warningType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="80">
          <template #default="{ row }">
            <el-tag :type="levelType(row.level)" size="small">{{ levelText(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scoreThreshold" label="阈值" width="70" />
        <el-table-column prop="weight" label="权重" width="70" />
        <el-table-column label="启用" width="70">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openRuleForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removeRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="ruleFormVisible" title="规则配置" width="560px" append-to-body>
        <el-form :model="ruleForm" label-width="100px">
          <el-form-item label="规则编码" required>
            <el-input v-model="ruleForm.ruleCode" placeholder="如 RULE_PSYCH_YELLOW" />
          </el-form-item>
          <el-form-item label="规则名称" required>
            <el-input v-model="ruleForm.ruleName" placeholder="如 心理关怀-黄色" />
          </el-form-item>
          <el-form-item label="维度类型" required>
            <el-select v-model="ruleForm.warningType" style="width: 100%">
              <el-option v-for="t in TYPE_LIST" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="预警等级" required>
            <el-select v-model="ruleForm.level" style="width: 100%">
              <el-option v-for="lv in LEVELS" :key="lv.value" :label="lv.label" :value="lv.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="风险阈值">
            <el-input-number v-model="ruleForm.scoreThreshold" :min="0" :max="100" />
            <span class="rule-hint">风险分（0-100）达到该值即触发</span>
          </el-form-item>
          <el-form-item label="权重">
            <el-input-number v-model="ruleForm.weight" :min="0" :max="10" :step="0.1" />
          </el-form-item>
          <el-form-item label="触发条件JSON">
            <el-input v-model="ruleForm.conditionJson" type="textarea" :rows="3"
                      placeholder='{"attendanceRate":{"lt":0.85}}' />
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="ruleForm.description" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="ruleFormVisible = false">取消</el-button>
          <el-button type="primary" @click="submitRule">保存</el-button>
        </template>
      </el-dialog>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Setting, Plus } from '@element-plus/icons-vue'
import {
  pageWarnings,
  warningStatistics,
  warningTypeStatistics,
  handleWarning,
  listWarningRules,
  saveWarningRule,
  deleteWarningRule,
  warningAttribution
} from '@/api/warning'
import { listBase } from '@/api/base'

const LEVELS = [
  { value: 4, label: '红色' },
  { value: 3, label: '橙色' },
  { value: 2, label: '黄色' },
  { value: 1, label: '蓝色' }
]
const LEVEL_TEXT = { 1: '蓝色', 2: '黄色', 3: '橙色', 4: '红色' }
const LEVEL_TYPE = { 1: 'info', 2: 'warning', 3: 'warning', 4: 'danger' }
const STATUS_TEXT = { ACTIVE: '待处理', HANDLED: '已处理', CLOSED: '已关闭', SUPPRESSED: '已抑制' }

/** 预警维度类型（与后端 warning_type 枚举保持一致） */
const TYPE_LIST = [
  { value: 'ACADEMIC', label: '学业' },
  { value: 'ATTENDANCE', label: '出勤' },
  { value: 'HOMEWORK', label: '作业' },
  { value: 'FAIL', label: '挂科' },
  { value: 'PROGRESS', label: '进度' },
  { value: 'PSYCH', label: '心理' },
  { value: 'BEHAVIOR', label: '行为' },
  { value: 'CONSUMPTION', label: '消费' },
  { value: 'COMPREHENSIVE', label: '综合' }
]
const TYPE_TEXT = Object.fromEntries(TYPE_LIST.map((t) => [t.value, t.label]))
const TYPE_TAG = {
  ACADEMIC: 'primary',
  ATTENDANCE: 'warning',
  HOMEWORK: 'info',
  FAIL: 'danger',
  PROGRESS: 'info',
  PSYCH: 'success',
  BEHAVIOR: 'warning',
  CONSUMPTION: 'info',
  COMPREHENSIVE: 'info'
}

const levelText = (l) => LEVEL_TEXT[l] || '未知'
const levelType = (l) => LEVEL_TYPE[l] || 'info'
const statusText = (s) => STATUS_TEXT[s] || s
const typeText = (t) => TYPE_TEXT[t] || t || '未知'
const typeTagType = (t) => TYPE_TAG[t] || 'info'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const stats = ref({})
const typeStats = ref({})
const terms = ref([])
const dialogVisible = ref(false)
const currentId = ref(null)
const handleForm = reactive({ status: 'HANDLED', ownerUserId: 1, handleResult: '' })

const query = reactive({
  current: 1,
  size: 10,
  level: null,
  status: '',
  termId: null,
  warningType: '',
  masked: false
})

async function loadData() {
  loading.value = true
  try {
    const data = await pageWarnings(query)
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  stats.value = await warningStatistics(query.termId)
  typeStats.value = await warningTypeStatistics(query.termId)
}

function resetQuery() {
  Object.assign(query, { level: null, status: '', termId: null, warningType: '', masked: false, current: 1 })
  loadData()
  loadStats()
}

function filterByLevel(level) {
  query.level = level
  query.current = 1
  loadData()
}

function filterByType(type) {
  query.warningType = query.warningType === type ? '' : type
  query.current = 1
  loadData()
}

function openHandle(row) {
  currentId.value = row.id
  handleForm.status = 'HANDLED'
  handleForm.handleResult = ''
  dialogVisible.value = true
}

async function submitHandle() {
  await handleWarning(currentId.value, { ...handleForm })
  ElMessage.success('处理成功')
  dialogVisible.value = false
  loadData()
  loadStats()
}

/** 跳转到干预页并带上预警 ID */
function emitIntervene(row) {
  window.dispatchEvent(new CustomEvent('xqfx:intervene', { detail: { warningId: row.id, studentName: row.studentName } }))
  ElMessage.info(`已选择预警 ${row.warningNo || row.id}，请在干预闭环页完成建单`)
}

/** ---------- 预警 AI 归因 ---------- */
const attrDialogVisible = ref(false)
const attrLoading = ref(false)
const attrRow = ref(null)
const attrContent = ref('')
const attrModel = ref('')

function openAttribution(row) {
  attrRow.value = row
  attrContent.value = ''
  attrModel.value = ''
  attrDialogVisible.value = true
  doAttribution()
}

async function doAttribution() {
  if (!attrRow.value) return
  attrLoading.value = true
  try {
    const res = await warningAttribution(attrRow.value.id)
    attrContent.value = res?.content || '（无返回内容）'
    attrModel.value = res?.model || ''
  } finally {
    attrLoading.value = false
  }
}

/** ---------- 预警规则管理 ---------- */
const ruleDialogVisible = ref(false)
const ruleLoading = ref(false)
const rules = ref([])
const ruleFormVisible = ref(false)
const ruleForm = reactive({
  id: null,
  ruleCode: '',
  ruleName: '',
  warningType: 'ACADEMIC',
  level: 2,
  scoreThreshold: 40,
  weight: 1,
  conditionJson: '{}',
  enabled: 1,
  description: ''
})

async function openRules() {
  ruleDialogVisible.value = true
  await loadRules()
}

async function loadRules() {
  ruleLoading.value = true
  try {
    rules.value = await listWarningRules()
  } finally {
    ruleLoading.value = false
  }
}

function openRuleForm(row) {
  Object.assign(ruleForm, row
    ? {
        id: row.id,
        ruleCode: row.ruleCode,
        ruleName: row.ruleName,
        warningType: row.warningType || 'ACADEMIC',
        level: row.level ?? 2,
        scoreThreshold: row.scoreThreshold ?? 40,
        weight: row.weight ?? 1,
        conditionJson: row.conditionJson || '{}',
        enabled: row.enabled ?? 1,
        description: row.description || ''
      }
    : {
        id: null,
        ruleCode: '',
        ruleName: '',
        warningType: 'ACADEMIC',
        level: 2,
        scoreThreshold: 40,
        weight: 1,
        conditionJson: '{}',
        enabled: 1,
        description: ''
      })
  ruleFormVisible.value = true
}

async function submitRule() {
  if (!ruleForm.ruleCode || !ruleForm.ruleName) {
    ElMessage.warning('请填写规则编码与名称')
    return
  }
  await saveWarningRule({ ...ruleForm })
  ElMessage.success('规则已保存')
  ruleFormVisible.value = false
  await loadRules()
}

async function removeRule(row) {
  await ElMessageBox.confirm(`确认删除规则「${row.ruleName}」？`, '提示', { type: 'warning' })
  await deleteWarningRule(row.id)
  ElMessage.success('已删除')
  await loadRules()
}

onMounted(async () => {
  terms.value = await listBase('term')
  await loadData()
  await loadStats()
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 4px;
}

.stat-card {
  padding: 14px;
  border-radius: 6px;
  text-align: center;
  cursor: pointer;
  color: #fff;
}

.stat-card.lv-4 { background: #f56c6c; }
.stat-card.lv-3 { background: #e6a23c; }
.stat-card.lv-2 { background: #909399; }
.stat-card.lv-1 { background: #409eff; }

.stat-value {
  font-size: 26px;
  font-weight: 700;
}

.stat-label {
  font-size: 13px;
  margin-top: 4px;
}

.type-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.type-title {
  font-size: 13px;
  color: #606266;
}

.type-tag {
  cursor: pointer;
}

.rule-hint {
  font-size: 12px;
  color: #909399;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

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
