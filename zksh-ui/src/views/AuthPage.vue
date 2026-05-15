<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { login, register, sendCode } from "../api";

const router = useRouter();
const tab = ref("login");
const loading = ref(false);
const phone = ref("");
const code = ref("");
const nickname = ref("");

async function handleSendCode() {
  if (!/^1\d{10}$/.test(phone.value)) {
    ElMessage.warning("请输入正确手机号");
    return;
  }
  loading.value = true;
  try {
    const res = await sendCode(phone.value);
    ElMessage.success(res.data.message || "验证码已发送");
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "发送失败");
  } finally {
    loading.value = false;
  }
}

async function handleLogin() {
  loading.value = true;
  try {
    const res = await login(phone.value, code.value);
    localStorage.setItem("zksh_token", res.data.data.token);
    ElMessage.success("登录成功");
    router.push("/user");
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "登录失败");
  } finally {
    loading.value = false;
  }
}

async function handleRegister() {
  loading.value = true;
  try {
    const res = await register(phone.value, code.value, nickname.value);
    localStorage.setItem("zksh_token", res.data.data.token);
    ElMessage.success("注册成功");
    router.push("/user");
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "注册失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="auth-shell">
      <div class="brand-panel">
        <h1>智康守护</h1>
        <p>Clinical Intelligence Platform</p>
        <div class="brand-desc">连接健康档案、通知提醒、AI 咨询和医疗报告，构建专业的一体化健康管理平台。</div>
        <div class="brand-features">
          <div class="feature-item"><span class="dot"></span>全链路健康档案</div>
          <div class="feature-item"><span class="dot"></span>智能风险提醒</div>
          <div class="feature-item"><span class="dot"></span>医疗报告一键解读</div>
        </div>
      </div>
      <el-card class="auth-card" shadow="never">
        <h2 class="auth-title">欢迎回来</h2>
        <p class="page-subtitle auth-subtitle">请登录或注册以继续使用平台</p>
        <el-tabs v-model="tab" class="auth-tabs">
          <el-tab-pane label="登录" name="login" />
          <el-tab-pane label="注册" name="register" />
        </el-tabs>
        <el-form label-width="72px" class="auth-form">
          <el-form-item label="手机号">
            <el-input v-model="phone" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item label="验证码">
            <el-input v-model="code" placeholder="请输入验证码" />
          </el-form-item>
          <el-form-item label="昵称" v-if="tab === 'register'">
            <el-input v-model="nickname" placeholder="选填" />
          </el-form-item>
        </el-form>
        <div class="actions">
          <el-button :loading="loading" @click="handleSendCode">发送验证码</el-button>
          <el-button v-if="tab === 'login'" type="primary" :loading="loading" @click="handleLogin">登录</el-button>
          <el-button v-else type="primary" :loading="loading" @click="handleRegister">注册并登录</el-button>
        </div>
        <div class="or-divider">或</div>
        <div class="social-row">
          <button type="button" class="social-btn">微</button>
          <button type="button" class="social-btn">Q</button>
          <button type="button" class="social-btn">A</button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.auth-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: #f1f5f9;
}

.auth-shell {
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: 1fr 460px;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: var(--shadow-md);
}

.brand-panel {
  padding: 48px;
  color: #ffffff;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-hover) 100%);
}

.brand-panel h1 {
  margin: 0 0 12px;
  font-size: 36px;
  font-weight: 600;
}

.brand-panel p {
  margin: 0;
  color: rgba(255, 255, 255, 0.9);
}

.brand-desc {
  margin-top: 32px;
  max-width: 360px;
  color: rgba(255, 255, 255, 0.86);
}

.brand-features {
  margin-top: 32px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
}

.auth-card {
  border-radius: 0 !important;
  padding: 8px;
}

.auth-title {
  margin: 0;
  font-size: 32px;
  font-weight: 600;
}

.auth-subtitle {
  margin-bottom: 20px;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.auth-form :deep(.el-input__wrapper) {
  min-height: 44px;
}

.auth-tabs {
  margin-bottom: 8px;
}

.auth-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: var(--color-border);
}

.auth-tabs :deep(.el-tabs__active-bar) {
  height: 2px;
  background: var(--primary);
  transition: var(--transition-base);
}

.auth-tabs :deep(.el-tabs__item) {
  font-weight: 500;
}

.or-divider {
  margin: 24px 0 12px;
  text-align: center;
  color: var(--gray-400);
  font-size: 12px;
  position: relative;
}

.or-divider::before,
.or-divider::after {
  content: "";
  position: absolute;
  top: 50%;
  width: calc(50% - 20px);
  height: 1px;
  background: var(--gray-100);
}

.or-divider::before {
  left: 0;
}

.or-divider::after {
  right: 0;
}

.social-row {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.social-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--gray-200);
  background: var(--white);
  color: var(--gray-500);
  cursor: pointer;
  transition: var(--transition-base);
}

.social-btn:hover {
  background: var(--gray-100);
}

@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }
}
</style>
