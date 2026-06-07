# Act One

Act One 是「AI 小说转剧本工具」比赛项目的初始化仓库。项目目标是支持作者输入 3 个章节以上的小说文本，并生成可编辑、可继续打磨的结构化剧本 YAML 初稿。

当前版本包含前后端基础骨架、用户登录、章节解析、YAML 初稿生成、草稿保存与校验能力：

- `backend/`：Java 17 + Spring Boot 后端服务。
- `frontend/`：Vite + React + TypeScript 前端工作台。

## 后端启动

```bash
cd backend
mvn spring-boot:run
```

健康检查接口：

```bash
curl http://localhost:10001/api/health
```

数据库默认连接本地 `act_one`，远程或个人数据库配置从 `backend/.env` 读取：

```bash
set -a
source backend/.env
set +a
```

⚠️ `backend/.env` 包含敏感信息，不进入 Git。

## 前端启动

```bash
cd frontend
npm install
npm run dev
```

默认访问地址：

```text
http://localhost:5173
```

## Git 远程仓库

计划绑定远程仓库：

```text
https://gitee.com/HJan0113/act-one.git
```

当前初始化阶段不提交、不推送。
