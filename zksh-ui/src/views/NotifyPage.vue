<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { createNotification, createReminderRule, listNotifications, listReminderRules, readNotification } from "../api";

const loading = ref(false);
const activeTab = ref("all");
const isRead = ref(null);
const reminders = ref([]);
const rules = ref([]);
const form = reactive({
  title: "",
  content: "",
  type: "system",
  notifyTime: ""
});
const ruleForm = reactive({
  title: "",
  content: "",
  type: "medication",
  intervalMinutes: 60
});

const unreadCount = computed(() => reminders.value.filter((x) => x.isRead !== 1).length);
const readCount = computed(() => reminders.value.filter((x) => x.isRead === 1).length);

function fmt(t) {
  if (!t) return null;
  const normalized = t.replace("T", " ");
  return normalized.length === 16 ? `${normalized}:00` : normalized;
}

async function queryList() {
  loading.value = true;
  try {
    const res = await listNotifications({ isRead: isRead.value });
    reminders.value = res.data.data || [];
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "查询通知失败");
  } finally {
    loading.value = false;
  }
}

function onTabChange(name) {
  if (name === "all") isRead.value = null;
  if (name === "unread") isRead.value = 0;
  if (name === "read") isRead.value = 1;
  queryList();
}

function rowClassName({ row }) {
  return row.isRead === 1 ? "" : "notify-unread-row";
}

async function createOne() {
  if (!form.title || !form.content) {
    ElMessage.warning("请先填写通知标题和内容");
    return;
  }
  loading.value = true;
  try {
    await createNotification({
      title: form.title,
      content: form.content,
      type: form.type,
      notifyTime: fmt(form.notifyTime)
    });
    ElMessage.success("创建成功");
    form.title = "";
    form.content = "";
    form.notifyTime = "";
    await queryList();
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "创建失败");
  } finally {
    loading.value = false;
  }
}

async function markRead(row) {
  await readNotification(row.id);
  ElMessage.success("已标记已读");
  await queryList();
}

async function queryRules() {
  try {
    const res = await listReminderRules();
    rules.value = res.data.data || [];
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "查询规则失败");
  }
}

async function createRule() {
  if (!ruleForm.title || !ruleForm.content) {
    ElMessage.warning("请先填写规则标题和内容");
    return;
  }
  try {
    await createReminderRule({ ...ruleForm });
    ElMessage.success("规则创建成功");
    ruleForm.title = "";
    ruleForm.content = "";
    ruleForm.intervalMinutes = 60;
    await queryRules();
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "规则创建失败");
  }
}

onMounted(() => {
  queryList();
  queryRules();
});
</script>

<template>
  <div class="module-page">
    <div class="module-hero">
      <div>
        <h2 class="module-title">提醒与服务通知</h2>
        <p class="page-subtitle">统一管理客户通知、提醒规则与执行状态，支持自动化健康服务触达。</p>
      </div>
      <div class="row-actions">
        <el-tag type="danger">未读 {{ unreadCount }}</el-tag>
        <el-tag type="info">规则 {{ rules.length }}</el-tag>
      </div>
    </div>
    <el-row :gutter="12" class="panel">
      <el-col :span="8"><el-input v-model="form.title" placeholder="通知标题" /></el-col>
      <el-col :span="10"><el-input v-model="form.content" placeholder="通知内容" /></el-col>
      <el-col :span="6"><el-input v-model="form.notifyTime" type="datetime-local" /></el-col>
    </el-row>
    <el-tabs v-model="activeTab" class="notify-tabs" @tab-change="onTabChange">
      <el-tab-pane name="all">
        <template #label>
          <el-badge :value="reminders.length" class="tab-badge">全部</el-badge>
        </template>
      </el-tab-pane>
      <el-tab-pane name="unread">
        <template #label>
          <el-badge :value="unreadCount" class="tab-badge">未读</el-badge>
        </template>
      </el-tab-pane>
      <el-tab-pane name="read">
        <template #label>
          <el-badge :value="readCount" class="tab-badge">已读</el-badge>
        </template>
      </el-tab-pane>
    </el-tabs>
    <div class="actions notify-actions">
      <el-button type="primary" :loading="loading" @click="createOne">新增通知</el-button>
      <el-button :loading="loading" @click="queryList">刷新</el-button>
    </div>
    <el-table :data="reminders" border v-loading="loading" class="panel-table" :row-class-name="rowClassName">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="content" label="内容" />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="notifyTime" label="提醒时间" width="180" class-name="notify-time-col" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.isRead === 1 ? 'success' : 'warning'">{{ scope.row.isRead === 1 ? "已读" : "未读" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="scope">
          <el-button link type="primary" :disabled="scope.row.isRead === 1" @click="markRead(scope.row)">标记已读</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">提醒规则（定时自动生成通知）</el-divider>
    <el-row :gutter="12" class="panel">
      <el-col :span="7"><el-input v-model="ruleForm.title" placeholder="规则标题" /></el-col>
      <el-col :span="9"><el-input v-model="ruleForm.content" placeholder="规则内容" /></el-col>
      <el-col :span="4"><el-input-number v-model="ruleForm.intervalMinutes" :min="1" :max="1440" /></el-col>
      <el-col :span="4"><el-button type="success" @click="createRule">新增规则</el-button></el-col>
    </el-row>
    <el-table :data="rules" border class="panel-table notify-rules-table">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="content" label="内容" />
      <el-table-column prop="intervalMinutes" label="间隔(分钟)" width="120" />
      <el-table-column prop="nextTriggerTime" label="下次触发时间" width="180" />
      <el-table-column label="状态" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.enabled === 1 ? 'success' : 'info'">{{ scope.row.enabled === 1 ? "启用" : "停用" }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.notify-tabs {
  margin-top: 8px;
}

.tab-badge :deep(.el-badge__content) {
  background: var(--primary);
}

.notify-actions {
  margin: 8px 0 4px;
}

.notify-rules-table {
  margin-top: 12px;
}

.panel-table {
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-md);
}

:deep(.notify-unread-row td:first-child) {
  border-left: 3px solid var(--primary);
}

:deep(.notify-unread-row td) {
  background: rgba(79, 110, 247, 0.02);
}

:deep(.notify-time-col .cell) {
  text-align: right;
  color: var(--gray-400);
}
</style>
