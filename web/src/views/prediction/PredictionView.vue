<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>学业风险预测</span>
          <el-tag :type="mlUp ? 'success' : 'danger'" size="small">
            {{ mlUp ? '预测服务在线' : '预测服务离线' }}
          </el-tag>
        </div>
      </template>

      <el-form inline>
        <el-form-item label="学期">
          <el-select v-model="termId" placeholder="请选择" style="width: 200px">
            <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="modelType" style="width: 180px">
            <el-option label="逻辑回归" value="logistic_regression" />
            <el-option label="随机森林" value="random_forest" />
            <el-option label="XGBoost" value="xgboost" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="training" @click="handleTrain">训练模型</el-button>
          <el-button type="success" :loading="predicting" @click="handlePredict">执行预测</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="latestModel" :column="5" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="模型">{{ latestModel.algorithm }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ latestModel.version }}</el-descriptions-item>
        <el-descriptions-item label="训练样本">{{ latestModel.trainSampleCount }}</el-descriptions-item>
        <el-descriptions-item label="准确率">{{ metricOf('accuracy') }}</el-descriptions-item>
        <el-descriptions-item label="F1">{{ metricOf('f1') }}</el-descriptions-item>
        <el-descriptions-item label="AUC">{{ metricOf('roc_auc') }}</el-descriptions-item>
        <el-descriptions-item label="精确率">{{ metricOf('precision') }}</el-descriptions-item>
        <el-descriptions-item label="召回率">{{ metricOf('recall') }}</el-descriptions-item>
        <el-descriptions-item label="训练时间">{{ latestModel.trainTime }}</el-descriptions-item>
      </el-descriptions>

      <el-row :gutter="16" v-if="Object.keys(importance).length">
        <el-col :span="24">
          <div ref="chartRef" style="height: 260px"></div>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" style="margin-top: 12px">
      <el-form inline>
        <el-form-item label="风险等级">
          <el-select v-model="query.riskLevel" placeholder="全部" clearable style="width: 130px">
            <el-option label="红色" :value="4" />
            <el-option label="橙色" :value="3" />
            <el-option label="黄色" :value="2" />
            <el-option label="蓝色" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe size="small">
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="studentName" label="姓名" width="90" />
        <el-table-column prop="clazzName" label="班级" min-width="140" show-overflow-tooltip />
        <el-table-column prop="termName" label="学期" min-width="150" show-overflow-tooltip />
        <el-table-column prop="predictedScore" label="预测成绩" width="90" />
        <el-table-column prop="predictedGpa" label="预测绩点" width="90" />
        <el-table-column prop="riskProbability" label="风险概率" width="90" />
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="levelType(row.riskLevel)" size="small">{{ levelText(row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ explainText(row.explainJson) }}</template>
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { mlHealth, trainModel, runPredict, pagePredictions, listModels } from '@/api/prediction'
import { listBase } from '@/api/base'

const LEVEL_TEXT = { 1: '蓝色', 2: '黄色', 3: '橙色', 4: '红色' }
const LEVEL_TYPE = { 1: 'info', 2: 'warning', 3: 'warning', 4: 'danger' }
const levelText = (l) => LEVEL_TEXT[l] || '未知'
const levelType = (l) => LEVEL_TYPE[l] || 'info'

const mlUp = ref(false)
const loading = ref(false)
const training = ref(false)
const predicting = ref(false)
const rows = ref([])
const total = ref(0)
const terms = ref([])
const models = ref([])
const termId = ref(null)
const modelType = ref('random_forest')
const chartRef = ref(null)
let chart = null

const query = reactive({ current: 1, size: 10, termId: null, riskLevel: null })

const latestModel = computed(() => (models.value.length ? models.value[0] : null))

const importance = computed(() => {
  if (!latestModel.value || !latestModel.value.featureListJson) return {}
  try {
    const all = JSON.parse(latestModel.value.featureListJson)
    return Object.fromEntries(Object.entries(all).filter(([, v]) => v > 0).slice(0, 10))
  } catch {
    return {}
  }
})

function metricOf(key) {
  if (!latestModel.value || !latestModel.value.metricsJson) return '-'
  try {
    const m = JSON.parse(latestModel.value.metricsJson)
    return m[key] !== undefined ? m[key] : '-'
  } catch {
    return '-'
  }
}

function explainText(json) {
  if (!json) return '-'
  try {
    const o = JSON.parse(json)
    const factors = o.mainFactors || []
    return factors.map((f) => `${f.feature}=${f.value}`).join('，')
  } catch {
    return '-'
  }
}

async function loadData() {
  loading.value = true
  try {
    query.termId = termId.value
    const data = await pagePredictions(query)
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadModels() {
  models.value = await listModels()
}

async function handleTrain() {
  training.value = true
  try {
    await trainModel(modelType.value)
    ElMessage.success('模型训练完成')
    await loadModels()
    renderChart()
  } finally {
    training.value = false
  }
}

async function handlePredict() {
  if (!termId.value) {
    ElMessage.warning('请先选择学期')
    return
  }
  predicting.value = true
  try {
    const res = await runPredict(termId.value, modelType.value)
    ElMessage.success(`预测完成，共 ${res.count} 条`)
    loadData()
  } finally {
    predicting.value = false
  }
}

function renderChart() {
  nextTick(() => {
    if (!chartRef.value || !Object.keys(importance.value).length) return
    chart = chart || echarts.init(chartRef.value)
    const keys = Object.keys(importance.value)
    chart.setOption({
      title: { text: '特征重要性 Top10', textStyle: { fontSize: 14 } },
      tooltip: {},
      grid: { left: 120, right: 40, top: 40, bottom: 20 },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: keys.reverse() },
      series: [{ type: 'bar', data: keys.map((k) => importance.value[k]), itemStyle: { color: '#409EFF' } }]
    })
  })
}

watch(importance, renderChart)

onMounted(async () => {
  terms.value = await listBase('term')
  if (terms.value.length) termId.value = terms.value[terms.value.length - 1].id
  mlUp.value = await mlHealth()
  await loadModels()
  renderChart()
  loadData()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
