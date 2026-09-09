# 智康守护（zksh）

一套**能完整跑起来的 Agentic 后端教学范例**：用 Spring Boot 3.2 + LangChain4j 把「大模型问答」拆成看得见、可断点、能降级的普通 Java 代码。用户在页面上问一句健康问题，后台会依次做问题改写 → 向量 + 关键词双路召回 → RRF 融合 → 交叉编码器重排 → 模型带着引用和工具（查他的血压、血糖记录）流式作答；上传一张体检报告 PDF，则走另一条 6 阶段异步管线，识别出的指标会自动入库、判异常、发提醒。

跟着这个仓库读，你能把「RAG / Agent 工作流 / 会话记忆 / 异步报告解析」这四类通常在论文和博客里看得很玄的东西，对上具体是哪一个类、哪一行、失败时退化成什么。

- **三条真实链路，不是一个 `chat()`**。AI 对话（SSE）、报告解读（RabbitMQ 6 阶段）、健康档案与提醒（定时扫描 + SSE 推送），共用一套认证和用户上下文。
- **每一步都可溯源、可降级**。检索把 query、召回数、耗时、候选与重排快照写进 `rag_retrieval_log`；改写失败退回原句，Qdrant 挂了返回空结果让模型自答，Rerank 失败退回向量相似度排序。
- **AI 层几乎不含 AI 代码**。核心编排是一个 `@AiService` 接口声明 + 3 个可插拔 `AgentStep`，加一个工具类，其余全是 Spring 熟悉的写法。
- **不绑死云端**。Chat / Streaming / Embedding 都走 OpenAI 兼容 `base-url`（Rerank 默认用 DashScope 原生端点，同样可改），换网关只需改几个环境变量；`OCR`、`XXL-JOB` 均为可选，关掉后本地调度器兜底。
- **中文注释密集**。133 个后端类里 58 个带中文说明，最集中的正是最该看懂的 `rag/`（18 个）与 `agent/workflow/`（4 个）。
- **13 张表的完整 DDL 就在仓库里**（`zksh.sql`，MySQL 8.0.36 dump），不含未提交的隐藏前提。

```java
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "openAiChatModel",
    streamingChatModel = "openAiStreamingChatModel",
    chatMemoryProvider = "chatMemoryProvider",   // Redis 里的会话记忆，超 12 条自动摘要
    contentRetriever = "contentRetriever",       // Qdrant + MySQL 全文 → RRF → Rerank
    tools = {"healthAiTools"}                    // 4 个只读 Function Calling 工具
)
public interface ConsultantService {
    @SystemMessage(fromResource = "system.txt")
    Flux<String> chat(@MemoryId String memoryId, @UserMessage String message);
}
// 一个接口声明 = 一次调用的完整编排：检索增强 + 带记忆的上下文 + 工具调用 + 流式输出
```

> **这是学习项目，不是可上线的医疗系统。** 登录短信是 Mock（验证码打在后台控制台），CORS 全开，测试只有 1 个上下文加载用例。详见[已知边界](#已知边界诚实清单)。

---

## 目录

- [它长什么样](#它长什么样三条主链路)
- [快速开始](#快速开始)
- [功能地图](#功能地图rest-一览)
- [核心机制拆解](#核心机制拆解)
- [配置速查](#配置速查真实默认值)
- [已知边界（诚实清单）](#已知边界诚实清单)
- [常见问题](#常见问题)
- [开发命令](#开发命令)
- [目录结构](#目录结构)

---

## 它长什么样：三条主链路

```
                 ┌── 手机号 + 验证码（Mock，码在控制台）→ JWT ──┐
   Vue 3 (5173)  │                                             │  Spring Boot (8080)
                 ▼                                             ▼
  /ai 咨询台 ── POST /api/ai/chat/sessions/{id}/messages ──► SequentialChatWorkflowOrchestrator
                                                                  │ rewrite → topic → answer
                                                                  ▼
                             ConsultantService (@AiService, Flux<String>)
                             ├── QdrantContentRetriever：改写 → 双路向量 + MySQL 全文 → RRF → Rerank → Top-6
                             ├── RedisChatMemoryStore：Redis 存记忆，>12 条自动 LLM 摘要
                             └── HealthAiTools：健康摘要 / 最近记录 / 未读通知 / 当前时间
                                                                  │
  SSE 分片 ◄──────────────── 每个 chunk + 末尾汇总（含引用与 riskLevel）◄┘

  /report 上传 ── POST /api/report/tasks ──► RabbitMQ ──► 消费者：PDF/图片 → 阿里云 OCR
                                                            → LLM 结构化提取 → 健康指标入库 → 异常告警 → 临床摘要 + 通知
                                                            （6 个阶段各写一条 report_task_step_log，前端轮询进度）

  /knowledge 上传 ── POST /api/rag/knowledge/documents/upload ──► 抽取文本 → 分块 → embedding → Qdrant upsert
```

---

## 快速开始

### 0. 前置条件

| 依赖 | 版本 | 必须？ | 默认地址 |
|------|------|--------|----------|
| JDK | 17 | **是** | — |
| Maven | 3.x | **是** | — |
| Node.js | 18+（建议 LTS） | **是** | — |
| MySQL | 8.0+（需要 FULLTEXT 索引） | **是** | `localhost:3306`，库名 `zksh` |
| Redis | 5+ | **是**（会话记忆 + 登录验证码） | `localhost:6379`，无密码 |
| RabbitMQ | 3.x | 跑报告解读必需（其余功能可不起） | `localhost:5672`，vhost `/` |
| Qdrant | 1.x | 用 RAG 才需要 | `http://localhost:6333` |
| 阿里云 DashScope API Key | — | AI 问答 / 向量化才需要 | OpenAI 兼容端点 |
| 阿里云 OCR AppCode | — | 报告解读才需要 | 市场 API |
| XXL-JOB | 2.4.1 | 否，默认关闭 | — |

> ⚠️ `zksh.sql` 是 mysqldump 产物，**不含 `CREATE DATABASE`**，必须先手工建库。

### 1. 建库并导入表结构

```powershell
mysql -u root -p -e "CREATE DATABASE zksh DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p zksh < zksh.sql
```

导入后应能看到 13 张表：`user`、`health_record`、`ai_chat_session`、`ai_chat_message`、`knowledge_document`、`knowledge_chunk`、`rag_retrieval_log`、`notification`、`reminder_rule`、`report_task`、`report_ocr_result`、`report_interpretation`、`report_task_step_log`。

### 2. 起依赖服务

仓库**没有** docker-compose，下面是等价的 `docker run`（本地已装这些服务可跳过）：

```bash
docker run -d --name zksh-mysql   -p 3306:3306  -e MYSQL_ROOT_PASSWORD=root     mysql:8.0
docker run -d --name zksh-redis   -p 6379:6379  redis:7
docker run -d --name zksh-rabbit  -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=guest -e RABBITMQ_DEFAULT_PASS=guest rabbitmq:3-management
docker run -d --name zksh-qdrant  -p 6333:6333  qdrant/qdrant
```

### 3. 写配置

```powershell
cd zksh\src\main\resources
copy application.example.yml application.yml     # Windows
# cp application.example.yml application.yml     # macOS / Linux
```

`application.yml` 已被 `.gitignore` 忽略。最少要给这 5 个变量（其余全有默认值）：

| 环境变量 | 为什么必须 | 示例 |
|----------|------------|------|
| `MYSQL_PASSWORD` | 模板默认空密码，本机 MySQL 一般不是空的 | `root` |
| `JWT_SECRET` | **默认空串，JJWT 要求 HS256 密钥 ≥ 32 字节，不设登录直接失败** | 任意 ≥32 字符随机串 |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | 模板默认空串，而 RabbitMQ 只认 `guest/guest` | `guest` / `guest` |
| `API_KEY` | DashScope（或任一 OpenAI 兼容服务）Key，AI 问答与向量化共用 | `sk-xxxx` |

```powershell
$env:MYSQL_PASSWORD = "root"
$env:JWT_SECRET     = "replace-me-with-a-random-string-32-bytes-min"
$env:RABBITMQ_USERNAME = "guest"
$env:RABBITMQ_PASSWORD = "guest"
$env:API_KEY        = "sk-xxxxxxxxxxxxxxxx"
```

### 4. 启动后端

```powershell
cd ..\..\..          # 回到 zksh 目录
mvn spring-boot:run
```

看到 `Tomcat started on port 8080` 即成功。改端口用 `SERVER_PORT`。

### 5. 启动前端

```powershell
cd ..\zksh-ui
npm install
npm run dev
```

打开 **http://localhost:5173**，未登录会跳 `/auth`。前端地址硬编码在 [`src/api.js`](zksh-ui/src/api.js) 的 `baseURL`（`http://localhost:8080`），后端换机器时改这里。

### 6. 第一次跑通（5 条命令）

在登录页输手机号 → 点「获取验证码」，**回到后端控制台**找这一行拿到验证码：

```
[MockSMS] phone=13800001111, code=417283
```

或者直接用 curl 走完（PowerShell 下把 `curl` 换成 `curl.exe`）：

```bash
# ① 发码（控制台读码）
curl -X POST http://localhost:8080/api/user/send-code -H 'Content-Type: application/json' -d '{"phone":"13800001111"}'

# ② 登录，data.token 就是后续请求的 JWT
curl -X POST http://localhost:8080/api/user/login -H 'Content-Type: application/json' -d '{"phone":"13800001111","code":"417283"}'
#=> {"code":0,"message":"ok","data":{"userId":3,"phone":"13800001111","token":"eyJhbGciOiJIUzI1NiJ9..."}}

TOKEN=<上一步的 token>

# ③ 建会话（title 可省，缺省为「新会话」）
curl -X POST http://localhost:8080/api/ai/chat/sessions -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'
#=> {"code":0,"message":"ok","data":{"sessionId":7,"title":"新会话", ... }}

# ④ 提问，SSE 流式返回：先一串 answer 分片，最后一条带 riskLevel 与 references
curl -N -X POST http://localhost:8080/api/ai/chat/sessions/7/messages -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"content":"儿童手足口病一般几天能好？"}'
#=> data:{"code":0,"message":"ok","data":{"messageId":"m_tmp_a1b2c3d4","answer":"儿童","riskLevel":null,"references":[],"tokens":2}}
#=> data:...（逐字分片）
#=> data:{"code":0,"message":"ok","data":{"messageId":"m_42","answer":"儿童手足口病……","riskLevel":"MEDIUM","references":[{"docId":"5","title":"手足口病诊疗指南","snippet":"……"}],"tokens":418}}

# ⑤ 看这次检索到底召回了什么（RAG 可观测性）
curl "http://localhost:8080/api/health/records/summary" -H "Authorization: Bearer $TOKEN"
mysql -u root -p zksh -e "select query_text,recall_count,topk_count,latency_ms from rag_retrieval_log order by id desc limit 5;"
```

第 ④ 步若返回「知识库无匹配内容」类回答，是正常的——集合是空的，先做下一步。

### 7. 给 RAG 灌知识（仓库自带 6 份语料）

`doc/` 下有 5 份儿科诊疗指南 + 1 份通用医疗健康知识手册（PDF）。在「知识库」页上传，或直接：

```bash
curl -X POST http://localhost:8080/api/rag/knowledge/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@../doc/手足口病诊疗指南.pdf" -F "title=手足口病诊疗指南" -F "tags=pediatric"
```

上传即完成「抽文本 → 分块 → embedding → upsert Qdrant」；集合不存在时会自动按 embedding 维度以 Cosine 距离创建，无需手工建。换 embedding 模型后重新灌索引：

```bash
curl -X POST http://localhost:8080/api/rag/knowledge/reindex -H "Authorization: Bearer $TOKEN"
```

### 8. 跑一次报告解读（需要 OCR AppCode）

```bash
export REPORT_OCR_APPCODE=<阿里云市场购买后的 AppCode>   # 已含在步骤 3 的重启环境里
curl -X POST http://localhost:8080/api/report/tasks -H "Authorization: Bearer $TOKEN" -F "file=@体检报告.pdf"
#=> {"code":0,"data":{"taskId":"rpt_14625fe248894a17aed0c0cfc9c7c8f9","status":"QUEUED","stage":"UPLOADED"}}

curl "http://localhost:8080/api/report/tasks/rpt_14625fe248894a17aed0c0cfc9c7c8f9" -H "Authorization: Bearer $TOKEN"
# 轮询到 status=DONE，data.stepLogs 会列出 6 个阶段各自的成功/失败与详情
```

原文件落在 `REPORT_STORAGE_DIR`（默认 `${user.home}/zksh/storage/reports`）。

---

## 功能地图：REST 一览

统一响应体 `{code, message, data}`，`code = 0` 为成功，非 0 由前端拦截器抛错。除 `send-code` / `login` / `register` 外，`/api/**` 全部需要 `Authorization: Bearer`。

| 前缀 | 能力 | 关键端点 |
|------|------|----------|
| `/api/user` | 验证码登录（首次登录自动注册）、档案读写 | `POST /send-code`、`POST /login`、`GET/PUT /profile` |
| `/api/ai` | 会话 CRUD + **SSE 流式问答** | `POST /chat/sessions/{id}/messages`（`text/event-stream`）、`GET /chat/sessions/{id}/messages` |
| `/api/health` | 指标记录增删改查、趋势、摘要、可选指标字典 | `POST/PUT/DELETE /records`、`GET /records/trend`、`GET /records/summary`、`GET /options` |
| `/api/rag` | 知识文档上传入库、我的文档、重建索引 | `POST /knowledge/documents/upload`、`GET /knowledge/documents`、`POST /knowledge/reindex` |
| `/api/report` | 报告任务创建、进度与阶段日志、历史列表 | `POST /tasks`、`GET /tasks/{taskId}`、`GET /tasks?limit=` |
| `/api/notify` | 通知增删读、**未读 SSE 推送**、提醒规则 | `GET /stream`、`GET /unread/changes`、`PUT /{id}/read`、`POST/GET /rules` |

前端 8 个页面组件（`views/` 下，另有 `App.vue`）对应 7 个路由：`/auth`，以及 `MainLayout` 下的 `/user`（档案）、`/health`（指标）、`/notify`（提醒）、`/ai`（咨询）、`/report`（报告）、`/knowledge`（知识库）。

---

## 核心机制拆解

### ① AI 对话工作流：3 个可插拔步骤

`SequentialChatWorkflowOrchestrator` 按 `@Order` 依次执行 `AgentStep`，每步独立开关、独立超时（默认 **1200 ms**）、失败可继续（`continue-on-error: true`）：

| 顺序 | 步骤 | 做什么 | 关掉会怎样 |
|------|------|--------|------------|
| 10 | `RewriteAgentStep` | 把口语问题改写成适合检索的 query，写回 `AgenticScope` | 直接用原句检索 |
| 20 | `TopicAgentStep` | 首条消息时用 LLM 给会话起标题（≤24 字，1200 ms 内不返回就跳过） | 标题一直是「新会话」 |
| 30 | `AnswerAgentStep` | 标记进入作答阶段，交给 `ConsultantService.chat()` | — |

要加一步「敏感词审核 / 意图分类」，只需实现 `AgentStep` 并给它一个 `@Order`，编排器不用改。

### ② RAG 混合检索：6 步

`QdrantContentRetriever`（611 行，注释最密集的一个类）：

1. **Query Rewrite** —— LLM 改写，失败用原文
2. **双路向量召回** —— 原文与改写句分别 embed，各查一次 Qdrant（`vector-top-n: 30`，相似度阈值 `0.3`）
3. **关键词召回** —— MySQL `knowledge_chunk` 的 FULLTEXT `MATCH ... AGAINST`，并额外带 `LIKE '%kw%'` 兜底（`keyword-top-n: 30`）
4. **RRF 融合** —— Reciprocal Rank Fusion，`k = 60`，合并上限 50
5. **Rerank** —— 交叉编码器 `gte-rerank-v2`，取 `top-n: 30`
6. **Top-K = 6** —— 引用写入 `RagReferenceStore`，随 SSE 最后一条事件返回给前端

整条链路的 query、召回数、命中数、耗时、候选与重排快照都会写进 `rag_retrieval_log`，所以「为什么答得不准」是能被查出来的。

### ③ 会话记忆与自动摘要

记忆存 Redis，key 为 `userId:sessionId`。当消息数超过 `AI_MEMORY_SUMMARY_KEEP_RECENT`（默认 **12**）时，旧消息交给 LLM 压缩成一段摘要，以 `【会话摘要】` 前缀挂回 SystemMessage 之前，控制上下文长度。

### ④ Function Calling 工具：4 个只读能力

`HealthAiTools` 提供 `getMyHealthSummary`、`getMyRecentHealthRecords`、`getMyUnreadNotifications`、`getCurrentDateTime`，全部只读（V1 故意如此，避免模型误删数据）。

### ⑤ 报告解读：6 阶段异步状态机

```
UPLOADED → OCR_DONE → INTERPRETED → SAVED → ALERTED → NOTIFIED
```

上传后只写一条 `QUEUED` 任务并发 RabbitMQ 消息，由 `ReportTaskConsumer` 消费，`ReportExtractionAndPersistService` 在 `@Transactional` 中跑完全流程。PDF 先用 PDFBox 按 **180 DPI 渲染前 3 页**为 PNG 再逐页送 OCR；图片则按扩展名判 `png/webp/jpeg` 直传。LLM 输出 JSON 解析成 `ExtractedReport`（报告类型、患者信息、检验项、异常标记），异常项触发通知。每阶段一条 `report_task_step_log`，失败原因可在 `report_task.error_message` 与阶段日志里读到。

### ⑥ 健康指标与提醒

7 类指标：`blood_pressure`、`heart_rate`、`blood_glucose`、`cholesterol`、`weight`、`sleep_duration`、`steps`。其中 5 类内置 HIGH/LOW/NORMAL 阈值（如血压 ≥140/90 判 HIGH、<90/60 判 LOW；血糖 >7.0 或 <3.9）。`NotificationLocalScheduler` 默认每 **60 s** 跑一次到期提醒规则与异常指标扫描；打开 XXL-JOB（`XXL_JOB_ENABLED=true`）可换成分布式调度。

---

## 配置速查（真实默认值）

全部配置项在 [`application.example.yml`](zksh/src/main/resources/application.example.yml) 中都有注释，这里列最常改的：

| 前缀 | 关键默认值 |
|------|------------|
| `server` | `port: 8080` |
| `spring.servlet.multipart` | 单文件 `20MB`，单请求 `25MB` |
| `app.jwt` | `expire-hours: 72`，`secret` 空（**必须自设**） |
| `app.login` | 6 位验证码，`code-expire-minutes: 5`，key 前缀 `zksh:login:code:` |
| `app.notify` | `schedule-ms: 60000`，`local-schedule-enabled: true` |
| `app.report` | `pdf.max-pages: 3`、`pdf.render-dpi: 180`、队列 `zksh.report.task.created.queue` |
| `langchain4j.open-ai` | `qwen-max-latest` @ DashScope 兼容端点（chat 与 streaming 各一份，可分别覆盖） |
| `rag.embedding` | `text-embedding-v4`（OpenAI 兼容） |
| `rag.rerank` | `gte-rerank-v2`（DashScope 原生端点） |
| `rag.hybrid` | `30 / 30 / 50 / 6`、`vector-score-threshold: 0.3`、`rrf-k: 60` |
| `ai.workflow` | `enabled: true`、`continue-on-error: true`、默认超时 `1200 ms` |
| `ai.memory-summary` | `enabled: true`、`keep-recent-messages: 12` |
| `qdrant` | `http://localhost:6333`，collection `zksh_knowledge_chunks` |
| `xxl.job` | `enabled: false` |

---

## 已知边界（诚实清单）

作为教学范例，这些地方是**故意没做完**的，读代码时别误会成最佳实践：

- **短信是 Mock**：验证码明文打印到控制台并存 Redis，没有接任何短信网关。
- **CORS 全开**：`WebMvcConfig` 用 `allowedOrigins("*")`，前端换域名方便，生产必须收紧。
- **检索不分用户**：Qdrant 搜索与 MySQL 全文召回都不带 `created_by` 过滤，任何登录用户都能召回到别人上传文档的分片；只有「我的文档」列表和重建索引是按用户的。
- **重建索引会清空整个集合**：`reindexMyKnowledge` 先 `deleteCollectionIfExists()`。若当前用户没有任何文档，它也会把集合删掉——多用户共库时会互相影响。
- **中文全文检索依赖 LIKE 兜底**：`idx_ft_knowledge_chunk_content` 没指定 `WITH PARSER ngram`，MySQL 默认分词器不切中文，真正命中的多是 `LIKE '%kw%'` 那条分支。
- **知识库不认扫描件**：`TextExtractService` 只用 PDFBox 抽文本层，图片型 PDF 会抽出空内容（代码注释自己也写了）。
- **风险分级是关键词匹配**：`detectRiskLevel` 靠答案里是否含「急诊 / 胸痛 / 呼吸困难」等词，不是模型判断。
- **测试几乎为零**：只有 `ZkshApplicationTests` 一个上下文加载用例，没有 CI。
- **内容免责**：所有诊疗指南语料与 AI 回答仅用于技术演示，**不能作为医疗建议**。

---

## 常见问题

**启动就报 RabbitMQ `ACCESS_REFUSED`**
`RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` 在模板里默认是**空串**，而 RabbitMQ 需要 `guest/guest` 或你自建账号。显式设置后重启。

**`/api/user/login` 返回 500 或 `WeakKeyException`**
`JWT_SECRET` 没设或短于 32 字节，JJWT 拒绝签发。见[步骤 3](#3-写配置)。

**AI 回答里没有引用**
按顺序确认：① Qdrant 起来了（`http://localhost:6333/collections` 能看到 `zksh_knowledge_chunks`）；② `API_KEY` 有效（Embedding 与 Chat 共用）；③ `doc/` 里的 PDF 已上传且 `knowledge_chunk` 有数据；④ 查 `rag_retrieval_log` 的 `recall_count` 是不是 0——为 0 说明向量化就没入库。

**答得不准 / 明显没读知识库**
先查 `rag_retrieval_log.result_snapshot_json` 看候选里有没有正确分片：有但没进 Top-6，是融合/精排的问题（把 `RAG_RERANK_MODEL_NAME` 置空即可跳过精排，`RerankService` 会直接返回空并退回向量顺序；也可调低 `RAG_HYBRID_VECTOR_THRESHOLD` 放宽召回）；候选里根本没有，就是分块或 embedding 的问题，重新上传或 `reindex`。

**前端一直 401**
`localStorage.zksh_token` 过期（默认 72 小时）或 `api.js` 的 `baseURL` 指错后端。路由守卫只认这个 key。

**报告任务一直停在 `OCR_DONE` 之前**
`REPORT_OCR_APPCODE` 未设置或额度用尽；看 `GET /api/report/tasks/{taskId}` 返回的 `stepLogs` 与 `errorMessage`。

**想完全不接外网试跑**
可以把 LLM 换成任意 OpenAI 兼容服务（改 `LANGCHAIN4J_OPENAI_BASE_URL` 与 `RAG_EMBEDDING_BASE_URL`）。

**没装 RabbitMQ 会怎样**
`spring-boot-starter-amqp` 在 `pom.xml` 里是硬依赖。RabbitMQ 不可用时，报告上传的任务会停在 `QUEUED`（消费者收不到消息），后台线程会持续重试并刷连接异常日志。只学 AI 对话与 RAG 的话，可以先不起 RabbitMQ。

---

## 开发命令

```bash
cd zksh
mvn test                              # 全部测试
mvn test -Dtest=ZkshApplicationTests  # 单个测试类
mvn spring-boot:run                   # 热开发
mvn -q package -DskipTests && java -jar target/zksh-0.0.1-SNAPSHOT.jar   # 打包运行

cd zksh-ui
npm run dev       # http://localhost:5173
npm run build     # 产物在 dist/
npm run preview   # 预览生产构建
```

版本：Spring Boot 3.2.5、LangChain4j 1.0.1-beta6（含 `spring-boot-starter` / `open-ai` / `reactor`）、MyBatis Starter 3.0.4、JJWT 0.11.5、PDFBox 2.0.32、XXL-JOB 2.4.1；前端 Vue 3.5.13、Vite 6.3.1、Element Plus 2.9.10、Vue Router 4.5.1、Axios 1.9.0。

给 AI 层加东西时的三个落点：新工作流步骤 → `agent/workflow/step/`；新工具 → `tool/HealthAiTools`（记得只读优先）；新检索策略 → `rag/service/QdrantContentRetriever`。系统提示词在 `resources/system.txt`，报告抽取与临床摘要的提示词分别是 `report_extractor_system.txt`、`report_clinical_summary_system.txt`。

---

## 目录结构

```
.
├── zksh/                             # 后端（133 个 Java 类，主类 ZkshApplication）
│   ├── src/main/java/com/jianjian/ai/zksh/
│   │   ├── agent/workflow/           # 对话工作流编排器 + 3 个 AgentStep
│   │   ├── rag/                      # 混合检索、改写、分块、入库、重建索引（注释最密集）
│   │   ├── report/                   # 报告异步管线：MQ 消费者、OCR、结构化提取、阶段日志
│   │   ├── service/                  # ConsultantService(@AiService)、会话/档案/通知/用户
│   │   ├── tool/                     # HealthAiTools（Function Calling）
│   │   ├── config/                   # LangChain4j Bean、RedisChatMemoryStore、CORS、XXL-JOB
│   │   ├── security/                 # JWT 签发解析、AuthInterceptor、UserContext(ThreadLocal)
│   │   ├── controller/ service/ mapper/ domain/   # 常规三层 + entity/dto/vo
│   │   └── job/                      # 本地调度器与 XXL-JOB handler
│   └── src/main/resources/
│       ├── application.example.yml   # 配置模板（所有环境变量都带注释）
│       ├── mapper/**/*.xml           # MyBatis 映射，含 FULLTEXT 检索 SQL
│       └── system.txt 等             # 提示词
├── zksh-ui/                          # 前端（8 个页面组件，无 Pinia，组件内 ref/reactive）
├── doc/                              # 6 份可直接上传的中文医疗指南语料
├── CLAUDE.md                         # 面向 AI 编码助手的架构速查
└── zksh.sql                          # 13 张表完整 DDL
```

---

## 许可

仓库当前**未附带 LICENSE 文件**，默认保留所有权利。若你计划公开分发，请先补充许可证声明。
