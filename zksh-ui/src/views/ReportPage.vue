<script setup>
import { computed, onBeforeUnmount, ref } from "vue";
import { ElMessage } from "element-plus";
import { createReportTask, getReportTaskStatus, listReportTasks } from "../api";

const accepted = ".jpg,.jpeg,.png,.pdf,.webp";
const fileRef = ref(null);
const selectedFile = ref(null);
const dragging = ref(false);
const uploading = ref(false);
const taskInfo = ref(null);
const pollTimer = ref(null);
const taskHistory = ref([]);

const stageList = [
  { key: "UPLOADED", title: "资料已接收", desc: "报告文件完成上传，任务进入队列" },
  { key: "OCR_DONE", title: "文字识别完成", desc: "OCR 正在提取检验项与基础信息" },
  { key: "INTERPRETED", title: "智能结构化解读", desc: "模型抽取指标并校验字段完整性" },
  { key: "SAVED", title: "健康数据入库", desc: "指标已同步到健康档案与趋势中心" },
  { key: "ALERTED", title: "风险规则评估", desc: "异常项触发告警并生成提醒内容" },
  { key: "NOTIFIED", title: "报告已交付", desc: "已完成通知推送，可查看报告结论" }
];

const stageIndexMap = Object.fromEntries(stageList.map((item, i) => [item.key, i]));
const stepOrder = ["INIT", "OCR", "EXTRACT", "PERSIST", "ALERT", "NOTIFY"];

const currentStageIndex = computed(() => {
  const stage = taskInfo.value?.stage;
  if (!stage || stageIndexMap[stage] == null) return 0;
  return stageIndexMap[stage];
});

const progressPercent = computed(() => {
  const base = ((currentStageIndex.value + 1) / stageList.length) * 100;
  if (taskInfo.value?.status === "FAILED") return Math.min(base, 92);
  if (taskInfo.value?.status === "SUCCEEDED") return 100;
  return Math.max(8, Math.round(base));
});

const statusMeta = computed(() => {
  const status = taskInfo.value?.status;
  if (status === "SUCCEEDED") return { label: "已完成", type: "success" };
  if (status === "FAILED") return { label: "处理失败", type: "danger" };
  if (status === "RUNNING") return { label: "处理中", type: "warning" };
  if (status === "QUEUED") return { label: "排队中", type: "info" };
  return { label: "待上传", type: "info" };
});

const canSubmit = computed(() => !!selectedFile.value && !uploading.value);

const timelineRows = computed(() => {
  const logs = taskInfo.value?.stepLogs || [];
  const latestMap = {};
  logs.forEach((log) => {
    latestMap[log.stepCode] = log;
  });
  return stepOrder
    .map((code) => latestMap[code])
    .filter(Boolean);
});

function pickFile(file) {
  if (!file) return;
  const ext = file.name.split(".").pop()?.toLowerCase();
  if (!["jpg", "jpeg", "png", "pdf", "webp"].includes(ext || "")) {
    ElMessage.warning("仅支持 jpg / png / webp / pdf 文件");
    return;
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning("文件大小不能超过 20MB");
    return;
  }
  selectedFile.value = file;
}

function onPick(event) {
  const file = event.target.files?.[0];
  pickFile(file);
}

function onDrop(event) {
  dragging.value = false;
  const file = event.dataTransfer?.files?.[0];
  pickFile(file);
}

function onDragOver() {
  dragging.value = true;
}

function onDragLeave() {
  dragging.value = false;
}

function triggerPick() {
  fileRef.value?.click();
}

function clearTimer() {
  if (pollTimer.value) {
    window.clearTimeout(pollTimer.value);
    pollTimer.value = null;
  }
}

async function pollTask(taskId) {
  clearTimer();
  try {
    const res = await getReportTaskStatus(taskId);
    taskInfo.value = res.data.data;
    const status = taskInfo.value?.status;
    if (status === "SUCCEEDED") {
      ElMessage.success("报告解读完成，已同步到健康档案");
      await loadTaskHistory();
      return;
    }
    if (status === "FAILED") {
      ElMessage.error(taskInfo.value?.errorMessage || "任务处理失败，请重试");
      return;
    }
    pollTimer.value = window.setTimeout(() => pollTask(taskId), 2200);
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || "任务状态查询失败");
  }
}

async function submitTask() {
  if (!selectedFile.value) {
    ElMessage.warning("请先选择报告文件");
    return;
  }
  uploading.value = true;
  try {
    const res = await createReportTask(selectedFile.value);
    taskInfo.value = res.data.data;
    ElMessage.success("报告已接收，正在进行智能解读");
    await loadTaskHistory();
    await pollTask(taskInfo.value.taskId);
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || "上传失败");
  } finally {
    uploading.value = false;
  }
}

async function loadTaskHistory() {
  try {
    const res = await listReportTasks(8);
    taskHistory.value = res.data.data || [];
  } catch (e) {
    // 历史数据失败不影响主流程
  }
}

function resetAll() {
  clearTimer();
  selectedFile.value = null;
  taskInfo.value = null;
  if (fileRef.value) {
    fileRef.value.value = "";
  }
}

loadTaskHistory();
onBeforeUnmount(() => clearTimer());
</script>

<template>
  <div class="report-page">
    <div class="report-hero">
      <div>
        <h2 class="report-title">医疗报告智能解读中心</h2>
        <p class="page-subtitle">
          支持检验单、体检报告自动识别，智能抽取关键指标并同步健康档案，异常项实时触发提醒。
        </p>
      </div>
      <div class="report-actions">
        <el-tag :type="statusMeta.type" effect="dark">{{ statusMeta.label }}</el-tag>
        <el-button plain @click="resetAll">重置任务</el-button>
      </div>
    </div>

    <div
      class="upload-zone"
      :class="{ dragging }"
      @drop.prevent="onDrop"
      @dragover.prevent="onDragOver"
      @dragleave.prevent="onDragLeave"
      @click="triggerPick"
    >
      <input ref="fileRef" class="hidden-input" type="file" :accept="accepted" @change="onPick" />
      <div class="upload-icon">+</div>
      <div class="upload-title">拖拽报告到此处，或点击选择文件</div>
      <div class="upload-desc">推荐格式：JPG / PNG / WEBP / PDF，单文件不超过 20MB</div>
      <div v-if="selectedFile" class="selected-file">
        <el-tag type="success" effect="light">已选择：{{ selectedFile.name }}</el-tag>
      </div>
    </div>

    <div class="actions" style="margin-top: 14px">
      <el-button type="primary" :loading="uploading" :disabled="!canSubmit" @click="submitTask">
        {{ uploading ? "处理中..." : "开始智能解读" }}
      </el-button>
      <el-button v-if="taskInfo?.taskId" text>任务编号：{{ taskInfo.taskId }}</el-button>
    </div>

    <div class="progress-board">
      <div class="progress-head">
        <span>处理进度</span>
        <span>{{ progressPercent }}%</span>
      </div>
      <el-progress :percentage="progressPercent" :stroke-width="10" :status="taskInfo?.status === 'FAILED' ? 'exception' : ''" />
      <div class="stage-grid">
        <div
          v-for="(stage, idx) in stageList"
          :key="stage.key"
          class="stage-card"
          :class="{
            active: idx <= currentStageIndex,
            pulse: taskInfo?.status === 'RUNNING' && idx === currentStageIndex
          }"
        >
          <div class="stage-index">{{ idx + 1 }}</div>
          <div class="stage-title">{{ stage.title }}</div>
          <div class="stage-desc">{{ stage.desc }}</div>
        </div>
      </div>
    </div>

    <el-alert
      v-if="taskInfo?.status === 'FAILED'"
      :title="taskInfo.errorMessage || '任务处理失败，请检查文件后重试'"
      type="error"
      :closable="false"
      style="margin-top: 14px"
      show-icon
    />

    <el-card v-if="taskInfo?.summary" class="result-card" shadow="never">
      <template #header>
        <div class="result-head">
          <span>解读结果摘要</span>
          <el-tag type="success">客户可读版</el-tag>
        </div>
      </template>
      <p class="result-text">{{ taskInfo.summary }}</p>
      <p class="result-tip">
        提示：详细指标已同步到健康档案，系统将根据阈值规则触发持续告警与后续提醒。
      </p>
    </el-card>

    <el-card shadow="never" class="report-history-card">
      <template #header>
        <div class="result-head">
          <span>近期报告任务</span>
          <el-tag type="info" effect="plain">自动记录</el-tag>
        </div>
      </template>
      <el-table :data="taskHistory" stripe>
        <el-table-column prop="taskId" label="任务编号" min-width="180" />
        <el-table-column prop="fileName" label="文件名称" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCEEDED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="stage" label="阶段" width="120" />
        <el-table-column prop="summary" label="解读摘要" min-width="260" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card v-if="timelineRows.length" shadow="never" class="report-history-card">
      <template #header>
        <div class="result-head">
          <span>流程时间线</span>
          <el-tag type="warning" effect="plain">可追踪步骤对象</el-tag>
        </div>
      </template>
      <el-timeline>
        <el-timeline-item
          v-for="(step, idx) in timelineRows"
          :key="`${step.stepCode}-${idx}`"
          :timestamp="step.createdAt"
          :type="step.status === 'SUCCESS' ? 'success' : step.status === 'FAILED' ? 'danger' : 'primary'"
        >
          <div class="timeline-title">
            {{ step.stepName }}
            <el-tag size="small" :type="step.status === 'SUCCESS' ? 'success' : step.status === 'FAILED' ? 'danger' : 'warning'">
              {{ step.status }}
            </el-tag>
          </div>
          <div class="timeline-detail">{{ step.detail || "系统处理中" }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<style scoped>
.upload-zone {
  border: 2px dashed var(--gray-200);
  border-radius: var(--radius-lg);
  min-height: 200px;
  padding: 36px 24px;
  text-align: center;
  background: var(--white);
  transition: var(--transition-base);
  cursor: pointer;
}

.upload-zone:hover,
.upload-zone.dragging {
  border-color: var(--primary);
  background: var(--primary-light);
  box-shadow: 0 0 0 3px rgba(79, 110, 247, 0.1);
}

.hidden-input {
  display: none;
}

.upload-icon {
  width: 52px;
  height: 52px;
  margin: 0 auto 12px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gray-100);
  color: var(--gray-500);
  font-size: 30px;
}

.upload-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}

.upload-desc {
  margin-top: 8px;
  color: var(--color-text-secondary);
}

.selected-file {
  margin-top: 16px;
}

.progress-board {
  padding: 20px 24px;
  border-radius: var(--radius-lg);
  background: var(--white);
  box-shadow: var(--shadow-md);
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.stage-grid {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stage-card {
  padding: 12px;
  border: 1px solid var(--gray-100);
  border-radius: var(--radius-md);
  background: var(--white);
  transition: var(--transition-base);
}

.stage-card.active {
  border-color: rgba(79, 110, 247, 0.24);
  background: var(--primary-light);
}

.stage-card.pulse {
  animation: pulse-border 1.4s ease infinite;
}

.stage-index {
  color: var(--primary);
  font-weight: 600;
}

.stage-title {
  margin-top: 6px;
  font-weight: 600;
}

.stage-desc {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.result-text {
  line-height: 1.8;
  color: var(--color-text);
}

.result-tip,
.timeline-detail {
  color: var(--color-text-secondary);
}

.timeline-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

@media (max-width: 960px) {
  .stage-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
