<template>
  <div class="page-container">
    <el-card shadow="never" class="msg-card">
      <el-row :gutter="12">
        <!-- 通讯录 -->
        <el-col :xs="24" :md="7">
          <div class="side">
            <div class="side-title">
              <span>通讯录</span>
              <el-tag v-if="unread" type="danger" size="small" round>未读 {{ unread }}</el-tag>
            </div>
            <el-scrollbar class="side-list">
              <div
                v-for="c in contacts"
                :key="c.userId"
                class="contact"
                :class="{ active: current && current.userId === c.userId }"
                @click="openChat(c)"
              >
                <div class="contact-main">
                  <span class="contact-name">{{ c.name }}</span>
                  <span class="contact-unread" v-if="unreadOf(c.userId)">{{ unreadOf(c.userId) }}</span>
                </div>
                <div class="contact-sub">
                  <el-tag size="small" effect="plain">{{ roleText(c.role) }}</el-tag>
                  <span>{{ c.relation }}</span>
                </div>
              </div>
              <el-empty v-if="!contacts.length" description="暂无可联系的对象" :image-size="60" />
            </el-scrollbar>
          </div>
        </el-col>

        <!-- 会话区 -->
        <el-col :xs="24" :md="17">
          <div class="chat">
            <template v-if="current">
              <div class="chat-title">
                <span>{{ current.name }}</span>
                <el-text type="info" size="small">{{ current.relation }}</el-text>
              </div>

              <el-scrollbar ref="scrollRef" class="chat-body">
                <div
                  v-for="m in messages"
                  :key="m.id"
                  class="msg-row"
                  :class="m.mine ? 'mine' : 'peer'"
                >
                  <div class="msg-bubble">
                    <div class="msg-meta">{{ m.senderName }} · {{ formatTime(m.createTime) }}</div>
                    <div class="msg-content">{{ m.content }}</div>
                  </div>
                </div>
                <el-empty v-if="!messages.length" description="还没有消息，打个招呼吧" :image-size="60" />
              </el-scrollbar>

              <div class="chat-input">
                <el-input
                  v-model="draft"
                  type="textarea"
                  :rows="3"
                  maxlength="1000"
                  show-word-limit
                  resize="none"
                  placeholder="输入消息，Ctrl + Enter 发送"
                  @keydown.ctrl.enter="doSend"
                />
                <div class="chat-actions">
                  <el-button size="small" @click="loadMessages">刷新</el-button>
                  <el-button type="primary" size="small" :loading="sending" :disabled="!draft.trim()" @click="doSend">
                    发送
                  </el-button>
                </div>
              </div>
            </template>

            <el-empty v-else description="从左侧选择联系人开始沟通" />
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listContacts, listConversations, messageHistory,
  sendMessage, markConversationRead, unreadMessageCount
} from '@/api/message'

const contacts = ref([])
const conversations = ref([])
const messages = ref([])
const current = ref(null)
const draft = ref('')
const sending = ref(false)
const unread = ref(0)
const scrollRef = ref()
let timer = null

const unreadMap = computed(() => {
  const map = {}
  conversations.value.forEach((c) => { map[c.peerId] = c.unreadCount || 0 })
  return map
})

function unreadOf(userId) {
  return unreadMap.value[userId] || 0
}

function roleText(role) {
  return { STUDENT: '学生', TEACHER: '教师', PARENT: '家长' }[role] || role || '用户'
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 16)
}

async function loadContacts() {
  contacts.value = await listContacts()
  conversations.value = await listConversations()
  unread.value = await unreadMessageCount()
}

async function openChat(contact) {
  current.value = contact
  await loadMessages()
  if (unreadOf(contact.userId)) {
    await markConversationRead(contact.userId)
    await loadContacts()
  }
}

async function loadMessages() {
  if (!current.value) return
  messages.value = await messageHistory(current.value.userId, 200)
  await scrollToBottom()
}

async function scrollToBottom() {
  await nextTick()
  const el = scrollRef.value?.$el?.querySelector('.el-scrollbar__wrap')
  if (el) el.scrollTop = el.scrollHeight
}

async function doSend() {
  const text = draft.value.trim()
  if (!text || !current.value) return
  sending.value = true
  try {
    await sendMessage(current.value.userId, text)
    draft.value = ''
    await loadMessages()
    await loadContacts()
  } catch (e) {
    ElMessage.error(e.message || '发送失败')
  } finally {
    sending.value = false
  }
}

/** 轮询：拉取新消息与未读数 */
function startPolling() {
  timer = setInterval(async () => {
    try {
      if (current.value) await loadMessages()
      await loadContacts()
    } catch (e) {
      // 轮询失败不打断页面
    }
  }, 8000)
}

onMounted(async () => {
  await loadContacts()
  startPolling()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.msg-card :deep(.el-card__body) {
  padding: 12px;
}

.side {
  border-right: 1px solid #ebeef5;
  padding-right: 8px;
}

.side-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 8px;
}

.side-list {
  height: 520px;
}

.contact {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
}

.contact:hover {
  background: #f5f7fa;
}

.contact.active {
  background: #ecf5ff;
}

.contact-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.contact-name {
  font-size: 14px;
  font-weight: 600;
}

.contact-unread {
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #f56c6c;
  color: #fff;
  font-size: 12px;
  text-align: center;
}

.contact-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  margin-bottom: 8px;
}

.chat-body {
  height: 340px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
}

.msg-row {
  display: flex;
  margin-bottom: 12px;
}

.msg-row.mine {
  justify-content: flex-end;
}

.msg-bubble {
  max-width: 70%;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f4f4f5;
}

.msg-row.mine .msg-bubble {
  background: #ecf5ff;
}

.msg-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.msg-content {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.chat-input {
  margin-top: 12px;
}

.chat-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
