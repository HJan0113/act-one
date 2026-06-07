# Act One

AI 小说转剧本工具 — 从小说文本到可编辑、可追溯、可校验的结构化剧本 YAML 初稿。

## 项目简介

Act One 是一款面向小说作者的 AI 辅助编剧工作台。用户输入 3 章以上小说文本，系统通过精简 AI Pipeline 自动完成章节摘要、场景规划、剧本 YAML 生成和 Schema 校验，最终输出一份结构化、可编辑、符合 Schema v1 规范的剧本初稿。

### 核心特色

| 维度 | 说明 |
| --- | --- |
| **结构化改编** | 不是一次性 LLM 文本输出，而是章节摘要 → 场景规划 → YAML 生成 → Schema 校验的工程管线 |
| **长文本一致性** | 章节摘要沉淀人物、地点、冲突和来源摘录，为后续场景规划与 YAML 生成提供稳定输入 |
| **场景自动拆场** | 按地点/时间/人物/冲突四维变化拆分，不机械一章一场 |
| **直接生成可编辑剧本** | YAML 生成阶段同时完成人物归纳、动作影视化和对白初稿 |
| **来源可审查** | 每个场景绑定来源章节 ID 和原文摘录，作者可随时审查 AI 改编依据 |
| **Schema 安全网** | Schema 结构校验不通过时显式降级为规则版脚手架，不静默输出非法 YAML |
| **AI 透明降级** | AI 任何阶段失败都显式降级为规则版脚手架，结果暴露 `generationMode` 和 `fallbackReason` |
| **SSE 实时进度** | 作者能实时看到 AI 在做什么阶段，可随时停止，失败后可从安全阶段恢复 |
| **作者可控精修** | 局部命令式精修、对白口吻重写、多版本风格草稿，不重写全篇 |

### 精简 AI Pipeline

```
章节解析 → 章节摘要 → 场景规划 → YAML 生成 → Schema 校验 → 成功返回/规则兜底
```

## 技术栈

| 层级 | 技术 | 版本 |
| --- | --- | --- |
| 后端语言 | Java | 17 |
| 后端框架 | Spring Boot | 3.5.7 |
| ORM | MyBatis-Plus | 3.5.14 |
| 认证 | Sa-Token | 1.43.0 |
| AI 集成 | Spring AI (OpenAI-compatible) | 1.1.6 |
| AI 模型 | Bailian qwen3-max / SiliconFlow | — |
| 数据库 | PostgreSQL (HikariCP) | — |
| 构建工具 | Maven | 3.9+ |
| 前端框架 | React | 18.3 |
| 前端语言 | TypeScript | 5.7 |
| 前端构建 | Vite | 5.4 |

## 快速启动

### 环境要求

- Java 17+
- Maven 3.9+
- Node.js 18+
- PostgreSQL

### 1. 数据库初始化

```bash
# 创建数据库
createdb act_one

# 执行建表脚本
psql act_one < backend/src/main/resources/db/schema_pg.sql
```

### 2. 配置环境变量

在 `backend/.env` 中配置数据库和 AI API Key：

```bash
# 数据库配置
export DB_URL=jdbc:postgresql://localhost:5432/act_one
export DB_USERNAME=your_username
export DB_PASSWORD=your_password

# AI 模型配置（百炼）
export BAILIAN_API_KEY=your_api_key
```

⚠️ `backend/.env` 包含敏感信息，不进入 Git。

### 3. 启动后端

```bash
cd backend
set -a && source .env && set +a
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

访问：`http://localhost:5173`

默认管理员账号：`admin / admin`

## 项目结构

```
act-one/
├── backend/                         # Spring Boot 后端
│   └── src/main/java/com/hjan/actone/
│       ├── ai/                      # AI 抽象端口（LLMService、Prompt、消息模型）
│       ├── infra/ai/                # AI 基础设施实现（Spring AI、客户端、配置）
│       ├── script/
│       │   ├── ai/                  # 剧本 AI Pipeline（精简主链路 + 中间模型）
│       │   ├── controller/          # REST/SSE 控制器
│       │   ├── service/             # 业务服务
│       │   ├── dao/                 # 数据访问层（MyBatis-Plus）
│       │   └── model/               # 请求/响应 DTO
│       ├── common/                  # 通用基础设施（Result、异常、上下文）
│       ├── config/                  # 全局配置
│       └── user/                    # 用户认证与鉴权
├── frontend/                        # React + TypeScript 前端
│   └── src/
│       ├── App.tsx                  # 页面状态编排
│       ├── components/
│       │   └── workspace.tsx        # 所有 UI 组件
│       ├── services/                # API 客户端
│       ├── types/                   # TypeScript 类型定义
│       └── utils/                   # YAML 解析工具
├── docs/                            # 设计文档
│   ├── script-yaml-schema.md        # YAML Schema 规范
│   ├── ai-prompt-templates.md       # Prompt 模板说明
│   ├── demo-script.md               # 答辩演示脚本
│   └── ...
├── notice/                          # 项目规划文档
│   ├── pipline.md                   # 开发流水线
│   ├── 项目全景总结.md               # 全景架构说明
│   └── 创新功能列表.md               # 创新功能详细设计
└── output/                          # 构建输出
```

## 功能矩阵

### P0 — 核心转换能力

| 功能 | 说明 |
| --- | --- |
| 分阶段 AI 转换管线 | 精简 Pipeline，可解释可校验可迭代 |
| 长文本摘要记忆 | 章节摘要保留人物、地点、冲突、心理和来源摘录 |
| 场景规划与自动拆场 | 基于地点/时间/人物/冲突变化拆分场景 |
| YAML 直接生成 | 人物、动作影视化、对白和场景结构一次生成 |
| YAML Schema 校验与降级 | Schema 校验 + 规则脚手架降级安全网 |
| 改编依据 | YAML 场景保留 sourceChapterId + sourceExcerpt |
| 超长章节结构化分块 | 自然段优先 + 不切句 |
| AI 扩写标记 | 场景输出 ai_expansion，标识动作/对白是否包含改编扩展 |

### P1 — 作者精修工作台

| 功能 | 说明 |
| --- | --- |
| 来源对照三栏视图 | 小说原文 / YAML 场景 / 可读剧本并排展示 |
| 人物卡片与关系数据 | 角色身份/别名/目标/出场统计/共现线索 |
| 角色一致性报告 | 别名冲突/出场异常/未知引用/speaker 归属 |
| 局部命令式精修 | 只改指定场景/字段，返回结构化 patch |
| 对白口吻一致性与重写 | 按人物 + instruction 多场景对白重写 |
| 可拍动作候选 | 动作/表情/道具/沉默四类 before/after 对照 |
| AI 改动差异摘要 | 事实/表达/节奏/格式四类变更说明 |

### P2 — 展示增强

| 功能 | 说明 |
| --- | --- |
| 冲突热力图 | 按场景排列的颜色编码冲突强度网格（1-5 级） |
| 角色出场密度 | 角色出场场次、对白句数、密度条可视化 |
| 分镜与画面建议 | AI 生成镜头类型、主体、动作、声音、道具、色调 |
| 可读剧本预览 | YAML → 完整可读剧本格式渲染 |
| 一键演示 | 内置 10 章赛博朋克演示小说，自动填入 |
| 答辩演示脚本 | 完整演示路径文档 |

## API 接口

| Method | Path | 功能 |
| --- | --- | --- |
| POST | `/api/auth/login` | 用户登录 |
| GET | `/api/auth/current` | 获取当前用户 |
| GET | `/api/health` | 健康检查 |
| POST | `/api/novels/chapters/preview` | 章节解析预览 |
| POST | `/api/script/drafts/yaml/from-text` | 同步生成剧本 YAML |
| POST | `/api/script/drafts/yaml/from-text/stream` | SSE 流式生成剧本 YAML |
| POST | `/api/script/drafts/tasks/{id}/cancel` | 取消进行中任务 |
| POST | `/api/script/drafts` | 保存草稿 |
| GET | `/api/script/drafts?projectId=` | 草稿列表 |
| GET | `/api/script/drafts/{id}` | 草稿详情 |
| GET | `/api/script/drafts/{id}/export.yaml` | 导出 YAML 文件 |
| GET | `/api/script/drafts/{id}/rhythm-analysis` | 节奏分析（含角色密度） |
| POST | `/api/script/drafts/{id}/scenes/{sid}/refine` | 局部场景精修 |
| POST | `/api/script/drafts/{id}/scenes/{sid}/storyboard` | 分镜建议生成 |
| POST | `/api/script/drafts/{id}/dialogues/voice-rewrite` | 对白口吻重写 |
| GET | `/api/script/demo/novel` | 获取内置演示小说 |
| POST | `/api/script/projects` | 创建项目 |
| GET | `/api/script/projects` | 项目列表 |

## 测试

```bash
# 后端全量测试（218 项）
cd backend && mvn test

# 前端类型检查 + 构建
cd frontend && npm run build
```

## 演示步骤

1. 启动后端 + 前端，访问 `http://localhost:5173`
2. 使用 `admin / admin` 登录
3. 点击「一键演示」加载内置小说《星轨回音》（10 章赛博朋克风格）
4. 点击「解析章节」验证章节识别
5. 点击「开始生成」启动精简 AI Pipeline（SSE 流式进度可见）
6. 在「结果」页查看/编辑生成的 YAML，查看节奏分析指标
7. 点击「热力图」查看场景冲突和角色出场密度
8. 在「追溯」页查看原文/YAML/可读剧本三栏对照，可执行局部精修
9. 在「预览」页查看完整可读剧本
10. 在「追溯」页点击「生成分镜」获取镜头建议
11. 在「人物」页查看角色卡，执行对白口吻重写
12. 保存草稿并导出 YAML 文件

## 开发分支策略

- `main`：稳定主分支，普通代码内容
- `actone-ai-development`：AI 功能开发分支
- 所有变更最终合并回 `main`

## Git 远程仓库

```text
https://gitee.com/HJan0113/act-one.git
```
