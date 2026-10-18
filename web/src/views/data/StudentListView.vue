<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-form :model="query" inline class="search-form">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="学号/姓名" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="学院">
          <el-select v-model="query.collegeId" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="c in colleges" :key="c.id" :label="c.collegeName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="年级">
          <el-input v-model="query.grade" placeholder="如 2023" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="在读" value="ENROLLED" />
            <el-option label="休学" value="SUSPEND" />
            <el-option label="毕业" value="GRADUATED" />
            <el-option label="退学" value="DROPPED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.masked">手机号脱敏</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增</el-button>
        <el-upload :http-request="handleImport" :show-file-list="false" accept=".xlsx,.xls" style="margin: 0 8px">
          <el-button type="success" :icon="Upload">Excel 导入</el-button>
        </el-upload>
        <el-button :icon="Download" @click="downloadTemplate">下载模板</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe size="small">
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column prop="gender" label="性别" width="60" />
        <el-table-column prop="collegeName" label="学院" min-width="150" show-overflow-tooltip />
        <el-table-column prop="majorName" label="专业" min-width="150" show-overflow-tooltip />
        <el-table-column prop="clazzName" label="班级" min-width="120" show-overflow-tooltip />
        <el-table-column prop="grade" label="年级" width="70" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENROLLED' ? 'success' : 'info'" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑学生' : '新增学生'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="学号"><el-input v-model="form.studentNo" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年级"><el-input v-model="form.grade" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="在读" value="ENROLLED" />
            <el-option label="休学" value="SUSPEND" />
            <el-option label="毕业" value="GRADUATED" />
            <el-option label="退学" value="DROPPED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Upload, Download } from '@element-plus/icons-vue'
import { pageStudents, addStudent, updateStudent, deleteStudent, importStudents, studentTemplateUrl } from '@/api/student'
import { listBase } from '@/api/base'

const STATUS_MAP = { ENROLLED: '在读', SUSPEND: '休学', GRADUATED: '毕业', DROPPED: '退学' }
const statusText = (s) => STATUS_MAP[s] || s

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const colleges = ref([])
const dialogVisible = ref(false)
const form = reactive({ id: null, studentNo: '', name: '', gender: '男', grade: '', phone: '', status: 'ENROLLED' })

const query = reactive({
  current: 1,
  size: 10,
  keyword: '',
  collegeId: null,
  grade: '',
  status: '',
  masked: false
})

async function loadData() {
  loading.value = true
  try {
    const data = await pageStudents(query)
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  Object.assign(query, { keyword: '', collegeId: null, grade: '', status: '', masked: false, current: 1 })
  loadData()
}

function openDialog(row) {
  Object.assign(form, {
    id: row?.id || null,
    studentNo: row?.studentNo || '',
    name: row?.name || '',
    gender: row?.gender || '男',
    grade: row?.grade || '',
    phone: row?.phone || '',
    status: row?.status || 'ENROLLED'
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (form.id) {
    await updateStudent({ ...form })
  } else {
    await addStudent({ ...form })
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除学生 ${row.name}？`, '提示', { type: 'warning' })
  await deleteStudent(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleImport({ file }) {
  const res = await importStudents(file)
  ElMessage.success(`导入完成：成功 ${res.successCount} 条，失败 ${res.failCount} 条`)
  if (res.errorReportUrl) {
    ElMessageBox.alert(res.errorReportUrl, '数据质量问题', { type: 'warning' })
  }
  loadData()
}

function downloadTemplate() {
  window.open(studentTemplateUrl, '_blank')
}

onMounted(async () => {
  colleges.value = await listBase('college')
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
</style>
