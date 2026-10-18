<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>通知中心（未读 {{ unread }} 条）</span>
          <div>
            <el-button size="small" @click="doSync">同步预警通知</el-button>
            <el-button type="primary" size="small" @click="doReadAll">全部标为已读</el-button>
          </div>
        </div>
      </template>

      <el-radio-group v-model="unreadOnly" size="small" style="margin-bottom: 12px" @change="load">
        <el-radio-button :value="false">全部</el-radio-button>
        <el-radio-button :value="true">仅未读</el-radio-button>
      </el-radio-group>

      <el-table v-loading="loading" :data="rows" border stripe size="small">
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.messageType === 'WARNING' ? 'danger' : 'info'" size="small">
              {{ row.messageType === 'WARNING' ? '预警' : '系统' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isRead ? 'success' : 'warning'" size="small">
              {{ row.isRead ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :disabled="!!row.isRead" @click="doRead(row)">标已读</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { listNotify, unreadCount as fetchUnread, markRead, markAllRead, syncWarningNotify } from '@/api/notify'

const userStore = useUserStore()
const userId = userStore.userInfo?.userId || 1

const rows = ref([])
const unread = ref(0)
const loading = ref(false)
const unreadOnly = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = await listNotify(userId, unreadOnly.value)
    unread.value = await fetchUnread(userId)
  } finally {
    loading.value = false
  }
}

async function doRead(row) {
  await markRead(row.id, userId)
  await load()
}

async function doReadAll() {
  await markAllRead(userId)
  ElMessage.success('已全部标记为已读')
  await load()
}

async function doSync() {
  const res = await syncWarningNotify(null)
  ElMessage.success(res.message || '已同步')
  await load()
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
