import axios from "axios";
import { ElMessage } from "element-plus";

/** 报告页解读成功后跳转咨询台时，经 sessionStorage 传递用户同时填写的问题 */
export const PENDING_REPORT_QUESTION_KEY = "zksh_pending_report_question";

const api = axios.create({
  baseURL: "http://localhost:8080"
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("zksh_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => {
    const body = response?.data;
    if (body && typeof body === "object" && Object.prototype.hasOwnProperty.call(body, "code")) {
      if (body.code !== 0) {
        return Promise.reject({
          response: { data: body, status: response.status },
          message: body.message || "请求失败"
        });
      }
    }
    return response;
  },
  (error) => {
    if (error?.response?.status === 401) {
      ElMessage.warning("请先登录后再操作");
    }
    return Promise.reject(error);
  }
);

export function sendCode(phone) {
  return api.post("/api/user/send-code", { phone });
}

export function login(phone, code) {
  return api.post("/api/user/login", { phone, code });
}

export function register(phone, code, nickname) {
  return api.post("/api/user/register", { phone, code, nickname });
}

export function getProfile() {
  return api.get("/api/user/profile");
}

export function updateProfile(payload) {
  return api.put("/api/user/profile", payload);
}

export function createHealthRecord(payload) {
  return api.post("/api/health/records", payload);
}

export function listHealthRecords(params) {
  return api.get("/api/health/records", { params });
}

export function getHealthTrend(params) {
  return api.get("/api/health/records/trend", { params });
}

export function updateHealthRecord(payload) {
  return api.put("/api/health/records", payload);
}

export function deleteHealthRecord(id) {
  return api.delete(`/api/health/records/${id}`);
}

export function getHealthSummary() {
  return api.get("/api/health/records/summary");
}

export function getHealthOptions() {
  return api.get("/api/health/options");
}

export function createNotification(payload) {
  return api.post("/api/notify", payload);
}

export function listNotifications(params) {
  return api.get("/api/notify", { params });
}

export function listUnreadNotificationChanges(lastId = 0, limit = 20) {
  return api.get("/api/notify/unread/changes", { params: { lastId, limit } });
}

export function readNotification(id) {
  return api.put(`/api/notify/${id}/read`);
}

export function createReminderRule(payload) {
  return api.post("/api/notify/rules", payload);
}

export function listReminderRules() {
  return api.get("/api/notify/rules");
}

export function listAiSessionMessages(sessionId) {
  return api.get(`/api/ai/chat/sessions/${sessionId}/messages`);
}

export function createAiSession(payload) {
  return api.post("/api/ai/chat/sessions", payload);
}

export function listAiSessions() {
  return api.get("/api/ai/chat/sessions");
}

export function renameAiSession(sessionId, title) {
  return api.put(`/api/ai/chat/sessions/${sessionId}/title`, { title });
}

export function deleteAiSession(sessionId) {
  return api.delete(`/api/ai/chat/sessions/${sessionId}`);
}

export function uploadKnowledgeDocument(formData) {
  return api.post("/api/rag/knowledge/documents/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
}

export function listKnowledgeDocuments() {
  return api.get("/api/rag/knowledge/documents");
}

export function reindexMyKnowledge() {
  return api.post("/api/rag/knowledge/reindex");
}

export function createReportTask(file) {
  const formData = new FormData();
  formData.append("file", file);
  return api.post("/api/report/tasks", formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
}

export function getReportTaskStatus(taskId) {
  return api.get(`/api/report/tasks/${taskId}`);
}

export function listReportTasks(limit = 10) {
  return api.get("/api/report/tasks", { params: { limit } });
}

export async function sendAiMessageStream(sessionId, content, handlers = {}) {
  const token = localStorage.getItem("zksh_token");
  const response = await fetch(`http://localhost:8080/api/ai/chat/sessions/${sessionId}/messages`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ content })
  });

  if (!response.ok) {
    throw new Error(`请求失败: ${response.status}`);
  }
  if (!response.body) {
    throw new Error("流式响应体为空");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  const flushEventBlock = (eventBlock) => {
    if (!eventBlock) return;
    const lines = eventBlock.split(/\r?\n/);
    const dataLines = lines
      .map((line) => line.trimStart())
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.slice(5).trim());
    if (!dataLines.length) return;

    // SSE 允许同一个事件有多行 data，这里拼接成完整 JSON 文本
    const jsonText = dataLines.join("\n");
    if (!jsonText || jsonText === "[DONE]") return;
    try {
      const payload = JSON.parse(jsonText);
      handlers.onMessage?.(payload);
    } catch (e) {
      handlers.onError?.(e);
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split(/\r?\n\r?\n/);
    buffer = events.pop() || "";

    for (const event of events) {
      flushEventBlock(event);
    }
  }

  // 流结束后尝试处理最后一个残留事件块
  flushEventBlock(buffer);
  handlers.onDone?.();
}
