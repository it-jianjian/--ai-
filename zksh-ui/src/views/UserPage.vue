<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { getProfile, updateProfile } from "../api";

const loading = ref(false);
const profileLoaded = ref(false);

const profile = reactive({
  id: null,
  phone: "",
  nickname: "",
  gender: "",
  age: null,
  height: null,
  weight: null
});

const profileCompletion = computed(() => {
  const fields = [profile.nickname, profile.gender, profile.age, profile.height, profile.weight];
  const filled = fields.filter((x) => x !== null && x !== undefined && String(x).trim() !== "").length;
  return Math.round((filled / fields.length) * 100);
});

async function loadProfile() {
  try {
    const res = await getProfile();
    Object.assign(profile, res.data.data);
    profileLoaded.value = true;
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "获取资料失败");
  }
}

async function saveProfile() {
  loading.value = true;
  try {
    const res = await updateProfile({
      nickname: profile.nickname || null,
      gender: profile.gender || null,
      age: profile.age ? Number(profile.age) : null,
      height: profile.height ? Number(profile.height) : null,
      weight: profile.weight ? Number(profile.weight) : null
    });
    Object.assign(profile, res.data.data);
    ElMessage.success("资料保存成功");
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "保存失败");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadProfile();
});
</script>

<template>
  <div class="module-page">
    <div class="module-hero">
      <div>
        <h2 class="module-title">客户档案中心</h2>
        <p class="page-subtitle">维护客户基础画像与身体参数，为健康趋势和 AI 建议提供可信输入。</p>
      </div>
      <el-tag type="info" effect="dark">服务级档案管理</el-tag>
    </div>
    <el-skeleton :rows="4" animated v-if="!profileLoaded" />
    <template v-else>
      <el-row :gutter="16" class="stats-row">
        <el-col :span="8"><div class="panel stat-card"><el-statistic title="用户ID" :value="profile.id || 0" /></div></el-col>
        <el-col :span="8"><div class="panel stat-card"><el-statistic title="手机号" :value="profile.phone || '-'" /></div></el-col>
        <el-col :span="8"><div class="panel stat-card"><el-statistic title="资料完整度" :value="profileCompletion" suffix="%" /></div></el-col>
      </el-row>
      <div class="panel completion-panel">
        <div class="completion-head">
          <span>资料完整度</span>
          <span>{{ profileCompletion }}%</span>
        </div>
        <el-progress :percentage="profileCompletion" :stroke-width="10" />
      </div>
      <el-divider content-position="left">个人资料</el-divider>
      <el-form label-width="90px" class="panel">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="昵称"><el-input v-model="profile.nickname" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="性别"><el-input v-model="profile.gender" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="年龄"><el-input v-model="profile.age" type="number" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="身高"><el-input v-model="profile.height" type="number" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="体重"><el-input v-model="profile.weight" type="number" /></el-form-item></el-col>
        </el-row>
        <el-button type="primary" :loading="loading" @click="saveProfile">保存资料</el-button>
      </el-form>
    </template>
  </div>
</template>

<style scoped>
.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  min-height: 100px;
  transition: var(--transition-base);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.completion-panel {
  margin-bottom: 12px;
}

.completion-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  color: var(--gray-500);
  font-weight: 500;
}

:deep(.el-statistic__number) {
  font-weight: 600;
  color: var(--gray-900);
  font-variant-numeric: tabular-nums;
}

:deep(.el-form-item__label) {
  color: var(--gray-500);
}
</style>
