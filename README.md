# Act One

Act One 是一个 AI 小说转剧本 YAML 工作台。它面向小说作者和编剧协作场景，把 3 章以上小说文本解析为可编辑、可追溯、可校验的结构化剧本初稿，并提供流式生成、草稿保存、局部精修、节奏分析、分镜建议和对白口吻重写等能力。

当前项目不是单纯的 LLM 文本生成 Demo。后端把 AI 生成拆成可观测的 Pipeline，并用 YAML Schema、阶段结果暂存、模型路由和规则降级保护生成结果；前端则围绕“导入小说、生成 YAML、编辑草稿、审查来源、导出结果”的作者工作流组织界面。

## 目录

- [项目定位](#项目定位)
- [当前项目情况](#当前项目情况)
- [核心链路](#核心链路)
- [功能清单](#功能清单)
- [技术栈](#技术栈)
- [系统结构](#系统结构)
- [快速启动](#快速启动)
- [配置说明](#配置说明)
- [数据库结构](#数据库结构)
- [API 概览](#api-概览)
- [测试与质量](#测试与质量)
- [项目边界与风险](#项目边界与风险)
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

不适合直接作为生产系统使用的部分见[项目边界与风险](#项目边界与风险)。

## 当前项目情况

| 维度 | 当前情况 | 结论 |
| --- | --- | --- |
| 产品形态 | 前后端分离的单体应用，提供登录、项目、草稿、AI 转换和演示小说工作流 | 已具备本地演示和毕业设计/答辩级完整流程 |
| 后端 | Spring Boot 3.5.7 + Java 17，单 Maven 模块，按包边界隔离 `ai`、`infra.ai`、`script.ai`、`script`、`user` | 架构边界清晰，但尚未拆成多 Maven 模块 |
| 前端 | React 18 + TypeScript + Vite，页面状态主要集中在 `App.tsx`，API 调用抽到 `services` 和 `types` | 适合当前工作台规模，后续复杂化时应继续拆页面和状态 |
| AI 能力 | OpenAI-compatible Chat 接口，配置了百炼和 SiliconFlow Provider，默认模型候选走优先级和健康状态筛选 | 具备多模型容错基础，质量依赖真实 API Key 和模型稳定性 |
| 生成链路 | 章节解析 -> 章节摘要 -> 场景规划 -> YAML 生成 -> Schema 校验 -> 成功或规则降级 | 比一次性 Prompt 更稳定，阶段边界可测试、可恢复 |
| 数据持久化 | PostgreSQL，包含用户、项目、章节、转换任务、阶段结果、草稿和草稿版本表 | 支持保存项目/草稿和持久化阶段结果 |
| 实时反馈 | SSE 返回 `meta`、`message`、`progress`、`validation`、`finish`、`cancel`、`error`、`done` 事件 | 前端可展示阶段进度、取消和失败信息 |
| 质量保障 | 后端已有 31 个测试类，本地 `mvn test` 验证通过 169 个测试 | 后端核心链路测试较完整，前端目前以类型检查和构建为主 |
| 部署成熟度 | 需要手动初始化数据库和配置环境变量，无 Docker、无数据库迁移框架 | 当前更接近开发/演示态，生产化仍需补齐部署和安全能力 |

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

- `qwen3-max`
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
| 后端框架 | Spring Boot | 3.5.7 |
| ORM | MyBatis-Plus | 3.5.14 |
| 认证 | Sa-Token | 1.43.0 |
| AI 集成 | Spring AI OpenAI-compatible | 1.1.6 |
| YAML 处理 | SnakeYAML | Spring Boot 管理版本 |
| 数据库 | PostgreSQL + HikariCP | 本地或外部 PostgreSQL |
| 构建工具 | Maven | 3.9+ 推荐 |
| 前端框架 | React | 18.3.1 |
| 前端语言 | TypeScript | 5.7.2 |
| 前端构建 | Vite | 5.4.11 |
| 图标 | lucide-react | 0.468.0 |

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

```bash
psql -U postgres -f backend/src/main/resources/db/schema_pg.sql
```

默认管理员账号仅用于本地演示：

```text
username: admin
password: admin
```

### 2. 配置后端环境变量

可以在 `backend/.env` 中放本地环境变量。该文件不应提交到 Git。

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

### 5. 推荐演示流程

1. 访问 `http://localhost:5173`。
2. 使用 `admin / admin` 登录。
3. 点击内置演示小说《星轨回音》或粘贴自己的小说文本。
4. 先做章节预览，确认识别到不少于 3 章。
5. 创建项目并开始流式生成 YAML。
6. 查看生成模式、校验状态、阶段进度和 YAML 内容。
7. 保存草稿，进入追溯、节奏分析、分镜建议或对白改写。
8. 导出 YAML 文件。

## 配置说明

主要配置位于 `backend/src/main/resources/application.yml`。

### 数据库

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ACT_ONE_DB_URL` | `jdbc:postgresql://localhost:5432/act_one?client_encoding=UTF8` | PostgreSQL JDBC 地址 |
| `ACT_ONE_DB_USERNAME` | `postgres` | 数据库用户名 |
| `ACT_ONE_DB_PASSWORD` | 空 | 数据库密码 |
| `ACT_ONE_DB_CONNECTION_TIMEOUT` | `5000` | Hikari 连接超时 |
| `ACT_ONE_DB_MAX_POOL_SIZE` | `10` | Hikari 最大连接数 |
| `ACT_ONE_DB_MIN_IDLE` | `5` | Hikari 最小空闲连接数 |

### AI Provider

| 环境变量 | 说明 |
| --- | --- |
| `BAILIAN_API_KEY` | 阿里云百炼 OpenAI-compatible Chat API Key |
| `SILICONFLOW_API_KEY` | SiliconFlow Chat API Key |

Provider 默认地址：

| Provider | Base URL | Chat Endpoint |
| --- | --- | --- |
| `bailian` | `https://dashscope.aliyuncs.com` | `/compatible-mode/v1/chat/completions` |
| `siliconflow` | `https://api.siliconflow.cn` | `/v1/chat/completions` |

### AI Pipeline

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ACT_ONE_SCRIPT_AI_MAX_STAGE_RETRIES` | `1` | 每个 AI 阶段最大重试次数 |
| `ACT_ONE_SCRIPT_AI_CHAPTER_SUMMARY_TIMEOUT` | `240s` | 章节摘要阶段超时 |
| `ACT_ONE_SCRIPT_AI_SCENE_PLANNING_TIMEOUT` | `600s` | 场景规划阶段超时 |
| `ACT_ONE_SCRIPT_AI_YAML_GENERATION_TIMEOUT` | `360s` | YAML 生成阶段超时 |
| `ACT_ONE_SCRIPT_AI_CHAPTER_SUMMARY_*` | 见 `application.yml` | 章节摘要线程池配置 |
| `ACT_ONE_SCRIPT_AI_SCENE_PLANNING_*` | 见 `application.yml` | 场景规划线程池配置 |
| `ACT_ONE_SCRIPT_AI_YAML_GENERATION_*` | 见 `application.yml` | YAML 生成线程池配置 |

## 数据库结构

初始化脚本：`backend/src/main/resources/db/schema_pg.sql`

| 表 | 作用 |
| --- | --- |
| `t_user` | 用户和默认管理员 |
| `t_script_project` | 小说转剧本项目 |
| `t_script_chapter` | 项目下的章节文本 |
| `t_script_conversion_task` | 转换任务状态、进度、失败原因 |
| `t_script_stage_result` | AI Pipeline 阶段结果暂存，用于安全恢复 |
| `t_script_draft` | 剧本 YAML 草稿 |
| `t_script_draft_revision` | 草稿版本表 |

当前项目没有接入 Flyway 或 Liquibase。数据库结构变更需要手动维护 SQL 脚本。

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

## 项目边界与风险

当前项目已经能支撑完整演示和本地开发，但仍有明显生产化缺口：

| 风险 | 当前状态 | 建议 |
| --- | --- | --- |
| 密码安全 | 默认管理员和用户密码为明文存储 | 接入密码哈希、密码策略和初始化时强制改密 |
| 默认账号 | SQL 脚本插入 `admin / admin` | 仅本地演示使用，生产环境必须删除或替换 |
| 数据库迁移 | 仅有手写 `schema_pg.sql` | 引入 Flyway 或 Liquibase 管理版本迁移 |
| 部署方式 | 无 Docker Compose、无生产配置模板 | 增加容器化部署和环境变量样例 |
| SSE 任务状态 | 运行中任务 Map 在内存中，阶段结果可持久化 | 多实例部署前需要外部任务协调或队列 |
| 前端架构 | 页面状态集中在 `App.tsx` | 功能继续增加时应拆路由、状态和工作台子页面 |
| 模型质量 | 依赖外部模型和 Prompt 输出稳定性 | 增加真实样本评测集、失败样例回归和成本统计 |
| 权限模型 | 已有 Sa-Token 和管理员角色，业务资源隔离较基础 | 强化项目/草稿归属校验和审计日志 |

## 参考文档

| 文档 | 内容 |
| --- | --- |
| `docs/script-yaml-schema.md` | 剧本 YAML Schema v1 字段说明 |
| `docs/ai-pipeline-skeleton.md` | AI Pipeline 阶段边界和失败策略 |
| `docs/ai-intermediate-models.md` | 章节摘要、场景规划和上下文中间模型 |
| `docs/ai-prompt-templates.md` | Prompt 模板说明 |
| `docs/ai-pipeline-logging.md` | AI Pipeline 日志与可观测性 |
| `docs/backend-package-boundaries.md` | 后端包边界和依赖方向 |
| `docs/frontend-api-layer.md` | 前端 API 与类型层约束 |
| `docs/demo-script.md` | 演示脚本 |
| `notice/` | 项目分析、规划、演讲稿和过程材料 |

## Git 远程仓库

```text
https://gitee.com/HJan0113/act-one.git
```
