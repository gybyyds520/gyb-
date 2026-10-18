<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>模型管理与评估中心</span>
          <div>
            <el-select v-model="trainType" style="width: 170px; margin-right: 8px">
              <el-option label="随机森林" value="random_forest" />
              <el-option label="逻辑回归" value="logistic_regression" />
              <el-option label="XGBoost" value="xgboost" />
            </el-select>
            <el-button type="primary" :loading="training" @click="doTrain">训练模型</el-button>
          </div>
        </div>
      </template>

      <div class="chart-box" v-loading="compareLoading">
        <div ref="compareRef" class="compare-chart"></div>
        <el-empty v-if="!compareLoading && !compare.length" description="暂无已训练模型，点击「训练模型」开始" :image-size="60" />
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>模型注册表</span>
          <el-button size="small" @click="loadList">刷新</el-button>
        </div>
      </template>
      <el-table :data="models" border stripe size="small" v-loading="listLoading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="modelCode" label="模型编码" width="120" />
        <el-table-column prop="modelName" label="名称" min-width="150" />
        <el-table-column prop="algorithm" label="算法" width="150" />
        <el-table-column prop="version" label="版本" width="90" />
        <el-table-column prop="trainSampleCount" label="样本数" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="trainTime" label="训练时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">评估详情</el-button>
            <el-button v-if="row.status !== 'DEPLOYED'" link type="success" size="small" @click="switchStatus(row, 'DEPLOYED')">上线</el-button>
            <el-button v-if="row.status === 'DEPLOYED'" link type="warning" size="small" @click="switchStatus(row, 'DISABLED')">下线</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @change="loadList"
      />
    </el-card>

    <!-- 评估详情 -->
    <el-dialog v-model="detailVisible" :title="detail?.model?.modelName || '评估详情'" width="760px">
      <div v-if="detail" v-loading="detailLoading">
        <div class="detail-metrics">
          <div class="metric-item" v-for="(v, k) in detail.metrics" :key="k">
            <div class="metric-label">{{ metricName(k) }}</div>
            <div class="metric-value">{{ typeof v === 'number' ? v.toFixed(4) : v }}</div>
          </div>
        </div>
        <div ref="featureRef" class="feature-chart"></div>
        <el-empty v-if="!detail.features?.length" description="暂无特征重要性数据" :image-size="60" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { pageModels, getModel, modelCompare, updateModelStatus } from '@/api/model'
import request from '@/api/request'

const trainType = ref('random_forest')
const training = ref(false)
const models = ref([])
const compare = ref([])
const compareLoading = ref(false)
const listLoading = ref(false)
const total = ref(0)
const query = reactive({ current: 1, size: 10, algorithm: '', status: '' })
const detailVisible = ref(false)
const detail = ref(null)
const detailLoading = ref(false)
const compareRef = ref(null)
const featureRef = ref(null)
let compareChart = null
let featureChart = null

const statusText = (s) => ({ TRAINED: '已训练', DEPLOYED: '已上线', DISABLED: '已下线' })[s] || s || '-'
const statusTag = (s) => ({ TRAINED: 'info', DEPLOYED: 'success', DISABLED: 'info' })[s] || 'info'

const METRIC_CN = {
  accuracy: '准确率', precision: '精确率', recall: '召回率', f1: 'F1 值',
  auc: 'AUC', rmse: 'RMSE', mse: 'MSE', mae: 'MAE'
}
const metricName = (k) => METRIC_CN[k] || k

async function doTrain() {
  training.value = true
  try {
    await request.post('/prediction/train', null, { params: { modelType: trainType.value } })
    ElMessage.success('模型训练完成')
    await Promise.all([loadList(), loadCompare()])
  } finally {
    training.value = false
  }
}

async function loadList() {
  listLoading.value = true
  try {
    const data = await pageModels(query)
    models.value = data.records
    total.value = data.total
  } finally {
    listLoading.value = false
  }
}

async function loadCompare() {
  compareLoading.value = true
  try {
    compare.value = await modelCompare()
    await nextTick()
    renderCompare()
  } finally {
    compareLoading.value = false
  }
}

function renderCompare() {
  if (!compareRef.value) return
  if (compareChart) compareChart.dispose()
  if (!compare.length) return
  const names = compare.map((m) => `${m.modelName}\n${m.algorithm} v${m.version}`)
  const series = ['accuracy', 'precision', 'recall', 'f1', 'auc'].map((k) => ({
    name: metricName(k),
    type: 'bar',
    data: compare.map((m) => {
      const v = m.metrics?.[k]
      return typeof v === 'number' ? v : null
    })
  }))
  compareChart = echarts.init(compareRef.value)
  compareChart.setOption({
    title: { text: '多模型评估指标对比', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { top: 30 },
    grid: { top: 70, left: 50, right: 20, bottom: 30 },
    xAxis: { type: 'category', data: names, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', min: 0, max: 1, name: '指标值' },
    series
  })
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getModel(row.id)
    await nextTick()
    renderFeature()
  } finally {
    detailLoading.value = false
  }
}

function renderFeature() {
  if (!featureRef.value) return
  if (featureChart) featureChart.dispose()
  const feats = detail.value?.features || []
  if (!feats.length) return
  const sorted = [...feats].sort((a, b) => (Number(a.importance) || 0) - (Number(b.importance) || 0))
  featureChart = echarts.init(featureRef.value)
  featureChart.setOption({
    title: { text: '特征重要性（Top 特征）', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    grid: { top: 40, left: 140, right: 40, bottom: 30 },
    xAxis: { type: 'value', name: '重要性' },
    yAxis: { type: 'category', data: sorted.map((f) => f.feature || f.name || String(f)), axisLabel: { fontSize: 11 } },
    series: [{
      type: 'bar',
      data: sorted.map((f) => Number(f.importance) || 0),
      itemStyle: { color: '#409eff' },
      barWidth: '60%'
    }]
  })
}

async function switchStatus(row, status) {
  await updateModelStatus(row.id, status)
  ElMessage.success(`模型已${status === 'DEPLOYED' ? '上线' : '下线'}`)
  await Promise.all([loadList(), loadCompare()])
}

onMounted(() => {
  loadList()
  loadCompare()
  window.addEventListener('resize', () => compareChart && compareChart.resize())
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-box {
  min-height: 260px;
}

.compare-chart {
  width: 100%;
  height: 320px;
}

.feature-chart {
  width: 100%;
  height: 320px;
  margin-top: 12px;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.detail-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 8px;
}

.metric-item {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 10px 16px;
  min-width: 100px;
  text-align: center;
}

.metric-label {
  font-size: 12px;
  color: #909399;
}

.metric-value {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  margin-top: 4px;
}
</style>
