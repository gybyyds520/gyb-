<template>
  <div class="bigscreen">
    <!-- 顶部标题栏 -->
    <div class="topbar">
      <div class="title">
        <span class="deco"></span> 高校学情预测分析系统 · 校级总览大屏
        <span class="deco"></span>
      </div>
      <div class="top-right">
        <span class="clock">{{ clock }}</span>
        <el-switch v-model="autoRefresh" size="small" active-text="自动刷新" style="margin-right: 8px" />
        <el-button size="small" plain :loading="loading" @click="load">刷新</el-button>
        <el-button size="small" plain @click="fullscreen">全屏</el-button>
      </div>
    </div>

    <!-- 指标带 -->
    <div class="kpi-row">
      <div class="kpi" v-for="k in kpis" :key="k.label">
        <div class="kpi-label">{{ k.label }}</div>
        <div class="kpi-value" :style="{ color: k.color }">{{ k.value ?? '-' }}</div>
      </div>
    </div>

    <!-- 中间图表行 -->
    <div class="panel-row">
      <div class="panel">
        <div class="panel-title">预警趋势（近 8 周）</div>
        <div ref="trendRef" class="chart"></div>
      </div>
      <div class="panel">
        <div class="panel-title">预警等级分布</div>
        <div ref="levelRef" class="chart"></div>
      </div>
      <div class="panel">
        <div class="panel-title">预警类型分布</div>
        <div ref="typeRef" class="chart"></div>
      </div>
    </div>

    <!-- 下部：排行 / 热力 / 动态 -->
    <div class="panel-row bottom">
      <div class="panel">
        <div class="panel-title">学院预警排行</div>
        <div ref="collegeRef" class="chart"></div>
      </div>
      <div class="panel">
        <div class="panel-title">学院 × 年级 风险热力</div>
        <div class="heat-wrap">
          <div v-if="heat.length">
            <div class="heat-head">
              <div v-for="g in heatGrades" :key="g" class="heat-g">{{ g }}</div>
            </div>
            <div v-for="row in heatRows" :key="row.college" class="heat-row">
              <div class="heat-c">{{ row.college }}</div>
              <div v-for="g in heatGrades" :key="g" class="heat-cell"
                   :style="{ background: heatColor(cellValue(row, g)) }"
                   :title="row.college + ' ' + g + '：' + cellValue(row, g)">
                {{ cellValue(row, g) || '' }}
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无预警" :image-size="50" />
        </div>
      </div>
      <div class="panel">
        <div class="panel-title">最新动态</div>
        <div class="feed">
          <div v-for="w in data?.latestWarnings" :key="w.warningNo" class="feed-item warn">
            <span class="feed-tag" :style="{ background: levelColor(w.warningLevel) }">{{ levelText(w.warningLevel) }}</span>
            <span class="feed-text">{{ w.studentName }} · {{ w.title }}</span>
          </div>
          <div v-for="t in data?.latestTickets" :key="t.ticketNo" class="feed-item">
            <span class="feed-tag ticket">单</span>
            <span class="feed-text">{{ t.studentName }} · {{ t.title }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { bigScreenOverview } from '@/api/bigscreen'

const data = ref(null)
const loading = ref(false)
const autoRefresh = ref(true)
const clock = ref('')
const trendRef = ref(null)
const levelRef = ref(null)
const typeRef = ref(null)
const collegeRef = ref(null)

let charts = []
let timer = null
let clockTimer = null

const LEVEL_TEXT = { 1: '蓝', 2: '黄', 3: '橙', 4: '红' }
const levelText = (v) => LEVEL_TEXT[v] || v || '-'
const levelColor = (v) => ({ 1: '#409eff', 2: '#e6a23c', 3: '#f56c6c', 4: '#c10a0a' })[v] || '#409eff'

const kpis = computed(() => {
  const o = data.value?.overview
  return [
    { label: '在校学生', value: o?.studentCount, color: '#409eff' },
    { label: '待处理预警', value: o?.activeWarning, color: '#e6a23c' },
    { label: '高危预警', value: o?.severeWarning, color: '#f56c6c' },
    { label: '进行中工单', value: o?.openTicket, color: '#67c23a' }
  ]
})

const heat = computed(() => data.value?.riskHeatmap || [])
const heatGrades = computed(() => [...new Set(heat.value.map((c) => c.grade))].sort().reverse())
const heatRows = computed(() => {
  const byCollege = {}
  heat.value.forEach((c) => {
    if (!byCollege[c.college]) byCollege[c.college] = {}
    byCollege[c.college][c.grade] = c.count
  })
  return Object.entries(byCollege).map(([college, grades]) => ({ college, grades }))
})
const cellValue = (row, g) => row.grades[g]
const heatColor = (v) => {
  if (!v) return '#122a44'
  if (v >= 20) return '#f56c6c'
  if (v >= 10) return '#e6a23c'
  return '#67c23a'
}

async function load() {
  loading.value = true
  try {
    data.value = await bigScreenOverview()
    await render()
  } finally {
    loading.value = false
  }
}

function render() {
  const d = data.value
  if (!d) return
  dispose()
  const theme = { text: '#8fb6d9', split: '#1c3a5e', axis: '#7da4c9' }
  charts = []

  // 趋势
  if (trendRef.value) {
    const c = echarts.init(trendRef.value)
    c.setOption({
      tooltip: { trigger: 'axis' },
      grid: { top: 20, left: 40, right: 20, bottom: 30 },
      xAxis: { type: 'category', data: d.warningTrend.map((t) => t.week), axisLine: { lineStyle: { color: theme.axis } }, axisLabel: { color: theme.text } },
      yAxis: { type: 'value', minInterval: 1, axisLine: { lineStyle: { color: theme.axis } }, axisLabel: { color: theme.text }, splitLine: { lineStyle: { color: theme.split } } },
      series: [{
        type: 'line', data: d.warningTrend.map((t) => t.count), smooth: true,
        lineStyle: { color: '#409eff', width: 3 },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(64,158,255,.45)' }, { offset: 1, color: 'rgba(64,158,255,0)' }]) },
        symbolSize: 6, itemStyle: { color: '#409eff' }
      }]
    })
    charts.push(c)
  }

  // 等级分布
  if (levelRef.value) {
    const c = echarts.init(levelRef.value)
    const colors = ['#409eff', '#e6a23c', '#f56c6c', '#c10a0a']
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
      legend: { bottom: 0, textStyle: { color: theme.text } },
      series: [{
        type: 'pie', radius: ['42%', '68%'], center: ['50%', '45%'],
        label: { color: theme.text, formatter: '{b} {c}' },
        data: d.warningLevelDist.map((n, i) => ({ name: n.name, value: n.value, itemStyle: { color: colors[i] || '#409eff' } }))
      }]
    })
    charts.push(c)
  }

  // 类型分布
  if (typeRef.value) {
    const c = echarts.init(typeRef.value)
    c.setOption({
      tooltip: { trigger: 'axis' },
      grid: { top: 20, left: 40, right: 20, bottom: 30 },
      xAxis: { type: 'category', data: d.warningTypeDist.map((n) => n.name), axisLabel: { color: theme.text, interval: 0, rotate: 20, fontSize: 10 }, axisLine: { lineStyle: { color: theme.axis } } },
      yAxis: { type: 'value', minInterval: 1, axisLabel: { color: theme.text }, splitLine: { lineStyle: { color: theme.split } } },
      series: [{
        type: 'bar', data: d.warningTypeDist.map((n) => n.value), barWidth: '45%',
        itemStyle: { color: '#7c4dff', borderRadius: [4, 4, 0, 0] }
      }]
    })
    charts.push(c)
  }

  // 学院排行
  if (collegeRef.value) {
    const c = echarts.init(collegeRef.value)
    const rows = d.collegeRanking.slice(0, 6)
    c.setOption({
      tooltip: { trigger: 'axis', formatter: '{b}<br/>预警：{c}' },
      grid: { top: 10, left: 150, right: 40, bottom: 30 },
      xAxis: { type: 'value', minInterval: 1, axisLabel: { color: theme.text }, splitLine: { lineStyle: { color: theme.split } } },
      yAxis: { type: 'category', data: rows.map((r) => r.collegeName), axisLabel: { color: theme.text, fontSize: 11 } },
      series: [{
        type: 'bar', data: rows.map((r) => r.warningCount), barWidth: '55%',
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#16324f' }, { offset: 1, color: '#e6a23c' }]), borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', color: theme.text }
      }]
    })
    charts.push(c)
  }
}

function dispose() {
  charts.forEach((c) => c.dispose())
  charts = []
}

function fullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen && document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen && document.exitFullscreen()
  }
}

onMounted(async () => {
  await load()
  clockTimer = setInterval(() => {
    const d = new Date()
    clock.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
  }, 1000)
  timer = setInterval(() => {
    if (autoRefresh.value) load()
  }, 30000)
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  clearInterval(timer)
  clearInterval(clockTimer)
  window.removeEventListener('resize', resize)
  dispose()
})

function resize() {
  charts.forEach((c) => c.resize())
}
</script>

<style scoped>
.bigscreen {
  background: #0d1b2a;
  color: #e0e6ed;
  padding: 14px 18px 24px;
  min-height: calc(100vh - 90px);
  box-sizing: border-box;
  font-family: 'Microsoft YaHei', sans-serif;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
}

.deco {
  display: inline-block;
  width: 8px;
  height: 18px;
  background: linear-gradient(180deg, #409eff, #7c4dff);
  border-radius: 2px;
  margin: 0 10px;
  vertical-align: middle;
}

.top-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.clock {
  color: #8fb6d9;
  font-size: 13px;
}

.kpi-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.kpi {
  flex: 1;
  background: linear-gradient(135deg, #16324f, #122a44);
  border: 1px solid #1c3a5e;
  border-radius: 10px;
  padding: 14px 18px;
  text-align: center;
}

.kpi-label {
  font-size: 13px;
  color: #8fb6d9;
}

.kpi-value {
  font-size: 34px;
  font-weight: 800;
  margin-top: 4px;
}

.panel-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.panel {
  flex: 1;
  min-width: 0;
  background: #122a44;
  border: 1px solid #1c3a5e;
  border-radius: 10px;
  padding: 10px 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #cfe3f7;
  margin-bottom: 6px;
}

.chart {
  width: 100%;
  height: 230px;
}

.bottom .chart {
  height: 200px;
}

.heat-wrap {
  height: 200px;
  overflow-y: auto;
  font-size: 12px;
}

.heat-head,
.heat-row {
  display: grid;
  grid-template-columns: 130px repeat(auto-fit, minmax(46px, 1fr));
  gap: 4px;
  margin-bottom: 4px;
  align-items: center;
}

.heat-g {
  text-align: center;
  color: #8fb6d9;
}

.heat-c {
  color: #cfe3f7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.heat-cell {
  height: 34px;
  border-radius: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
}

.feed {
  height: 200px;
  overflow-y: auto;
}

.feed-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px dashed #1c3a5e;
  font-size: 13px;
}

.feed-tag {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}

.feed-tag.ticket {
  background: #67c23a;
}

.feed-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
