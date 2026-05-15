<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElNotification } from "element-plus";
import { listNotifications, listUnreadNotificationChanges } from "../api";

const route = useRoute();
const router = useRouter();
const initializedNotify = ref(false);
let notifyTimer = null;

function onSelect(index) {
  router.push(index);
}

function logout() {
  localStorage.removeItem("zksh_token");
  router.push("/auth");
}

function getSeenNotifyIds() {
  const raw = sessionStorage.getItem("zksh_seen_notify_ids");
  if (!raw) return new Set();
  try {
    const arr = JSON.parse(raw);
    return new Set(Array.isArray(arr) ? arr : []);
  } catch {
    return new Set();
  }
}

function saveSeenNotifyIds(set) {
  const arr = Array.from(set).slice(-300);
  sessionStorage.setItem("zksh_seen_notify_ids", JSON.stringify(arr));
}

function getLastNotifyId() {
  const raw = sessionStorage.getItem("zksh_last_notify_id");
  const n = Number(raw || 0);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

function saveLastNotifyId(id) {
  if (!id) return;
  sessionStorage.setItem("zksh_last_notify_id", String(id));
}

async function pollNotifications() {
  const token = localStorage.getItem("zksh_token");
  if (!token) return;
  try {
    let unread = [];
    const seen = getSeenNotifyIds();
    let newOnes = [];
    if (initializedNotify.value) {
      const lastId = getLastNotifyId();
      const deltaRes = await listUnreadNotificationChanges(lastId, 50);
      newOnes = (deltaRes?.data?.data || []).filter((item) => item?.id && !seen.has(item.id));
      unread = newOnes;
    } else {
      const res = await listNotifications({ isRead: 0 });
      unread = res?.data?.data || [];
      newOnes = [];
    }

    if (initializedNotify.value) {
      const count = newOnes.length;
      if (count > 0) {
        const preview = newOnes
          .slice(0, 3)
          .map((n) => `• ${n.title || "新通知"}：${n.content || "您有一条新的健康提醒"}`)
          .join("\n");
        ElNotification({
          title: `收到 ${count} 条新通知`,
          message: preview + (count > 3 ? `\n...还有 ${count - 3} 条` : ""),
          type: newOnes.some((x) => x.type?.includes("health_alert")) ? "warning" : "info",
          duration: 8000,
          offset: 72
        });
      }
    } else {
      unread.slice(0, 50).forEach((n) => {
        if (n?.id) {
          seen.add(n.id);
        }
      });
    }

    [...unread, ...newOnes].forEach((n) => {
      if (n?.id) seen.add(n.id);
      saveLastNotifyId(n.id);
    });
    saveSeenNotifyIds(seen);
    initializedNotify.value = true;
  } catch {
    // 通知轮询失败不影响主流程
  }
}

onMounted(async () => {
  await pollNotifications();
  notifyTimer = window.setInterval(() => {
    pollNotifications();
  }, 10000);
});

onBeforeUnmount(() => {
  if (notifyTimer) {
    clearInterval(notifyTimer);
    notifyTimer = null;
  }
});
</script>

<template>
  <el-container class="layout">
    <el-aside width="260px" class="aside">
      <div class="logo-wrap">
        <div class="logo">智康守护</div>
        <div class="logo-sub">Clinical Intelligence Platform</div>
      </div>
      <el-menu :default-active="route.path" @select="onSelect">
        <el-menu-item index="/user">客户档案中心</el-menu-item>
        <el-menu-item index="/health">健康指标档案</el-menu-item>
        <el-menu-item index="/notify">提醒与服务通知</el-menu-item>
        <el-menu-item index="/ai">AI健康咨询台</el-menu-item>
        <el-menu-item index="/report">医疗报告解读</el-menu-item>
        <el-menu-item index="/knowledge">知识库运营台</el-menu-item>
      </el-menu>
      <div class="logout-wrap">
        <div class="user-card">
          <div class="user-avatar">管</div>
          <div class="user-meta">
            <div class="user-name">健康管家</div>
            <div class="user-role">运营账号</div>
          </div>
        </div>
        <el-button type="danger" plain class="logout-btn" @click="logout">退出登录</el-button>
      </div>
    </el-aside>
    <el-main class="main">
      <div class="topbar">
        <div>
          <h1 class="page-title">智能家庭医生工作台</h1>
          <div class="page-subtitle">用户健康管理 · 通知提醒 · AI咨询</div>
        </div>
        <div class="topbar-tools">
          <el-input placeholder="搜索客户、通知、报告..." class="topbar-search" />
          <div class="bell-dot">!</div>
          <div class="topbar-avatar">卢</div>
        </div>
      </div>
      <el-card shadow="hover">
        <RouterView />
      </el-card>
    </el-main>
  </el-container>
</template>

<style scoped>
.aside {
  display: flex;
  flex-direction: column;
  padding: 0 12px 16px;
  background: #18181b;
}

.logo-wrap {
  height: 64px;
  padding: 12px 8px;
  margin-bottom: 8px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
}

.logo {
  color: #f8fafc;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.logo-sub {
  color: #94a3b8;
  font-size: 12px;
}

:deep(.el-menu) {
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #cbd5e1;
  --el-menu-active-color: #ffffff;
}

:deep(.el-menu-item) {
  position: relative;
  height: 44px;
  line-height: 44px;
  margin: 4px 0;
  border-radius: 10px;
  font-weight: 500;
  color: #a1a1aa;
  transition: var(--transition-base);
}

:deep(.el-menu-item)::before {
  content: "";
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 2px;
  background: transparent;
}

:deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #f4f4f5;
}

:deep(.el-menu-item.is-active) {
  background: rgba(79, 110, 247, 0.16);
  color: #ffffff;
}

:deep(.el-menu-item.is-active)::before {
  background: var(--color-primary-light);
}

.logout-wrap {
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.08);
  color: #e2e8f0;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(99, 102, 241, 0.35);
  font-size: 14px;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
}

.user-role {
  font-size: 12px;
  color: #94a3b8;
}

.logout-btn {
  width: 100%;
  margin-top: 12px;
}

.topbar {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding: 0 4px;
  border-bottom: 1px solid var(--gray-100);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.05);
}

.topbar-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topbar-search {
  width: 280px;
}

.bell-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--danger-light);
  color: var(--danger);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
}

.topbar-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--primary-light);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}
</style>
