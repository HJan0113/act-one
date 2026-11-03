# 开发参考

[返回项目首页](../README.md)

本文中的命令均从仓库根目录执行；前后端启动与本地数据库初始化见 [快速开始](../README.md#快速开始)。

## 目录

- [核心链路](#核心链路)
- [生成模式](#生成模式)
- [系统结构](#系统结构)
- [API 概览](#api-概览)
- [测试与质量](#测试与质量)

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

模型提供方、候选列表和优先级以 [application.yml](../backend/src/main/resources/application.yml) 的 `ai` 配置为准。当前启用的候选均使用百炼提供方。

路由策略按模型候选优先级、启用状态和健康状态选择目标；单个候选失败后会标记失败并尝试下一个候选。若 AI 阶段最终失败，剧本 Pipeline 会返回 `generationMode=rule_based_fallback`、`fallback=true` 和 `fallbackReason`，让前端和用户明确知道结果来自降级链路。

## 生成模式

| `generationMode` | 触发条件 |
| --- | --- |
| `ai_pipeline` | AI Pipeline 完整执行，生成结果通过 YAML Schema 校验 |
| `rule_based_scaffold` | AI Pipeline Bean 不可用，直接生成规则版脚手架 |
| `rule_based_fallback` | AI 阶段失败或生成的 YAML 校验失败，回退到规则版脚手架 |

规则版结果保留幕、场景和来源信息，供作者继续补写；它与 AI 完整生成的内容质量不同。三种模式的具体值由接口返回，前端可据此显示生成状态。

## 系统结构

```text
act-one/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/hjan/actone/
│       │   ├── ai/              # AI 端口、消息模型、Prompt 接口
│       │   ├── infra/ai/        # 模型客户端、路由、异常映射
│       │   ├── script/          # 剧本 Pipeline、项目、草稿、精修与分析
│       │   ├── user/            # 登录与用户管理
│       │   ├── common/          # 响应、异常、上下文、SSE 工具
│       │   └── config/          # 全局配置
│       ├── main/resources/     # 应用配置、演示小说、Prompt 模板
│       └── test/               # 单元测试和 Fake LLM 测试资源
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── App.tsx             # 页面状态与工作流编排
│       ├── components/         # 工作台组件
│       ├── services/           # API 封装
│       ├── types/              # 接口类型
│       └── utils/              # YAML 追溯解析等工具
├── sql/act-one.sql             # PostgreSQL 表结构
├── docs/                       # 剧本 Schema 与开发参考
└── README.md
```

### 后端包边界

后端采用单个 Maven 模块，通过包边界测试约束依赖方向：

```text
script.ai  -> script / common
infra.ai   -> ai / common / Spring AI
ai         -> JDK only
```

`BackendPackageBoundaryTest` 检查上述依赖方向，避免业务逻辑直接绑定具体模型客户端。

## API 概览

除健康检查和登录等公开入口外，业务接口需要登录。前端 API 封装位于 [frontend/src/services](../frontend/src/services)，认证请求头名为 `Authorization`。


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
# 在仓库根目录执行后端测试
cd backend
mvn test
```

测试结果以当前环境的实际运行输出为准。

覆盖重点包括：

- 章节解析、长章节分块和 YAML Schema 校验。
- AI Pipeline 成功、失败、重试、降级和日志。
- Prompt 模板加载、LLM 响应清理、模型路由和健康状态。
- 草稿、项目、转换任务、阶段结果等业务服务。
- 后端包边界约束。

### 前端检查

```bash
# 在仓库根目录执行前端类型检查与构建
cd frontend
npm run build
```

该命令会先运行 `tsc --noEmit`，再执行 Vite 生产构建。
