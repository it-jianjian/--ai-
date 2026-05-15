<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createAiSession,
  deleteAiSession,
  getHealthSummary,
  listAiSessionMessages,
  listAiSessions,
  listNotifications,
  PENDING_REPORT_QUESTION_KEY,
  renameAiSession,
  sendAiMessageStream
} from "../api";

const question = ref("");
const loading = ref(false);
const sessions = ref([]);
const messages = ref([]);
const currentSessionId = ref(null);
const chatBoxRef = ref(null);
const dashboard = ref({
  sessionCount: 0,
  messageCount: 0,
  referenceCount: 0,
  unreadNotifyCount: 0,
  abnormalRecords: 0,
  latestMetricType: "-"
});
let metricsTimer = null;

function mapRole(role) {
  const upper = String(role || "").toUpperCase();
  if (upper.includes("USER")) return "user";
  return "assistant";
}

function buildSessionTitle(text) {
  const clean = (text || "").trim();
  if (!clean) return "新会话";
  return clean.length > 18 ? `${clean.slice(0, 18)}...` : clean;
}

function formatTime(ts) {
  if (!ts) return "";
  const d = new Date(ts);
  const hh = `${d.getHours()}`.padStart(2, "0");
  const mm = `${d.getMinutes()}`.padStart(2, "0");
  return `${hh}:${mm}`;
}

function parseAssistantContent(content) {
  const rawLines = String(content || "").split(/\r?\n/);
  const blocks = [];
  for (let i = 0; i < rawLines.length; i += 1) {
    const line = rawLines[i].trim();
    if (!line) continue;
    if (line.startsWith("### ")) {
      blocks.push({ type: "heading", level: 3, text: line.slice(4).trim() });
      continue;
    }
    if (line.startsWith("## ")) {
      blocks.push({ type: "heading", level: 2, text: line.slice(3).trim() });
      continue;
    }
    if (line.startsWith("# ")) {
      blocks.push({ type: "heading", level: 1, text: line.slice(2).trim() });
      continue;
    }
    if (/^\d+\.\s+/.test(line)) {
      const items = [];
      let j = i;
      while (j < rawLines.length && /^\d+\.\s+/.test(rawLines[j].trim())) {
        items.push(rawLines[j].trim().replace(/^\d+\.\s+/, ""));
        j += 1;
      }
      blocks.push({ type: "ordered-list", items });
      i = j - 1;
      continue;
    }
    if (/^[-*]\s+/.test(line)) {
      const items = [];
      let j = i;
      while (j < rawLines.length && /^[-*]\s+/.test(rawLines[j].trim())) {
        items.push(rawLines[j].trim().replace(/^[-*]\s+/, ""));
        j += 1;
      }
      blocks.push({ type: "unordered-list", items });
      i = j - 1;
      continue;
    }
    blocks.push({ type: "paragraph", text: line });
  }
  return blocks;
}

function updateMessageMetrics() {
  dashboard.value.messageCount = messages.value.length;
  dashboard.value.referenceCount = messages.value
    .filter((m) => m.role === "assistant")
    .reduce((sum, m) => sum + (m.references?.length || 0), 0);
}

async function refreshSharedMetrics() {
  try {
    const [summaryRes, unreadRes] = await Promise.all([
      getHealthSummary(),
      listNotifications({ isRead: 0 })
    ]);
    const summary = summaryRes?.data?.data || {};
    const unreadList = unreadRes?.data?.data || [];
    dashboard.value.abnormalRecords = summary.abnormalRecords || 0;
    dashboard.value.latestMetricType = summary.latestMetricType || "-";
    dashboard.value.unreadNotifyCount = unreadList.length;
  } catch (e) {
    // 跨模块指标刷新失败不阻断主流程
  }
}

async function scrollToBottom() {
  await nextTick();
  const el = chatBoxRef.value;
  if (!el) return;
  el.scrollTop = el.scrollHeight;
}

async function loadSessions(preserveCurrent = true) {
  const prevSessionId = currentSessionId.value;
  const res = await listAiSessions();
  sessions.value = res?.data?.data || [];
  dashboard.value.sessionCount = sessions.value.length;
  if (!sessions.value.length) {
    currentSessionId.value = null;
    messages.value = [];
    updateMessageMetrics();
    return;
  }
  if (preserveCurrent && prevSessionId && sessions.value.some((s) => s.sessionId === prevSessionId)) {
    currentSessionId.value = prevSessionId;
    return;
  }
  currentSessionId.value = sessions.value[0].sessionId;
}

async function loadMessages() {
  if (!currentSessionId.value) {
    messages.value = [];
    updateMessageMetrics();
    return;
  }
  const res = await listAiSessionMessages(currentSessionId.value);
  const list = res?.data?.data || [];
  messages.value = list.map((item) => ({
    role: mapRole(item.role),
    content: item.content || "",
    createdAt: item.createdAt,
    references: item.references || []
  }));
  updateMessageMetrics();
  await scrollToBottom();
}

async function switchSession(sessionId) {
  if (loading.value || currentSessionId.value === sessionId) return;
  currentSessionId.value = sessionId;
  await loadMessages();
}

async function newSession() {
  if (loading.value) return;
  const res = await createAiSession({ title: "新会话" });
  const session = res?.data?.data;
  if (!session) return;
  await loadSessions(false);
  currentSessionId.value = session.sessionId;
  messages.value = [];
  updateMessageMetrics();
}

async function ensureSession(questionText) {
  if (currentSessionId.value) return currentSessionId.value;
  const res = await createAiSession({ title: buildSessionTitle(questionText) });
  const session = res?.data?.data;
  if (!session) throw new Error("创建会话失败");
  await loadSessions(false);
  currentSessionId.value = session.sessionId;
  return currentSessionId.value;
}

async function promptRename(session) {
  try {
    const { value } = await ElMessageBox.prompt("请输入新的会话名称", "重命名会话", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      inputValue: session.title || "",
      inputPattern: /^.{1,100}$/,
      inputErrorMessage: "名称长度需在 1-100 个字符"
    });
    await renameAiSession(session.sessionId, value.trim());
    ElMessage.success("会话名称已更新");
    await loadSessions(true);
  } catch (e) {
    if (e !== "cancel" && e !== "close") {
      ElMessage.error(e?.response?.data?.message || "重命名失败");
    }
  }
}

async function removeSession(session) {
  try {
    await ElMessageBox.confirm("删除后会话消息不可恢复，是否继续？", "删除会话", {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning"
    });
    await deleteAiSession(session.sessionId);
    if (currentSessionId.value === session.sessionId) {
      currentSessionId.value = null;
      messages.value = [];
      updateMessageMetrics();
    }
    await loadSessions(false);
    await loadMessages();
    ElMessage.success("会话已删除");
  } catch (e) {
    if (e !== "cancel" && e !== "close") {
      ElMessage.error(e?.response?.data?.message || "删除失败");
    }
  }
}

function quickAskHealth() {
  question.value = `请结合我的健康档案最近指标（当前异常记录 ${dashboard.value.abnormalRecords} 条，最新指标 ${dashboard.value.latestMetricType}），给我一个本周健康建议计划。`;
}

function quickAskReminder() {
  question.value = `我当前还有 ${dashboard.value.unreadNotifyCount} 条未读提醒，请帮我按优先级整理今天必须处理的健康事项。`;
}

async function send() {
  const content = question.value.trim();
  if (!content || loading.value) return;
  const sessionId = await ensureSession(content);
  messages.value.push({ role: "user", content, references: [] });
  const aiMsg = { role: "assistant", content: "", references: [] };
  messages.value.push(aiMsg);
  updateMessageMetrics();
  await scrollToBottom();
  question.value = "";
  loading.value = true;

  try {
    await sendAiMessageStream(sessionId, content, {
      onMessage(payload) {
        const data = payload?.data;
        if (!data) return;
        if (data.riskLevel) {
          if (data.answer && data.answer.length > (aiMsg.content || "").length) {
            aiMsg.content = data.answer;
          }
          aiMsg.references = data.references || [];
          updateMessageMetrics();
          return;
        }
        aiMsg.content += data.answer || "";
        scrollToBottom();
      }
    });
    if (!aiMsg.content) {
      aiMsg.content = "AI暂未返回内容，请稍后再试。";
    }
    await loadSessions(true);
    await refreshSharedMetrics();
  } catch (e) {
    aiMsg.content = "请求失败，请检查后端服务或登录状态。";
    ElMessage.error(e?.message || "发送失败");
  } finally {
    loading.value = false;
    updateMessageMetrics();
    await scrollToBottom();
  }
}

function consumePendingReportQuestion() {
  const raw = sessionStorage.getItem(PENDING_REPORT_QUESTION_KEY);
  if (!raw) return;
  sessionStorage.removeItem(PENDING_REPORT_QUESTION_KEY);
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return;
  }
  const q = String(parsed?.question || "").trim();
  if (!q) return;
  const maxAgeMs = 60 * 60 * 1000;
  if (parsed.at != null && Date.now() - Number(parsed.at) > maxAgeMs) return;
  const taskId = parsed.taskId != null ? String(parsed.taskId) : "";
  const prefix = taskId
    ? `我刚上传并完成报告解读（任务编号 ${taskId}），健康档案应已同步。请结合最新档案回答：`
    : `我刚上传并完成报告解读，健康档案应已同步。请结合最新档案回答：`;
  question.value = `${prefix}${q}`;
  ElMessage.info("已从报告解读带入问题，可修改后再点发送");
}

onMounted(async () => {
  try {
    await loadSessions(false);
    await loadMessages();
    await refreshSharedMetrics();
    consumePendingReportQuestion();
    metricsTimer = setInterval(() => {
      refreshSharedMetrics();
    }, 30000);
  } catch (e) {
    ElMessage.warning("加载会话失败");
  }
});

onBeforeUnmount(() => {
  if (metricsTimer) {
    clearInterval(metricsTimer);
    metricsTimer = null;
  }
});
</script>

<template>
  <div class="ai-page">
    <div class="session-panel">
      <div class="session-header">
        <h3>会话历史</h3>
        <el-button type="primary" plain size="small" @click="newSession">新建会话</el-button>
      </div>
      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.sessionId"
          class="session-item"
          :class="{ active: s.sessionId === currentSessionId }"
          @click="switchSession(s.sessionId)"
        >
          <div class="session-title-row">
            <div class="session-title">{{ s.title }}</div>
            <div class="session-actions">
              <el-button link size="small" @click.stop="promptRename(s)">命名</el-button>
              <el-button link type="danger" size="small" @click.stop="removeSession(s)">删除</el-button>
            </div>
          </div>
          <div class="session-time">{{ formatTime(s.lastMessageAt || s.updatedAt) }} · 主题会自动提炼</div>
        </div>
      </div>
    </div>

    <div class="chat-panel">
      <div class="module-hero">
        <div>
          <h2 class="module-title">AI健康咨询台</h2>
          <p class="page-subtitle">结合健康档案、通知与知识库内容，提供可追溯的结构化健康建议。</p>
        </div>
      </div>
      <div class="metrics-row">
        <el-tag type="info">会话数：{{ dashboard.sessionCount }}</el-tag>
        <el-tag type="success">当前消息：{{ dashboard.messageCount }}</el-tag>
        <el-tag type="warning">引用片段：{{ dashboard.referenceCount }}</el-tag>
        <el-tag type="danger">未读提醒：{{ dashboard.unreadNotifyCount }}</el-tag>
        <el-tag>异常记录：{{ dashboard.abnormalRecords }}</el-tag>
      </div>
      <div class="quick-actions">
        <el-button size="small" @click="quickAskHealth">结合健康档案给建议</el-button>
        <el-button size="small" @click="quickAskReminder">整理今日提醒优先级</el-button>
      </div>
      <div ref="chatBoxRef" class="chat-box">
        <div v-for="(m, i) in messages" :key="i" class="chat-item" :class="m.role">
          <b>{{ m.role === "user" ? "你" : "AI" }}：</b>
          <template v-if="m.role === 'assistant'">
            <div class="assistant-content">
              <div v-for="(block, blockIdx) in parseAssistantContent(m.content)" :key="`${i}-${blockIdx}`">
                <h3 v-if="block.type === 'heading' && block.level === 1" class="md-title">{{ block.text }}</h3>
                <h4 v-else-if="block.type === 'heading' && block.level === 2" class="md-subtitle">{{ block.text }}</h4>
                <h5 v-else-if="block.type === 'heading' && block.level === 3" class="md-small-title">{{ block.text }}</h5>
                <ol v-else-if="block.type === 'ordered-list'" class="md-list">
                  <li v-for="(item, liIdx) in block.items" :key="`${blockIdx}-o-${liIdx}`">{{ item }}</li>
                </ol>
                <ul v-else-if="block.type === 'unordered-list'" class="md-list">
                  <li v-for="(item, liIdx) in block.items" :key="`${blockIdx}-u-${liIdx}`">{{ item }}</li>
                </ul>
                <p v-else class="md-paragraph">{{ block.text }}</p>
              </div>
            </div>
          </template>
          <template v-else>{{ m.content }}</template>
          <div v-if="m.role === 'assistant' && m.references?.length" class="refs">
            <div class="refs-title">参考来源：</div>
            <div v-for="(refItem, idx) in m.references" :key="`${i}-${idx}`" class="ref-item">
              [{{ idx + 1 }}] 文档{{ refItem.docId }} {{ refItem.title }} - {{ refItem.snippet }}
            </div>
          </div>
        </div>
      </div>
      <div class="composer">
        <el-input
          v-model="question"
          placeholder="请输入你的健康问题"
          :disabled="loading"
          @keyup.enter="send"
        />
        <el-button type="primary" :loading="loading" @click="send">发送</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-page {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 24px;
  min-height: 68vh;
}

.session-panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 16px;
  background: var(--color-card);
  box-shadow: var(--shadow-sm);
}

.session-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.session-header h3 {
  margin: 0;
  font-size: 15px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-item {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: #fff;
  padding: 12px;
  cursor: pointer;
  transition: var(--transition-base);
}

.session-item:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
  background: #f8fafc;
}

.session-item.active {
  border-color: var(--primary);
  background: var(--gray-100);
}

.session-title {
  font-size: 14px;
  line-height: 1.4;
}

.session-title-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.session-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.session-time {
  margin-top: 4px;
  color: var(--gray-400);
  font-size: 11px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.chat-panel {
  display: flex;
  flex-direction: column;
}

.chat-box {
  margin-top: 12px;
  min-height: 52vh;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 20px;
  overflow-y: auto;
  background: var(--white);
}

.metrics-row {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-actions {
  margin-top: 10px;
}

.chat-item {
  margin-bottom: 16px;
  max-width: 720px;
  border-radius: 14px;
  padding: 10px 12px;
  line-height: 1.8;
  white-space: pre-wrap;
  box-shadow: var(--shadow-sm);
}

.chat-item.user {
  margin-left: auto;
  border-bottom-right-radius: 6px;
  background: var(--primary);
  color: #ffffff;
}

.chat-item.assistant {
  margin-right: auto;
  border-bottom-left-radius: 6px;
  background: var(--gray-100);
  color: var(--gray-700);
}

.assistant-content {
  margin-top: 6px;
}

.md-title {
  margin: 10px 0 6px;
  font-size: 18px;
}

.md-subtitle {
  margin: 8px 0 5px;
  font-size: 16px;
}

.md-small-title {
  margin: 6px 0 4px;
  font-size: 14px;
}

.md-list {
  margin: 4px 0 8px 20px;
}

.md-paragraph {
  margin: 4px 0;
}

.refs {
  margin-top: 8px;
  padding: 8px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  color: var(--color-text-secondary);
}

.refs-title {
  font-weight: 600;
  margin-bottom: 4px;
}

.ref-item {
  line-height: 1.6;
}

.composer {
  margin-top: 16px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--gray-100);
}

.composer :deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: 22px !important;
}

.composer :deep(.el-button) {
  width: 40px;
  height: 40px;
  padding: 0 !important;
  border-radius: 50% !important;
}
</style>
