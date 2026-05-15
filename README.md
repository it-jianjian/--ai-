# 智康守护说明文档

面向健康体检 / 检验报告场景的示例项目：后端提供账号与对话、报告解析管线、健康知识库（RAG）、通知等能力；前端为 Vue 3 管理端，通过 HTTP 调用后端 API。

---

## 技术栈一览

| 模块 | 说明 |
|------|------|
| **zksh** | Spring Boot 3.2、Java 17、MyBatis、MySQL、Redis、RabbitMQ |
| **AI** | LangChain4j，默认对接阿里云 DashScope OpenAI 兼容接口（可按配置切换兼容地址） |
| **向量检索** | Qdrant；Embedding / Rerank 可配置 DashScope 或兼容网关 |
| **zksh-ui** | Vue 3 + Vite + Vue Router + Element Plus + Axios |

可选组件：**XXL-JOB**（配置中默认关闭，按需开启）。

---

## 仓库目录

```
├── zksh/                 # 后端 Spring Boot（主类：ZkshApplication）
│   └── src/main/resources/
│       └── application.example.yml   # 配置模板（可复制为 application.yml）
└── zksh-ui/              # 前端 Vite + Vue
```

---

## 运行前置条件

1. **JDK 17**、**Maven 3.x**  
2. **Node.js 18+**（建议 LTS）、**pnpm / npm / yarn** 任选其一  
3. **MySQL**：创建与 JDBC URL 一致的数据库（默认库名示例为 `zksh`）  
4. **Redis**  
5. **RabbitMQ**  
6. **Qdrant**（使用 RAG / 向量能力时需要，默认 `http://localhost:6333`）

> **数据库表结构**：当前仓库未附带完整 `schema.sql`，需与你环境一致或由维护者另行提供 DDL。表名可参考 `zksh/src/main/resources/mapper/**/*.xml` 与各 `Entity`。  
> **阿里云相关**：报告 OCR（若启用）、DashScope API Key、LangChain 模型调用等需在配置或环境变量中填写，不要将含真实密钥的配置提交到 Git。

---

## 一、后端配置（zksh）

### 1. 准备配置文件

```bash
cd zksh/src/main/resources
copy application.example.yml application.yml    # Windows
# 或 macOS/Linux: cp application.example.yml application.yml
```

按需编辑 **`application.yml`**，或通过**环境变量**覆盖（推荐生产环境）。模板文件顶部已列出常用变量；最小可运行集通常包括：

| 变量 | 说明 |
|------|------|
| `MYSQL_URL` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` | MySQL 连接 |
| `JWT_SECRET` | JWT 签发密钥（须足够随机、勿泄露） |
| `API_KEY` | DashScope（或兼容服务）API Key |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ 账号 |

其余如 `REDIS_*`、`REPORT_OCR_APPCODE`、`RAG_EMBEDDING_API_KEY`、`QDRANT_*` 等见 `application.example.yml` 内注释。

Windows PowerShell 示例（会话内生效）：

```powershell
$env:MYSQL_PASSWORD = "your-mysql-password"
$env:JWT_SECRET = "至少 32 字节的随机串"
$env:API_KEY = "sk-xxxx"
```
![Uploading image.png…]()

### 2. 启动后端

在 **`zksh`** 目录：

```powershell
cd zksh
mvn spring-boot:run
```

或打包运行：

```powershell
mvn -q package -DskipTests
java -jar target/zksh-0.0.1-SNAPSHOT.jar
```

默认 **HTTP 端口**：`8080`（可由 `SERVER_PORT` 修改）。

---

## 二、前端配置与启动（zksh-ui）

### 1. 安装依赖

```powershell
cd zksh-ui
npm install
```

### 2. API 地址

默认在 `zksh-ui/src/api.js` 中将 **`baseURL`** 指向 `http://localhost:8080`。  
若后端部署在其他主机或端口，请修改此处，或通过后续自行接入 `import.meta.env.VITE_*`（需在 `vite.config.js` 与 `.env` 中配置，勿提交真实生产密钥）。

### 3. 开发模式

```powershell
npm run dev
```

Vite 默认 **http://localhost:5173**。浏览器访问该地址即可对接本地后端。

### 4. 生产构建（可选）

```powershell
npm run build
npm run preview
```

---

## 三、与健康 / 文档相关的隐私提示

本项目涉及体检与报告处理能力，请在部署与开源时：

- 不要提交 **`application.yml`**、**`.env`** 及个人数据目录；仓库根 `.gitignore` 已忽略常见敏感路径。  
- 对外演示使用脱敏数据；遵守当地医疗数据与个人隐私法规。

---

## 四、常见问题

**启动报数据库连接失败**  
检查 MySQL 是否运行、库名与用户密码是否与 `MYSQL_*`（或 `application.yml`）一致，且表结构已就绪。

**RabbitMQ / Redis / Qdrant 报错**  
确认服务已启动，且地址端口与配置文件一致。

**前端 401 / 跨域**  
确认 `api.js` 中 `baseURL` 指向正确后端；若前后端域名端口不同，需在后端配置 CORS（本项目若已内置则按需检查）。

---

## 许可证与贡献

根据需要在本仓库添加 `LICENSE`；欢迎通过 Issue / Pull Request 贡献说明与 DDL 脚本等。
