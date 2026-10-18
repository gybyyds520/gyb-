<template>
  <div class="page-container">
    <el-row :gutter="12" v-if="groups.length" class="stat-row">
      <el-col v-for="g in groups" :key="g.code" :xs="12" :md="6" :lg="4">
        <el-card shadow="hover" class="stat-card">
          <p class="stat-title">{{ g.title }}</p>
          <p class="stat-value" :style="{ color: countColor(g.code) }">{{ g.count }}</p>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-for="g in groups" :key="g.code" shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>{{ g.title }}（{{ g.count }}）</span>
          <el-button v-if="g.items?.length" size="small" type="primary" plain @click="go(g.items[0].actionPath)">
            前往处理
          </el-button>
        </div>
      </template>
      <el-empty v-if="!g.items?.length" description="暂无待办" :image-size="60" />
      <el-table v-else :data="g.items" border stripe size="small">
        <el-table-column label="事项" min-width="180">
          <template #default="{ row }">
            <el-tag v-if="row.level === 'SEVERE'" size="small" type="danger" effect="dark">严重</el-tag>
            <el-tag v-else-if="row.level === 'ATTENTION'" size="small" type="warning" effect="plain">关注</el-tag>
            <span style="margin-left: 4px">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="240" show-overflow-tooltip />
        <el-table-column prop="time" label="时间" width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' || row.status === 'OPEN' || row.status === 'UNREAD' ? 'primary' : 'info'">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="go(row.actionPath)">办理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-empty v-if="!loading && !groups.length" description="太棒了，当前没有任何待办事项" :image-size="80" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getTodos } from '@/api/todo'

const router = useRouter()
const groups = ref([])
const loading = ref(false)

const countColor = (code) => ({ WARNING: '#f56c6c', TICKET: '#e6a23c', FEEDBACK: '#409eff', GOAL: '#67c23a', NOTIFY: '#909399' })[code] || '#409eff'

const statusText = (s) => ({ ACTIVE: '进行中', OPEN: '待处理', PROCESSING: '处理中', CLOSED: '已关闭', UNREAD: '未读', ACHIEVED: '已达成', ABANDONED: '已放弃', ACKNOWLEDGED: '已阅待跟进' })[s] || s || '-'

const go = (path) => {
  if (path) router.push(path)
}

onMounted(async () => {
  loading.value = true
  try {
    groups.value = await getTodos()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 4px;
}

.stat-card {
  border-radius: 8px;
}

.stat-title {
  font-size: 13px;
  color: #909399;
  margin: 0 0 6px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
