<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>我的会员</span>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="当前等级">
          <el-tag :type="my.level > 0 ? 'success' : 'info'" size="small">
            {{ my.levelName || '普通用户' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="有效期">
          {{ my.expireTime ? formatTime(my.expireTime) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="剩余天数">
          {{ my.remainDays != null ? my.remainDays + ' 天' : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="累计充值">
          ¥{{ my.totalAmount != null ? my.totalAmount : 0 }}
        </el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="my.expired"
        type="warning"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        title="会员已到期，续费后可继续使用会员功能"
      />
    </el-card>

    <el-row :gutter="12" style="margin-top: 16px">
      <el-col v-for="lv in levels" :key="lv.levelCode" :xs="24" :md="8">
        <el-card
          shadow="never"
          class="vip-card"
          :class="{ current: my.levelCode === lv.levelCode }"
        >
          <template #header>
            <div class="card-header">
              <span>{{ lv.name }}</span>
              <el-tag v-if="my.levelCode === lv.levelCode" type="success" size="small">当前</el-tag>
            </div>
          </template>
          <div class="price">¥{{ lv.priceMonth }}<span class="unit"> / 月</span></div>
          <div class="price-sub">年费 ¥{{ lv.priceYear }}（约 ¥{{ yearlyMonth(lv) }} / 月）</div>
          <ul class="benefits">
            <li v-for="(b, i) in benefitList(lv.benefits)" :key="i">{{ b }}</li>
          </ul>
          <el-button type="primary" size="small" style="width: 100%" @click="openPay(lv)">
            {{ my.levelCode === lv.levelCode ? '续费' : '立即开通' }}
          </el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>功能与权益对照</span>
          <el-text type="info" size="small">
            基础学业数据与预警对所有人免费，会员仅覆盖增值分析功能
          </el-text>
        </div>
      </template>
      <el-table v-loading="loading" :data="features" border stripe size="small">
        <el-table-column prop="label" label="功能" min-width="200" />
        <el-table-column label="所需会员" width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="row.level === 0 ? 'info' : 'warning'">
              {{ row.levelName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="我的状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="canUse(row) ? 'success' : 'danger'">
              {{ canUse(row) ? '可用' : '需开通' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>我的充值记录</span></template>
      <el-table :data="orders" border stripe size="small">
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column prop="levelCode" label="等级" width="90" />
        <el-table-column label="周期" width="80">
          <template #default="{ row }">{{ row.period === 'YEAR' ? '年' : '月' }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.payStatus === 'PAID' ? 'success' : 'info'">
              {{ row.payStatus === 'PAID' ? '已支付' : '待支付' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" width="170">
          <template #default="{ row }">{{ formatTime(row.payTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 模拟支付 -->
    <el-dialog v-model="payDialog" title="开通会员" width="440px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="演示环境为模拟支付，点击确认即完成开通；正式环境需接入持牌支付渠道"
        style="margin-bottom: 12px"
      />
      <el-form label-width="90px" size="small">
        <el-form-item label="会员等级">
          <el-text>{{ current?.name }}</el-text>
        </el-form-item>
        <el-form-item label="购买周期">
          <el-radio-group v-model="period">
            <el-radio-button value="MONTH">月付 ¥{{ current?.priceMonth }}</el-radio-button>
            <el-radio-button value="YEAR">年付 ¥{{ current?.priceYear }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="支付方式">
          <el-radio-group v-model="payMethod">
            <el-radio-button value="WECHAT">微信支付</el-radio-button>
            <el-radio-button value="ALIPAY">支付宝</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="应付金额">
          <span class="pay-amount">¥{{ amount }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payDialog = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="doPay">确认支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  vipLevels, vipFeatures, myVip, myVipOrders, createVipOrder, payVipOrder
} from '@/api/vip'

const levels = ref([])
const features = ref([])
const orders = ref([])
const my = ref({ level: 0 })
const loading = ref(false)

const payDialog = ref(false)
const paying = ref(false)
const current = ref(null)
const period = ref('MONTH')
const payMethod = ref('WECHAT')

const amount = computed(() => {
  const lv = current.value
  if (!lv) return 0
  return period.value === 'YEAR' ? lv.priceYear : lv.priceMonth
})

function yearlyMonth(lv) {
  const v = Number(lv.priceYear) / 12
  return Number.isFinite(v) ? v.toFixed(1) : '-'
}

function benefitList(raw) {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : [String(arr)]
  } catch {
    return [String(raw)]
  }
}

function canUse(row) {
  return (my.value.level || 0) >= row.level
}

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').substring(0, 16)
}

async function load() {
  loading.value = true
  try {
    levels.value = await vipLevels()
    features.value = await vipFeatures()
    orders.value = await myVipOrders()
    my.value = await myVip()
  } finally {
    loading.value = false
  }
}

function openPay(lv) {
  current.value = lv
  period.value = 'MONTH'
  payDialog.value = true
}

async function doPay() {
  paying.value = true
  try {
    const order = await createVipOrder(current.value.levelCode, period.value)
    const res = await payVipOrder(order.orderNo, payMethod.value)
    ElMessage.success(res.message || '开通成功')
    payDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message || '支付失败')
  } finally {
    paying.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.vip-card {
  margin-bottom: 12px;
}

.vip-card.current {
  border: 1px solid #409eff;
}

.price {
  font-size: 26px;
  font-weight: 700;
  color: #f56c6c;
}

.price .unit {
  font-size: 13px;
  font-weight: 400;
  color: #909399;
}

.price-sub {
  font-size: 12px;
  color: #909399;
  margin-bottom: 10px;
}

.benefits {
  margin: 0 0 12px;
  padding-left: 18px;
  min-height: 78px;
}

.benefits li {
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
}

.pay-amount {
  font-size: 20px;
  font-weight: 700;
  color: #f56c6c;
}
</style>
