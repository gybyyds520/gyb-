<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form :model="query" inline class="search-form">
        <el-form-item label="学期">
          <el-select v-model="query.termId" placeholder="全部" clearable style="width: 200px">
            <el-option v-for="t in terms" :key="t.id" :label="t.termName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="query.courseId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.onlyFailed">仅看不及格</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-upload :http-request="handleImport" :show-file-list="false" accept=".xlsx,.xls">
          <el-button type="success" :icon="Upload">Excel 导入</el-button>
        </el-upload>
        <el-button :icon="Download" style="margin-left: 8px" @click="downloadTemplate">下载模板</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe size="small">
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="studentName" label="姓名" width="90" />
        <el-table-column prop="clazzName" label="班级" min-width="140" show-overflow-tooltip />
        <el-table-column prop="courseName" label="课程" min-width="150" show-overflow-tooltip />
        <el-table-column prop="termName" label="学期" min-width="160" show-overflow-tooltip />
        <el-table-column prop="usualScore" label="平时" width="80" />
        <el-table-column prop="midScore" label="期中" width="80" />
        <el-table-column prop="finalScore" label="期末" width="80" />
        <el-table-column prop="totalScore" label="总评" width="80">
          <template #default="{ row }">
            <span :class="{ 'fail-score': row.isPass === 0 }">{{ row.totalScore }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="gradePoint" label="绩点" width="70" />
        <el-table-column label="是否及格" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isPass === 1 ? 'success' : 'danger'" size="small">
              {{ row.isPass === 1 ? '及格' : '不及格' }}
            </el-tag>
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
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Upload, Download } from '@element-plus/icons-vue'
import { pageScores, importScores, scoreTemplateUrl } from '@/api/score'
import { listBase } from '@/api/base'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const terms = ref([])
const courses = ref([])

const query = reactive({
  current: 1,
  size: 10,
  termId: null,
  courseId: null,
  onlyFailed: false
})

async function loadData() {
  loading.value = true
  try {
    const data = await pageScores(query)
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  Object.assign(query, { termId: null, courseId: null, onlyFailed: false, current: 1 })
  loadData()
}

async function handleImport({ file }) {
  const res = await importScores(file)
  ElMessage.success(`导入完成：成功 ${res.successCount} 条，失败 ${res.failCount} 条`)
  if (res.errorReportUrl) {
    ElMessageBox.alert(res.errorReportUrl, '数据质量问题', { type: 'warning' })
  }
  loadData()
}

function downloadTemplate() {
  window.open(scoreTemplateUrl, '_blank')
}

onMounted(async () => {
  terms.value = await listBase('term')
  courses.value = await listBase('course')
  loadData()
})
</script>

<style scoped>
.search-form {
  margin-bottom: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.fail-score {
  color: #f56c6c;
  font-weight: 600;
}
</style>
