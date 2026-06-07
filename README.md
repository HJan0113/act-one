
# Act One

Act One 是一个 AI 小说转剧本 YAML 工作台。它面向小说作者和编剧协作场景，把 3 章以上小说文本解析为可编辑、可追溯、可校验的结构化剧本初稿，并提供流式生成、草稿保存、局部精修、节奏分析、分镜建议和对白口吻重写等能力。

当前项目不是单纯的 LLM 文本生成 Demo。后端把 AI 生成拆成可观测的 Pipeline，并用 YAML Schema、阶段结果暂存、模型路由和规则降级保护生成结果；前端则围绕“导入小说、生成 YAML、编辑草稿、审查来源、导出结果”的作者工作流组织界面。

## 演示

- **演示视频（B站）**：[【七牛云-AI小说转剧本】](https://www.bilibili.com/video/BV1CVEh6zEnt/?share_source=copy_web&vd_source=352dbb347a529896debb8b2f5f734a5d)
- **备用视频链接**：[act-one.mp4](http://hjan.xin:9001/helloqiniu/act-one.mp4)
- **在线演示**：[hjan.xin/act-one](http://hjan.xin:3000 )

## 目录

- [项目定位](#项目定位)
- [核心链路](#核心链路)
- [功能清单](#功能清单)
- [技术栈](#技术栈)
- [原创实现与第三方依赖](#原创实现与第三方依赖)
- [系统结构](#系统结构)
- [快速启动](#快速启动)
- [数据库结构](#数据库结构)
- [测试与质量](#测试与质量)
- [参考文档](#参考文档)

## 项目定位

Act One 的目标是让小说改编剧本的早期阶段更可控：

- 输入长篇小说文本，识别章节并进行基础清洗。
- 通过 AI Pipeline 生成章节摘要、场景规划和剧本 YAML。
- 保留每个场景的来源章节和原文摘录，方便作者核对 AI 改编依据。
- 用 Schema 校验防止非法 YAML 静默进入草稿系统。
- 在模型失败、超时或 YAML 不合规时，显式降级为规则版脚手架，而不是返回不可用结果。
- 支持作者在生成后继续做局部精修、对白改写、节奏分析和分镜建议。

适合的使用场景：

- 小说作者快速得到可编辑的剧本初稿。
- 编剧团队把长文本拆成场景、幕结构和对白草稿。
- AI 应用开发中演示“模型编排 + 结构化生成 + 可追溯降级”的完整链路。


## 核心链路

### 小说转剧本 Pipeline

```text
小说文本
  -> NovelChapterParser 章节识别、清洗、长章节分块
  -> ChapterSummaryStage 章节摘要和证据提取
  -> ScenePlanningStage 场景规划和自动拆场
  -> ScriptYamlGenerationStage 剧本 YAML 生成
  -> ScriptYamlValidator Schema 校验
  -> 成功返回 ai_pipeline 结果
  -> 或显式降级为 rule_based_fallback
```

关键设计点：

- `NovelChapterParser` 要求至少识别 3 个章节；过长章节会按自然段和句子边界拆分。
- `ChapterSummary`、`ScenePlan`、`PipelineContext` 是 AI 阶段之间的结构化中间模型。
- Prompt 模板放在 `backend/src/main/resources/prompt/v1/`，通过 `PromptTemplateService` 渲染。
- 生成的 YAML 必须通过 `ScriptYamlValidator`，不合规则进入规则版 fallback。
- 规则版脚手架同样保留幕、场景、来源章节、原文摘录和基础元数据。

### 模型路由与降级

模型调用通过稳定端口 `LLMService` 进入基础设施层：

```text
script.ai Pipeline
  -> LLMService
  -> DefaultLLMService
  -> ModelSelector + ModelRoutingExecutor
  -> SpringAiChatClient
  -> OpenAI-compatible Provider
```

当前配置里聊天模型候选来自 `application.yml`：

- `qwen3.7-max-2026-05-17`
- `deepseek-v4-flash`

路由策略按模型候选优先级、启用状态和健康状态选择目标；单个候选失败后会标记失败并尝试下一个候选。若 AI 阶段最终失败，剧本 Pipeline 会返回 `generationMode=rule_based_fallback`、`fallback=true` 和 `fallbackReason`，让前端和用户明确知道结果来自降级链路。

## 功能清单

### 已落地功能

| 模块 | 功能 |
| --- | --- |
| 登录与用户 | 默认管理员登录、当前用户、退出、管理员用户管理、修改密码 |
| 小说导入 | 小说正文输入、章节预览、至少 3 章校验、内置演示小说《星轨回音》 |
| 项目管理 | 从文本创建项目、项目列表、项目删除 |
| YAML 生成 | 同步生成、SSE 流式生成、任务取消、失败任务安全阶段恢复 |
| 草稿管理 | 保存草稿、草稿列表、草稿详情、草稿删除、YAML 导出 |
| AI 追溯 | 场景来源章节、来源摘录、阶段结果暂存 |
| 作者精修 | 指定场景局部精修、对白口吻重写 |
| 分析展示 | 节奏分析、冲突指标、角色出场密度、分镜建议 |
| 工程质量 | 后端包边界测试、AI Pipeline Fake LLM 测试、模型路由/异常映射测试 |

### 生成模式

| `generationMode` | 含义 |
| --- | --- |
| `ai_pipeline` | AI Pipeline 完整执行并通过 YAML Schema 校验 |
| `rule_based_scaffold` | AI Pipeline Bean 不可用时，直接生成规则版脚手架 |
| `rule_based_fallback` | AI 阶段失败或 AI YAML 校验失败后，显式降级生成规则版脚手架 |

## 技术栈

| 层级 | 技术 | 当前版本 |
| --- | --- | --- |
| 后端语言 | Java | 17 |
| 后端框架 | Spring Boot (Web, JDBC, Validation) | 3.5.7 |
| ORM | MyBatis-Plus + JSqlParser | 3.5.14 |
| 认证 | Sa-Token | 1.43.0 |
| AI 集成 | Spring AI Chat Client + OpenAI 适配 | 1.1.6 |
| YAML 处理 | SnakeYAML | Spring Boot 管理版本 |
| 代码简化 | Lombok | Spring Boot 管理版本 |
| 数据库 | PostgreSQL + HikariCP | — |
| 构建工具 | Maven | 3.9+ 推荐 |
| 前端框架 | React + ReactDOM | 18.3.1 |
| 前端语言 | TypeScript | 5.7.2 |
| 前端构建 | Vite + @vitejs/plugin-react | 5.4.11 |
| 图标 | lucide-react | 0.468.0 |
| 前端类型 | @types/react, @types/react-dom | 18.x |

## 原创实现与第三方依赖

Act One 的核心价值来自以下**自研设计**，而非第三方框架自带能力：

| 原创实现 | 说明 |
| --- | --- |
| **AI Pipeline 编排** | 章节摘要 → 场景规划 → YAML 生成的分阶段流水线，每阶段保存中间结果，失败时可从安全阶段恢复 |
| **模型路由与健康状态跟踪** | 多模型候选优先级路由，单个失败自动尝试下一个，配合 Spring AI 但不依赖其路由能力 |
| **三级降级策略** | `ai_pipeline` → `rule_based_fallback` → `rule_based_scaffold`，确保任何故障下都有可用结果，并向前端明确传递降级原因 |
| **YAML Schema 校验** | 自定义 Schema 验证生成的剧本 YAML，不合规则触发降级，防止非法数据进入草稿系统 |
| **章节解析与分块** | 自研的 `NovelChapterParser`，支持中文小说章节识别、清洗、长章节按段落句子边界智能拆分 |
| **SSE 流式生成** | 基于 Spring MVC 的 SSE 流式推送 Pipeline 各阶段进度，前端实时渲染生成状态 |
| **溯源追溯** | 每个剧本场景保留来源章节和原文摘录，方便作者核对 AI 改编依据 |
| **对白精修与分镜建议** | 生成后支持按场景局部精修、对白口吻重写和分镜建议，非一次生成结束 |

以下能力由第三方框架提供，项目在其上构建业务逻辑：

| 能力 | 依赖框架 |
| --- | --- |
| Web 服务、REST 接口 | Spring Boot (Web Starter, 内嵌 Tomcat) |
| 数据库访问与连接池 | MyBatis-Plus + HikariCP |
| 登录认证与会话管理 | Sa-Token |
| LLM 调用与 OpenAI 协议适配 | Spring AI (Chat Client + OpenAI Starter) |
| 参数校验 | Spring Boot Validation (Hibernate Validator) |
| YAML 序列化/反序列化 | SnakeYAML |
| 前端 UI 框架与构建 | React + Vite |

## 系统结构

```text
act-one/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/hjan/actone/
│       │   ├── ActOneApplication.java
│       │   ├── ai/                  # AI 稳定端口、消息模型、Prompt 接口
│       │   ├── infra/ai/            # Spring AI 客户端、模型路由、异常映射、响应清理
│       │   ├── script/
│       │   │   ├── ai/              # 剧本 AI Pipeline、中间模型、阶段编排
│       │   │   ├── controller/      # 小说、项目、草稿、Demo REST/SSE 接口
│       │   │   ├── service/         # 章节解析、YAML 生成、草稿、精修、分析
│       │   │   ├── dao/             # MyBatis-Plus Mapper 和 DO
│       │   │   └── model/           # 请求/响应 DTO
│       │   ├── user/                # 登录、用户管理、Sa-Token 集成
│       │   ├── common/              # Result、异常、上下文、SSE Sender
│       │   └── config/              # 数据库等全局配置
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── db/schema_pg.sql
│       │   ├── demo/星轨回音.txt
│       │   └── prompt/v1/
│       └── test/                    # 后端单元测试和 Fake LLM 测试资源
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── App.tsx                  # 当前主要页面状态和工作流编排
│       ├── components/              # 工作台 UI 组件
│       ├── services/                # auth/script API 封装
│       ├── types/api.ts             # 前后端 DTO 类型
│       ├── utils/                   # YAML 追溯解析工具
│       └── styles.css
├── docs/                            # 架构、Schema、Prompt、前端 API 层说明
├── notice/                          # 项目分析、演讲稿、规划和过程材料
├── output/                          # 本地输出和历史中间产物
└── README.md
```

### 后端包边界

当前后端暂不拆 Maven 多模块，但通过包边界和测试固定依赖方向：

```text
script.ai  -> script / common
infra.ai   -> ai / common / Spring AI
ai         -> JDK only
```

`BackendPackageBoundaryTest` 会检查 AI 端口层、模型基础设施层和剧本 Pipeline 层的依赖方向，避免业务逻辑直接绑定具体模型客户端。

## 快速启动

### 环境要求

- Java 17+
- Maven 3.9+
- Node.js 18+
- PostgreSQL

### 1. 初始化数据库

初始化脚本会创建 `act_one` 数据库、建表并插入默认管理员。

脚本路径：sql/act-one.sql

默认管理员账号仅用于本地演示：

```text
username: admin
password: admin
```

### 2. 配置后端环境变量

可以在 `backend/.env` 中放本地环境变量。当前已将.env.example放入根目录，修改为自己的配置即可

```bash
export ACT_ONE_DB_URL=jdbc:postgresql://localhost:5432/act_one?client_encoding=UTF8
export ACT_ONE_DB_USERNAME=postgres
export ACT_ONE_DB_PASSWORD=your_password

export BAILIAN_API_KEY=your_bailian_api_key
export SILICONFLOW_API_KEY=your_siliconflow_api_key
```

没有可用模型 Key 时，应用仍可启动；AI 调用失败后会降级到规则版脚手架，但生成质量会明显下降。

### 3. 启动后端

```bash
cd backend
set -a
source .env
set +a
mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:10001/api/health
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

`frontend/vite.config.ts` 已将 `/api` 代理到 `http://localhost:10001`。

## API 概览

### 基础与认证

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/health` | 健康检查 |
| `POST` | `/api/auth/login` | 登录 |
| `POST` | `/api/auth/logout` | 退出 |
| `GET` | `/api/user/me` | 当前用户 |
| `PUT` | `/api/user/password` | 修改当前用户密码 |

### 用户管理

管理员权限接口：

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/users` | 用户分页列表 |
| `POST` | `/api/users` | 创建用户 |
| `PUT` | `/api/users/{id}` | 更新用户 |
| `DELETE` | `/api/users/{id}` | 删除用户 |

### 小说、项目与草稿

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/novels/chapters/preview` | 章节解析预览 |
| `GET` | `/api/script/demo/novel` | 获取内置演示小说 |
| `POST` | `/api/script/projects/from-text` | 从小说文本创建项目 |
| `GET` | `/api/script/projects/my` | 当前用户项目列表 |
| `DELETE` | `/api/script/projects/{projectId}` | 删除项目 |
| `POST` | `/api/script/drafts/yaml/from-text` | 同步生成剧本 YAML |
| `POST` | `/api/script/drafts/yaml/from-text/stream` | SSE 流式生成剧本 YAML |
| `POST` | `/api/script/drafts/tasks/{taskId}/cancel` | 取消生成任务 |
| `POST` | `/api/script/drafts` | 保存草稿 |
| `GET` | `/api/script/drafts?projectId={projectId}` | 草稿列表 |
| `GET` | `/api/script/drafts/{draftId}` | 草稿详情 |
| `DELETE` | `/api/script/drafts/{draftId}` | 删除草稿 |
| `GET` | `/api/script/drafts/{draftId}/export.yaml` | 导出 YAML |

### 分析与精修

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/script/drafts/{draftId}/rhythm-analysis` | 节奏分析和角色密度 |
| `POST` | `/api/script/drafts/{draftId}/scenes/{sceneId}/refine` | 局部场景精修 |
| `POST` | `/api/script/drafts/{draftId}/scenes/{sceneId}/storyboard` | 分镜建议 |
| `POST` | `/api/script/drafts/{draftId}/dialogues/voice-rewrite` | 对白口吻重写 |

## 测试与质量

### 后端测试

```bash
cd backend
mvn test
```

当前本地验证结果：

```text
Tests run: 169, Failures: 0, Errors: 0, Skipped: 0
```

覆盖重点包括：

- 章节解析、长章节分块和 YAML Schema 校验。
- AI Pipeline 成功、失败、重试、降级和日志。
- Prompt 模板加载、LLM 响应清理、模型路由和健康状态。
- 草稿、项目、转换任务、阶段结果等业务服务。
- 后端包边界约束。

### 前端检查

```bash
cd frontend
npm run build
```

该命令会先运行 `tsc --noEmit`，再执行 Vite 生产构建。


## Git 远程仓库

```text
https://gitee.com/HJan0113/act-one.git
```
