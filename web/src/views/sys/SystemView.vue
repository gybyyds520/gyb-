<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <!-- 用户管理 -->
        <el-tab-pane label="用户管理" name="user">
          <el-form inline>
            <el-form-item label="关键字">
              <el-input v-model="userQuery.keyword" placeholder="用户名/姓名" clearable style="width: 180px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadUsers">查询</el-button>
              <el-button type="success" @click="openUser(null)">新增用户</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="loading" :data="users" border stripe size="small">
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="realName" label="姓名" width="120" />
            <el-table-column prop="userType" label="类型" width="100" />
            <el-table-column prop="phone" label="手机号" width="140" />
            <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openUser(row)">编辑</el-button>
                <el-button link type="warning" size="small" @click="openRoles(row)">分配角色</el-button>
                <el-button link type="danger" size="small" @click="removeUser(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="userQuery.current"
            v-model:page-size="userQuery.size"
            :total="userTotal"
            layout="total, prev, pager, next"
            class="pagination"
            @change="loadUsers"
          />
        </el-tab-pane>

        <!-- 角色管理 -->
        <el-tab-pane label="角色管理" name="role">
          <el-button type="success" size="small" @click="openRole(null)">新增角色</el-button>
          <el-table :data="roles" border stripe size="small" style="margin-top: 12px">
            <el-table-column prop="roleCode" label="角色编码" width="180" />
            <el-table-column prop="roleName" label="角色名称" width="160" />
            <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openRole(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeRole(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 参数配置 -->
        <el-tab-pane label="参数配置" name="config">
          <el-button type="success" size="small" @click="openConfig(null)">新增配置</el-button>
          <el-table :data="configs" border stripe size="small" style="margin-top: 12px">
            <el-table-column prop="configKey" label="配置键" width="220" />
            <el-table-column prop="configName" label="名称" width="180" />
            <el-table-column prop="configValue" label="配置值" min-width="180" show-overflow-tooltip />
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openConfig(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 审计日志 -->
        <el-tab-pane label="审计日志" name="audit">
          <el-form inline>
            <el-form-item label="模块">
              <el-input v-model="auditQuery.module" placeholder="如：系统管理" clearable style="width: 160px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadAudit">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="audits" border stripe size="small">
            <el-table-column prop="username" label="操作人" width="120" />
            <el-table-column prop="module" label="模块" width="120" />
            <el-table-column prop="operation" label="操作" width="180" />
            <el-table-column prop="url" label="请求地址" min-width="200" show-overflow-tooltip />
            <el-table-column prop="ip" label="IP" width="130" />
            <el-table-column prop="costMs" label="耗时(ms)" width="100" />
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" width="170" />
          </el-table>
          <el-pagination
            v-model:current-page="auditQuery.current"
            v-model:page-size="auditQuery.size"
            :total="auditTotal"
            layout="total, prev, pager, next"
            class="pagination"
            @change="loadAudit"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 用户编辑 -->
    <el-dialog v-model="userDialog" :title="userForm.id ? '编辑用户' : '新增用户'" width="480px">
      <el-form :model="userForm" label-width="90px">
        <el-form-item label="用户名"><el-input v-model="userForm.username" :disabled="!!userForm.id" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="userForm.realName" /></el-form-item>
        <el-form-item label="密码">
          <el-input v-model="userForm.password" type="password" show-password
                    :placeholder="userForm.id ? '留空表示不修改' : '留空则默认 123456'" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="userForm.userType">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="辅导员" value="COUNSELOR" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="学生" value="STUDENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model="userForm.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="userForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialog = false">取消</el-button>
        <el-button type="primary" @click="submitUser">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色分配 -->
    <el-dialog v-model="roleDialog" title="分配角色" width="420px">
      <el-checkbox-group v-model="checkedRoles">
        <el-checkbox v-for="r in roles" :key="r.id" :value="r.id" :label="r.id">
          {{ r.roleName }}（{{ r.roleCode }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" @click="submitRoles">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色编辑 -->
    <el-dialog v-model="roleEditDialog" title="角色" width="420px">
      <el-form :model="roleForm" label-width="90px">
        <el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="roleForm.roleName" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="roleForm.description" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="roleForm.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleEditDialog = false">取消</el-button>
        <el-button type="primary" @click="submitRole">确定</el-button>
      </template>
    </el-dialog>

    <!-- 配置编辑 -->
    <el-dialog v-model="configDialog" title="参数配置" width="480px">
      <el-form :model="configForm" label-width="90px">
        <el-form-item label="配置键"><el-input v-model="configForm.configKey" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="configForm.configName" /></el-form-item>
        <el-form-item label="配置值"><el-input v-model="configForm.configValue" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="configForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialog = false">取消</el-button>
        <el-button type="primary" @click="submitConfig">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  pageUsers, addUser, updateUser, deleteUser, userRoles, assignRoles,
  listRoles, saveRole, deleteRole, listConfigs, saveConfig, pageAuditLogs
} from '@/api/sys'

const tab = ref('user')
const loading = ref(false)
const users = ref([])
const userTotal = ref(0)
const roles = ref([])
const configs = ref([])
const audits = ref([])
const auditTotal = ref(0)

const userQuery = reactive({ current: 1, size: 10, keyword: '' })
const auditQuery = reactive({ current: 1, size: 10, module: '' })

const userDialog = ref(false)
const roleDialog = ref(false)
const roleEditDialog = ref(false)
const configDialog = ref(false)
const checkedRoles = ref([])
const currentUserId = ref(null)

const userForm = reactive({ id: null, username: '', realName: '', password: '', userType: 'COUNSELOR', phone: '', email: '', status: 1 })
const roleForm = reactive({ id: null, roleCode: '', roleName: '', description: '', sort: 0 })
const configForm = reactive({ id: null, configKey: '', configName: '', configValue: '', remark: '' })

async function loadUsers() {
  loading.value = true
  try {
    const data = await pageUsers(userQuery)
    users.value = data.records
    userTotal.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  roles.value = await listRoles()
}

async function loadConfigs() {
  configs.value = await listConfigs()
}

async function loadAudit() {
  const data = await pageAuditLogs(auditQuery)
  audits.value = data.records
  auditTotal.value = data.total
}

function openUser(row) {
  Object.assign(userForm, row
    ? { ...row, password: '' }
    : { id: null, username: '', realName: '', password: '', userType: 'COUNSELOR', phone: '', email: '', status: 1 })
  userDialog.value = true
}

async function submitUser() {
  if (userForm.id) {
    await updateUser(userForm)
  } else {
    await addUser(userForm)
  }
  ElMessage.success('保存成功')
  userDialog.value = false
  loadUsers()
}

async function removeUser(row) {
  await deleteUser(row.id)
  ElMessage.success('已删除')
  loadUsers()
}

async function openRoles(row) {
  currentUserId.value = row.id
  checkedRoles.value = await userRoles(row.id)
  await loadRoles()
  roleDialog.value = true
}

async function submitRoles() {
  await assignRoles(currentUserId.value, checkedRoles.value)
  ElMessage.success('角色已分配')
  roleDialog.value = false
}

function openRole(row) {
  Object.assign(roleForm, row
    ? { ...row }
    : { id: null, roleCode: '', roleName: '', description: '', sort: 0 })
  roleEditDialog.value = true
}

async function submitRole() {
  await saveRole(roleForm)
  ElMessage.success('保存成功')
  roleEditDialog.value = false
  loadRoles()
}

async function removeRole(row) {
  await deleteRole(row.id)
  ElMessage.success('已删除')
  loadRoles()
}

function openConfig(row) {
  Object.assign(configForm, row
    ? { ...row }
    : { id: null, configKey: '', configName: '', configValue: '', remark: '' })
  configDialog.value = true
}

async function submitConfig() {
  await saveConfig(configForm)
  ElMessage.success('保存成功')
  configDialog.value = false
  loadConfigs()
}

onMounted(async () => {
  await loadUsers()
  await loadRoles()
  await loadConfigs()
  await loadAudit()
})
</script>

<style scoped>
.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
