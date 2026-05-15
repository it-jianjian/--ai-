<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { listKnowledgeDocuments, reindexMyKnowledge, uploadKnowledgeDocument } from "../api";

const uploading = ref(false);
const documents = ref([]);
const selectedFile = ref(null);
const title = ref("");
const tags = ref("");
const previewUrl = ref("");
const reindexing = ref(false);
const viewMode = ref("list");

const acceptedTypes = ".pdf,.txt,.md";
const isPdf = computed(() => (selectedFile.value?.name || "").toLowerCase().endsWith(".pdf"));
const readyCount = computed(() => documents.value.filter((d) => d.status === "READY").length);

function resetPreviewUrl() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = "";
  }
}

function onFileChange(file) {
  resetPreviewUrl();
  selectedFile.value = file?.raw || null;
  if (selectedFile.value && isPdf.value) {
    previewUrl.value = URL.createObjectURL(selectedFile.value);
  }
}

async function loadDocuments() {
  const res = await listKnowledgeDocuments();
  documents.value = res?.data?.data || [];
}

async function submitUpload() {
  if (!selectedFile.value) {
    ElMessage.warning("请先选择文件");
    return;
  }
  const formData = new FormData();
  formData.append("file", selectedFile.value);
  if (title.value.trim()) formData.append("title", title.value.trim());
  if (tags.value.trim()) formData.append("tags", tags.value.trim());

  uploading.value = true;
  try {
    await uploadKnowledgeDocument(formData);
    ElMessage.success("上传并入库成功");
    selectedFile.value = null;
    title.value = "";
    tags.value = "";
    resetPreviewUrl();
    await loadDocuments();
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || "上传失败");
  } finally {
    uploading.value = false;
  }
}

async function rebuildIndex() {
  reindexing.value = true;
  try {
    const res = await reindexMyKnowledge();
    const data = res?.data?.data;
    ElMessage.success(`重建完成：文档${data?.docCount ?? 0}，分片${data?.chunkCount ?? 0}`);
    await loadDocuments();
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || "重建失败");
  } finally {
    reindexing.value = false;
  }
}

onMounted(() => {
  loadDocuments().catch(() => ElMessage.warning("加载知识文档列表失败"));
});

onBeforeUnmount(() => {
  resetPreviewUrl();
});
</script>

<template>
  <div class="knowledge-page module-page">
    <div class="module-hero">
      <div>
        <h2 class="module-title">知识库运营台</h2>
        <p class="page-subtitle">管理医疗知识文档资产，保障检索质量与 AI 引用可信度。</p>
      </div>
      <div class="row-actions">
        <el-tag type="success">已就绪 {{ readyCount }}</el-tag>
        <el-tag type="info">总文档 {{ documents.length }}</el-tag>
      </div>
    </div>

    <div class="grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-title">文档上传</div>
        </template>
        <el-form label-width="80px">
          <el-form-item label="标题">
            <el-input v-model="title" placeholder="可选，默认使用文件名" />
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="tags" placeholder="可选，例如：高血压,血糖" />
          </el-form-item>
          <el-form-item label="文件">
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              :accept="acceptedTypes"
              :on-change="onFileChange"
            >
              <el-button type="primary" plain>选择文件</el-button>
            </el-upload>
            <span class="file-name">{{ selectedFile?.name || "未选择文件" }}</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="uploading" @click="submitUpload">上传并入库</el-button>
            <el-button type="warning" plain :loading="reindexing" @click="rebuildIndex">一键重建索引</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-title">PDF 预览（本地）</div>
        </template>
        <div v-if="previewUrl" class="preview-wrap">
          <iframe :src="previewUrl" class="pdf-frame" />
        </div>
        <el-empty v-else description="选择 PDF 文件后可预览" :image-size="100" />
      </el-card>
    </div>

    <el-card shadow="never" class="mt16">
      <template #header>
        <div class="doc-header">
          <div class="card-title">已入库文档</div>
          <el-segmented v-model="viewMode" :options="[{ label: '紧凑列表', value: 'list' }, { label: '卡片视图', value: 'card' }]" />
        </div>
      </template>
      <el-table v-if="viewMode === 'list'" :data="documents" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="source" label="来源文件" min-width="180" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="chunkCount" label="分片数" width="100" />
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
      </el-table>
      <div v-else class="doc-card-grid">
        <div v-for="doc in documents" :key="doc.id" class="doc-card">
          <div class="doc-icon">{{ (doc.source || "DOC").split(".").pop()?.toUpperCase() }}</div>
          <div class="doc-main">
            <div class="doc-title">{{ doc.title }}</div>
            <div class="doc-meta">{{ doc.source }} · 分片 {{ doc.chunkCount || 0 }}</div>
          </div>
          <el-tag :type="doc.status === 'READY' ? 'success' : 'warning'">{{ doc.status }}</el-tag>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.knowledge-page {
  display: flex;
  flex-direction: column;
}
.page-subtitle {
  margin: 0 0 12px;
  color: #909399;
}
.grid {
  display: grid;
  gap: 20px;
  grid-template-columns: 1fr 1.2fr;
}
.card-title {
  font-weight: 600;
}
.doc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.file-name {
  margin-left: 10px;
  color: var(--color-text-secondary);
}
.preview-wrap {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}
.pdf-frame {
  width: 100%;
  height: 420px;
  border: none;
}
.mt16 {
  margin-top: 16px;
}
.doc-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.doc-card {
  padding: 16px;
  border: 1px solid var(--gray-100);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  gap: 12px;
  transition: var(--transition-base);
}
.doc-card:hover {
  background: var(--white);
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}
.doc-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--primary-light);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}
.doc-main {
  flex: 1;
  min-width: 0;
}
.doc-title {
  font-weight: 500;
  color: var(--gray-900);
}
.doc-meta {
  color: var(--color-text-muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 960px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .doc-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>

