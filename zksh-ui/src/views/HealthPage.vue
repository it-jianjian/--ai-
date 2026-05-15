<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  createHealthRecord,
  deleteHealthRecord,
  getHealthOptions,
  getHealthSummary,
  getHealthTrend,
  listHealthRecords,
  updateHealthRecord
} from "../api";

const loading = ref(false);
const dialogVisible = ref(false);
const dialogMode = ref("create");
const records = ref([]);
const trendData = ref([]);
const summary = ref({ totalRecords: 0, abnormalRecords: 0, latestMetricType: "-", latestMetricValue: "-", latestRecordTime: "-" });
const options = ref([]);
const defaultOptions = [
  { code: "blood_pressure", name: "血压", scene: "vital", units: ["mmHg"] },
  { code: "heart_rate", name: "心率", scene: "vital", units: ["bpm"] },
  { code: "blood_glucose", name: "血糖", scene: "lab", units: ["mmol/L"] },
  { code: "cholesterol", name: "胆固醇", scene: "lab", units: ["mmol/L"] },
  { code: "weight", name: "体重", scene: "lifestyle", units: ["kg"] },
  { code: "sleep_duration", name: "睡眠时长", scene: "lifestyle", units: ["h"] },
  { code: "steps", name: "步数", scene: "lifestyle", units: ["steps"] }
];

const form = reactive({
  id: null,
  metricType: "blood_pressure",
  metricValue: "",
  unit: "",
  recordTime: "",
  remark: ""
});

const query = reactive({
  metricType: "",
  startTime: "",
  endTime: ""
});

function fmt(t) {
  if (!t) return null;
  const normalized = t.replace("T", " ");
  return normalized.length === 16 ? `${normalized}:00` : normalized;
}

function unitOptionsByType(type) {
  const hit = options.value.find((x) => x.code === type);
  return hit?.units || [];
}

function typeName(type) {
  return options.value.find((x) => x.code === type)?.name || type;
}

function onTypeChange(type) {
  const units = unitOptionsByType(type);
  form.unit = units[0] || "";
}

async function createRecord() {
  loading.value = true;
  try {
    await createHealthRecord({
      ...form,
      recordTime: fmt(form.recordTime)
    });
    ElMessage.success("新增成功");
    resetForm();
    dialogVisible.value = false;
    await refreshAll();
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "新增失败");
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  form.id = null;
  form.metricType = options.value[0]?.code || "blood_pressure";
  form.metricValue = "";
  form.unit = unitOptionsByType(form.metricType)[0] || "";
  form.recordTime = "";
  form.remark = "";
}

async function queryRecords() {
  loading.value = true;
  try {
    const res = await listHealthRecords({
      metricType: query.metricType || undefined,
      startTime: fmt(query.startTime),
      endTime: fmt(query.endTime)
    });
    records.value = res.data.data || [];
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "查询失败");
  } finally {
    loading.value = false;
  }
}

async function queryTrend() {
  if (!query.metricType) {
    ElMessage.warning("趋势查询请先选择指标类型");
    return;
  }
  loading.value = true;
  try {
    const res = await getHealthTrend({
      metricType: query.metricType,
      startTime: fmt(query.startTime),
      endTime: fmt(query.endTime)
    });
    trendData.value = res.data.data || [];
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "趋势查询失败");
  } finally {
    loading.value = false;
  }
}

async function refreshSummary() {
  try {
    const res = await getHealthSummary();
    summary.value = res.data.data || summary.value;
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "概览查询失败");
  }
}

async function refreshAll() {
  await queryRecords();
  await queryTrend();
  await refreshSummary();
}

function editRow(row) {
  form.id = row.id;
  form.metricType = row.metricType;
  form.metricValue = row.metricValue;
  form.unit = row.unit;
  form.recordTime = row.recordTime?.replace(" ", "T");
  form.remark = row.remark;
  dialogMode.value = "edit";
  dialogVisible.value = true;
}

async function saveEdit() {
  if (!form.id) {
    ElMessage.warning("请先选择要编辑的记录");
    return;
  }
  loading.value = true;
  try {
    await updateHealthRecord({
      id: form.id,
      metricType: form.metricType,
      metricValue: form.metricValue,
      unit: form.unit,
      recordTime: fmt(form.recordTime),
      remark: form.remark
    });
    ElMessage.success("更新成功");
    resetForm();
    dialogVisible.value = false;
    await refreshAll();
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "更新失败");
  } finally {
    loading.value = false;
  }
}

async function removeRow(row) {
  loading.value = true;
  try {
    await deleteHealthRecord(row.id);
    ElMessage.success("删除成功");
    await refreshAll();
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "删除失败");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  (async () => {
    try {
      const res = await getHealthOptions();
      options.value = res.data.data?.length ? res.data.data : defaultOptions;
    } catch (e) {
      options.value = defaultOptions;
      ElMessage.warning("健康指标接口未就绪，已使用本地选项");
    }
    resetForm();
    await refreshAll();
  })();
});

function openCreateDialog() {
  dialogMode.value = "create";
  resetForm();
  dialogVisible.value = true;
}
</script>

<template>
  <div class="module-page">
    <div class="module-hero">
      <div>
        <h2 class="module-title">健康指标档案</h2>
        <p class="page-subtitle">面向客户的连续健康数据管理中心，支持记录、趋势分析与风险级别识别。</p>
      </div>
      <div class="row-actions">
        <el-tag type="success">数据可追溯</el-tag>
        <el-tag type="warning">风险分级</el-tag>
      </div>
    </div>
    <el-row :gutter="16" class="health-stats-row">
      <el-col :span="8"><div class="panel"><el-statistic title="总记录数" :value="summary.totalRecords || 0" /></div></el-col>
      <el-col :span="8"><div class="panel"><el-statistic title="异常记录数" :value="summary.abnormalRecords || 0" /></div></el-col>
      <el-col :span="8"><div class="panel"><el-statistic title="最新指标" :value="summary.latestMetricType || '-'" /></div></el-col>
    </el-row>

    <el-divider content-position="left">新增 / 编辑健康记录</el-divider>
    <div class="actions health-actions">
      <el-button type="primary" @click="openCreateDialog">新增记录</el-button>
    </div>

    <el-divider content-position="left">记录查询</el-divider>
    <el-form label-width="90px" class="panel health-toolbar">
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="类型">
            <el-select v-model="query.metricType" clearable style="width: 100%">
              <el-option v-for="item in options" :key="item.code" :label="item.name" :value="item.code" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8"><el-form-item label="开始时间"><el-input v-model="query.startTime" type="datetime-local" /></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="结束时间"><el-input v-model="query.endTime" type="datetime-local" /></el-form-item></el-col>
      </el-row>
      <el-button :loading="loading" @click="queryRecords">查询记录</el-button>
      <el-button type="success" :loading="loading" @click="queryTrend">查询趋势</el-button>
    </el-form>

    <el-divider content-position="left">记录列表</el-divider>
    <el-table :data="records" border class="panel-table">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="类型">
        <template #default="scope">{{ typeName(scope.row.metricType) }}</template>
      </el-table-column>
      <el-table-column prop="metricValue" label="值" />
      <el-table-column prop="unit" label="单位" />
      <el-table-column label="风险" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.riskLevel === 'HIGH' ? 'danger' : (scope.row.riskLevel === 'LOW' ? 'success' : 'warning')">
            {{ scope.row.riskLevel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="recordTime" label="时间" />
      <el-table-column prop="remark" label="备注" />
      <el-table-column label="操作" width="160">
        <template #default="scope">
          <el-button link type="primary" @click="editRow(scope.row)">编辑</el-button>
          <el-button link type="danger" @click="removeRow(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">趋势数据</el-divider>
    <el-table :data="trendData" border class="panel-table">
      <el-table-column label="类型">
        <template #default="scope">{{ typeName(scope.row.metricType) }}</template>
      </el-table-column>
      <el-table-column prop="metricValue" label="值" />
      <el-table-column prop="riskLevel" label="风险" />
      <el-table-column prop="recordTime" label="时间" />
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增健康记录' : '编辑健康记录'" width="760px" class="health-dialog">
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="form.metricType" @change="onTypeChange" style="width: 100%">
                <el-option v-for="item in options" :key="item.code" :label="item.name" :value="item.code" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="值"><el-input v-model="form.metricValue" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-select v-model="form.unit" style="width: 100%">
                <el-option v-for="u in unitOptionsByType(form.metricType)" :key="u" :label="u" :value="u" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="记录时间"><el-input v-model="form.recordTime" type="datetime-local" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="dialogMode === 'create'" type="primary" :loading="loading" @click="createRecord">确认新增</el-button>
        <el-button v-else type="warning" :loading="loading" @click="saveEdit">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.health-stats-row {
  margin-top: 8px;
}

.health-actions {
  margin-bottom: 8px;
}

.health-toolbar {
  padding-top: 20px;
}

.panel-table {
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

:deep(.health-dialog .el-dialog) {
  border-radius: var(--radius-xl);
}

:deep(.health-dialog .el-dialog__body) {
  padding: 28px;
}

:deep(.el-statistic__number) {
  font-variant-numeric: tabular-nums;
}

:deep(.el-tag.el-tag--danger) {
  background: var(--danger-light);
  color: var(--danger);
}

:deep(.el-tag.el-tag--warning) {
  background: var(--warning-light);
  color: var(--warning);
}

:deep(.el-tag.el-tag--success) {
  background: var(--success-light);
  color: var(--success);
}
</style>
