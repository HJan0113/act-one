import {useState, type ChangeEvent, type ReactNode} from "react";
import {
    Activity,
    BookOpen,
    CheckCircle2,
    ChevronRight,
    CircleStop,
    ClipboardCopy,
    Code2,
    Film,
    FileDown,
    FileInput,
    FileText,
    FolderOpen,
    History,
    ListChecks,
    PanelLeftClose,
    PanelLeftOpen,
    Radio,
    RefreshCcw,
    Save,
    ScrollText,
    ShieldCheck,
    Sparkles,
    UploadCloud
} from "lucide-react";

import {ActionButton} from "./ActionButton";
import {EmptyState} from "./EmptyState";
import {StatusBadge, type StatusTone} from "./StatusBadge";
import type {ScriptCharacterCard, ScriptRelationship, ScriptTraceScene} from "../utils/scriptYamlTrace";

export type ChapterOutline = {
  content: string;
  excerpt: string;
  id: string;
  index: number;
  title: string;
  wordCount: number;
};

export type DraftListItem = {
  draftId: string;
  schemaVersion: string;
  title: string;
  validationStatus: string;
};

export type ProjectListItem = {
  description?: string | null;
  projectId: string;
  status: string;
  title: string;
};

export type ProgressStep = {
  detail: string;
  label: string;
  status: "active" | "complete" | "error" | "idle" | "warning";
};

export type TaskStatus = "canceled" | "failed" | "idle" | "ready" | "running" | "success";

export type TaskSummary = {
    canCancel: boolean;
    canRetry: boolean;
    currentStage: string;
    errorMessage: string;
    errorType?: string;
    failedStage?: string;
    message: string;
    progress: number;
    resumeStage?: string;
    retryable?: boolean | null;
    status: TaskStatus;
    taskId: string;
    usageSummary?: {
        callCount: number;
        estimated: boolean;
        totalTokens: number;
    } | null;
};

export type ValidationCheck = {
  detail: string;
  label: string;
  state: "fail" | "pass" | "pending" | "warn";
};

export type AiChangeSummary = {
  changes: Array<{
    detail: string;
    tone: StatusTone;
    type: "表达" | "格式" | "节奏" | "事实";
  }>;
  riskWarnings: string[];
  sceneIds: string[];
  summary: string;
  validationStatus: string;
};

export type ScriptStyleVariant = "broadcast" | "screenplay" | "short_drama" | "stage";

export const scriptStyleVariantOptions: Array<{
  detail: string;
  label: string;
  prompt: string;
  value: ScriptStyleVariant;
}> = [
  {
    detail: "保留场景动作、对白、调度，适合常规影视剧本。",
    label: "影视剧",
    prompt: "强化镜头可拍性、场面调度、动作节奏和对白表演，保持原剧情事实不变。",
    value: "screenplay"
  },
  {
    detail: "更短节奏、更强钩子，适合连续反转和高密度冲突。",
    label: "短剧",
    prompt: "压缩铺垫，突出开场钩子、场景冲突、反转节点和强情绪对白，保持原剧情事实不变。",
    value: "short_drama"
  },
  {
    detail: "突出舞台空间、出入场、灯光和可演台词。",
    label: "舞台剧",
    prompt: "强化舞台调度、出入场、灯光/道具提示和可朗读台词，保持原剧情事实不变。",
    value: "stage"
  },
  {
    detail: "以声音叙事，强化旁白、环境音和对白辨识度。",
    label: "广播剧",
    prompt: "强化声音线索、环境音、旁白过渡和角色口吻辨识度，保持原剧情事实不变。",
    value: "broadcast"
  }
];

export type WorkspaceView = "archive" | "characters" | "consistency" | "pipeline" | "result" | "source" | "trace";

type AppShellProps = {
    activeView: WorkspaceView;
  children: ReactNode;
  sidebar: ReactNode;
    sidebarCollapsed: boolean;
  topBar: ReactNode;
    onNavigate: (view: WorkspaceView) => void;
    onToggleSidebar: () => void;
};

export function AppShell({
                             activeView,
                             children,
                             onNavigate,
                             onToggleSidebar,
                             sidebar,
                             sidebarCollapsed,
                             topBar
                         }: AppShellProps) {
  return (
      <main className="app-shell" data-sidebar-collapsed={sidebarCollapsed}>
      <div className="studio-frame">
        {topBar}
          <div className="workspace-layout">
              <nav className="workspace-rail" aria-label="工作台页面">
                  <button
                      aria-label={sidebarCollapsed ? "展开项目栏" : "压缩项目栏"}
                      className="rail-toggle"
                      onClick={onToggleSidebar}
                      type="button"
                  >
                      {sidebarCollapsed ? <PanelLeftOpen size={18} aria-hidden="true"/> :
                          <PanelLeftClose size={18} aria-hidden="true"/>}
                  </button>
                  <div className="workspace-nav">
                      {workspaceNavItems.map((item) => (
                          <button
                              aria-current={activeView === item.id ? "page" : undefined}
                              className="workspace-nav__item"
                              data-active={activeView === item.id}
                              key={item.id}
                              onClick={() => onNavigate(item.id)}
                              type="button"
                          >
                              <span className="workspace-nav__icon">{item.icon}</span>
                              <span className="workspace-nav__text">
                    <strong>{item.label}</strong>
                    <small>{item.detail}</small>
                  </span>
                          </button>
                      ))}
                  </div>
              </nav>
              <div className="workspace-sidebar">{sidebar}</div>
              <section className="workspace-page" data-view={activeView} aria-live="polite">
                  <div className="workspace-page__inner" key={activeView}>
                      {children}
                  </div>
          </section>
        </div>
      </div>
    </main>
  );
}

const workspaceNavItems: Array<{
    detail: string;
    icon: ReactNode;
    id: WorkspaceView;
    label: string;
}> = [
    {
        detail: "导入与解析",
        icon: <BookOpen size={18} aria-hidden="true"/>,
        id: "source",
        label: "原文"
    },
    {
        detail: "任务与阶段",
        icon: <Activity size={18} aria-hidden="true"/>,
        id: "pipeline",
        label: "转换"
    },
    {
        detail: "YAML 与校验",
        icon: <Code2 size={18} aria-hidden="true"/>,
        id: "result",
        label: "结果"
    },
    {
        detail: "源文对照",
        icon: <ScrollText size={18} aria-hidden="true"/>,
        id: "trace",
        label: "追溯"
    },
    {
        detail: "角色卡片",
        icon: <ListChecks size={18} aria-hidden="true"/>,
        id: "characters",
        label: "人物"
    },
    {
        detail: "一致性报告",
        icon: <ShieldCheck size={18} aria-hidden="true"/>,
        id: "consistency",
        label: "校验"
    },
    {
        detail: "项目与草稿",
        icon: <History size={18} aria-hidden="true"/>,
        id: "archive",
        label: "归档"
    }
];

type TopBarProps = {
  actions: ReactNode;
  status: ReactNode;
};

export function TopBar({ actions, status }: TopBarProps) {
  return (
    <header className="top-bar">
      <div className="top-bar__brand">
        <span className="product-mark" aria-hidden="true">
          <Sparkles size={18} />
        </span>
        <div>
          <p className="top-bar__eyebrow">Act One</p>
          <h1>AI 小说转剧本工作台</h1>
        </div>
      </div>
      <div className="top-bar__meta">
        {status}
        <div className="top-bar__actions">{actions}</div>
      </div>
    </header>
  );
}

type SidebarProps = {
  author: string;
  chapterCount: number;
  children: ReactNode;
  isValid: boolean;
  projectId: string;
  title: string;
};

export function Sidebar({ author, chapterCount, children, isValid, projectId, title }: SidebarProps) {
  return (
    <aside className="sidebar" aria-label="项目导航">
      <div className="sidebar__section sidebar__project">
        <div className="sidebar__section-title">
          <FolderOpen size={16} aria-hidden="true" />
          <span>当前项目</span>
        </div>
        <h2>{title || "未命名剧本"}</h2>
        <p>{author || "作者未填写"}</p>
        <div className="sidebar__badges">
          <StatusBadge label={projectId ? "已建项目" : "本地草稿"} tone={projectId ? "info" : "neutral"} />
          <StatusBadge
            label={chapterCount > 0 ? `${chapterCount} 章` : "待解析"}
            tone={isValid ? "success" : chapterCount > 0 ? "warning" : "neutral"}
          />
        </div>
      </div>

      {children}

      <div className="sidebar__section sidebar__future">
        <div className="sidebar__section-title">
          <Sparkles size={16} aria-hidden="true" />
          <span>AI 扩展槽</span>
        </div>
        <div className="future-list">
          <span>人物表抽取</span>
          <span>对白精修</span>
          <span>多版本对比</span>
        </div>
      </div>
    </aside>
  );
}

type TaskConsoleProps = {
    summary: TaskSummary;
    onCancel: () => void;
    onRetry: () => void;
};

export function TaskConsole({onCancel, onRetry, summary}: TaskConsoleProps) {
    return (
        <section className="sidebar__section task-console" data-status={summary.status} aria-label="AI 转换任务控制台">
            <div className="sidebar__section-title">
                <Activity size={16} aria-hidden="true"/>
                <span>任务控制台</span>
                <StatusBadge label={taskStatusLabel(summary.status)} tone={taskStatusTone(summary.status)}/>
            </div>
            <div className="task-console__body">
                <div className="task-console__metric">
                    <strong>{summary.progress}%</strong>
                    <span>{summary.currentStage}</span>
                </div>
                <progress aria-label="AI 转换进度" className="task-meter" max={100} value={summary.progress}/>
                <p>{summary.errorMessage || summary.message}</p>
                {summary.status === "failed" ? (
                    <div className="task-console__recovery">
                        <span>{summary.failedStage ? stageDisplayName(summary.failedStage) : "失败阶段未知"}</span>
                        <span>{summary.errorType || "RuntimeException"}</span>
                        <span>
                            {summary.retryable === false
                                ? "不可重试"
                                : `可从 ${summary.resumeStage ? stageDisplayName(summary.resumeStage) : "安全阶段"} 继续`}
                        </span>
                    </div>
                ) : null}
                <div className="task-console__meta">
                    <span>{summary.taskId || "未创建任务"}</span>
                    <div className="task-console__actions">
                        <ActionButton
                            disabled={!summary.canRetry}
                            icon={<RefreshCcw aria-hidden="true" size={14}/>}
                            onClick={onRetry}
                            size="sm"
                            variant="secondary"
                        >
                            重试
                        </ActionButton>
                        <ActionButton
                            disabled={!summary.canCancel}
                            icon={<CircleStop aria-hidden="true" size={14}/>}
                            onClick={onCancel}
                            size="sm"
                            variant="ghost"
                        >
                            停止
                        </ActionButton>
                    </div>
                </div>
            </div>
        </section>
    );
}

function stageDisplayName(stage: string) {
    return stageLabels[stage] || stage;
}

function usageSummaryLabel(summary: TaskSummary["usageSummary"]) {
    if (!summary || summary.callCount <= 0) {
        return "待统计";
    }
    const tokenPrefix = summary.estimated ? "~" : "";
    return `${summary.callCount} 次 / ${tokenPrefix}${summary.totalTokens} tokens`;
}

const stageLabels: Record<string, string> = {
    CHAPTER_SUMMARY: "章节摘要",
    COMPLETED: "转换完成",
    CONSISTENCY_CHECK: "一致性校验",
    DIALOGUE_GENERATION: "对白生成",
    FACT_CONSTRAINT_CHECK: "事实约束",
    GLOBAL_STORY_EXTRACTION: "全局故事抽取",
    INIT: "任务初始化",
    PSYCHOLOGICAL_VISUALIZATION: "心理影视化",
    SCENE_PLANNING: "场景规划",
    SCRIPT_YAML_GENERATION: "YAML 生成",
    SOURCE_TRACE: "源文追溯",
    YAML_REPAIR: "YAML 自动修复",
    YAML_VALIDATION: "Schema 校验"
};

type ChapterListProps = {
  activeChapterIndex: number;
  chapters: ChapterOutline[];
  chapterCount: number;
  onSelectChapter: (index: number) => void;
};

export function ChapterList({ activeChapterIndex, chapterCount, chapters, onSelectChapter }: ChapterListProps) {
  return (
    <section className="sidebar__section chapter-list" id="chapter-preview" aria-label="章节管理">
      <div className="sidebar__section-title">
        <FileText size={16} aria-hidden="true" />
        <span>章节管理</span>
        {chapterCount > 0 ? <strong>{chapterCount}</strong> : null}
      </div>
      {chapters.length > 0 ? (
        <div className="chapter-list__items">
          {chapters.map((chapter) => (
            <button
              className="chapter-list__item"
              data-active={chapter.index === activeChapterIndex}
              key={chapter.id}
              onClick={() => onSelectChapter(chapter.index)}
              type="button"
            >
              <span className="chapter-list__index">{String(chapter.index).padStart(2, "0")}</span>
              <span className="chapter-list__body">
                <span>{chapter.title}</span>
                <small>{chapter.wordCount} 字</small>
              </span>
              <ChevronRight size={15} aria-hidden="true" />
            </button>
          ))}
        </div>
      ) : (
        <EmptyState
          description="粘贴或导入包含章节标题的小说文本后，这里会形成可跳转的章节索引。"
          icon={<BookOpen size={18} />}
          size="sm"
          title="暂无章节"
        />
      )}
    </section>
  );
}

type ProjectListProps = {
  activeProjectId: string;
  loading: boolean;
  onRefresh: () => void;
  onSelectProject: (projectId: string) => void;
  projects: ProjectListItem[];
};

export function ProjectList({ activeProjectId, loading, onRefresh, onSelectProject, projects }: ProjectListProps) {
  return (
    <section className="sidebar__section project-list" aria-label="我的项目">
      <div className="sidebar__section-title">
        <FolderOpen size={16} aria-hidden="true" />
        <span>我的项目</span>
        <button aria-label="刷新我的项目" className="sidebar-icon-button" disabled={loading} onClick={onRefresh} type="button">
          <RefreshCcw aria-hidden="true" className={loading ? "spin" : ""} size={14} />
        </button>
      </div>
      {projects.length > 0 ? (
        <div className="project-list__items">
          {projects.map((project) => (
            <button
              className="project-list__item"
              data-active={project.projectId === activeProjectId}
              key={project.projectId}
              onClick={() => onSelectProject(project.projectId)}
              type="button"
            >
              <span>{project.title}</span>
              <small>{project.status || "draft"}</small>
            </button>
          ))}
        </div>
      ) : (
        <EmptyState description="保存草稿后会在这里形成项目入口。" icon={<FolderOpen size={18} />} size="sm" title="暂无项目" />
      )}
    </section>
  );
}

type InputPanelProps = {
  author: string;
  chapterCount: number;
  characterCount: number;
  isValid: boolean;
  logline: string;
  message: string;
  novelText: string;
    locked: boolean;
  onAuthorChange: (value: string) => void;
  onFileImport: (file: File) => Promise<void> | void;
  onLoglineChange: (value: string) => void;
  onNovelTextChange: (value: string) => void;
  onTitleChange: (value: string) => void;
  selectedChapter?: ChapterOutline;
  title: string;
};

export function InputPanel({
  author,
  chapterCount,
  characterCount,
  isValid,
  logline,
  message,
  novelText,
                               locked,
  onAuthorChange,
  onFileImport,
  onLoglineChange,
  onNovelTextChange,
  onTitleChange,
  selectedChapter,
  title
}: InputPanelProps) {
  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const input = event.currentTarget;
    const file = input.files?.[0];
    if (file) {
      await onFileImport(file);
    }
    input.value = "";
  }

  return (
    <section className="studio-panel input-panel" id="novel-input" aria-labelledby="novel-input-title">
      <div className="panel-heading">
        <div>
          <p className="panel-kicker">Novel Source</p>
          <h2 id="novel-input-title">小说输入</h2>
        </div>
        <label className="file-import-button">
          <UploadCloud size={16} aria-hidden="true" />
          <span>导入文本</span>
          <input
            accept=".txt,.md,.markdown,text/plain,text/markdown"
            className="visually-hidden"
            disabled={locked}
            onChange={handleFileChange}
            type="file"
          />
        </label>
      </div>

      <div className="metadata-grid" aria-label="剧本基础信息">
        <label className="field-control">
          <span>剧本标题</span>
          <input
            maxLength={128}
            disabled={locked}
            onChange={(event) => onTitleChange(event.target.value)}
            placeholder="未填写时使用第一章标题"
            value={title}
          />
        </label>
        <label className="field-control">
          <span>作者</span>
            <input
                disabled={locked}
                maxLength={64}
                onChange={(event) => onAuthorChange(event.target.value)}
                placeholder="可选"
                value={author}
            />
        </label>
        <label className="field-control field-control--wide">
          <span>一句话梗概</span>
          <input
              disabled={locked}
            maxLength={512}
            onChange={(event) => onLoglineChange(event.target.value)}
            placeholder="主角目标、冲突和转变"
            value={logline}
          />
        </label>
      </div>

      {!novelText.trim() ? (
        <EmptyState
          action={
            <label className="empty-upload">
              <FileInput size={15} aria-hidden="true" />
              <span>选择文本文件</span>
              <input
                accept=".txt,.md,.markdown,text/plain,text/markdown"
                className="visually-hidden"
                disabled={locked}
                onChange={handleFileChange}
                type="file"
              />
            </label>
          }
          description="至少准备 3 个章节，章节标题建议使用“第 1 章 / 第一章”等格式。"
          icon={<BookOpen size={22} />}
          title="导入或粘贴小说正文"
        />
      ) : null}

      <textarea
        aria-label="小说章节输入"
        className="novel-editor"
        disabled={locked}
        onChange={(event) => onNovelTextChange(event.target.value)}
        placeholder={"第 1 章\n\n在这里粘贴小说正文...\n\n第 2 章\n\n...\n\n第 3 章\n\n..."}
        spellCheck={false}
        value={novelText}
      />

      <div className="input-panel__footer">
        <div className="status-line" data-valid={isValid}>
          <CheckCircle2 aria-hidden="true" size={16} />
          <span>{message}</span>
        </div>
        <div className="editor-stats">
          <span>{characterCount} 字符</span>
          <span>{chapterCount > 0 ? `${chapterCount} 章` : "未解析"}</span>
        </div>
      </div>

      <section className="chapter-excerpt" aria-label="章节预览">
        <div className="chapter-excerpt__heading">
          <span>当前章节预览</span>
          {selectedChapter ? <strong>{selectedChapter.wordCount} 字</strong> : null}
        </div>
        {selectedChapter ? (
          <>
            <h3>{selectedChapter.title}</h3>
            <p>{selectedChapter.excerpt || "该章节暂无正文摘录。"}</p>
          </>
        ) : (
          <p className="muted-copy">解析章节后会显示当前章节的前段内容，便于对照生成结果。</p>
        )}
      </section>
    </section>
  );
}

type SourceTraceWorkbenchProps = {
  activeDraftId: string;
  activeSceneId: string;
  chapters: ChapterOutline[];
  refining: boolean;
  scenes: ScriptTraceScene[];
  onRefineScene: (sceneId: string, instruction: string) => Promise<void> | void;
  onSelectScene: (sceneId: string) => void;
  yamlReady: boolean;
};

export function SourceTraceWorkbench({
  activeDraftId,
  activeSceneId,
  chapters,
  onRefineScene,
  onSelectScene,
  refining,
  scenes,
  yamlReady
}: SourceTraceWorkbenchProps) {
  const [refineInstruction, setRefineInstruction] = useState("");
  const selectedScene = scenes.find((scene) => scene.id === activeSceneId) || scenes[0];
  const selectedChapter = selectedScene
    ? chapters.find(
      (chapter) => chapter.id === selectedScene.sourceChapterId || chapter.index === selectedScene.chapterIndex
    )
    : undefined;
  const sourceExcerpt = selectedScene?.sourceExcerpt.trim() || "";
  const hasExactExcerpt = Boolean(selectedChapter?.content && sourceExcerpt && selectedChapter.content.includes(sourceExcerpt));
  const canRefine = Boolean(activeDraftId && selectedScene && refineInstruction.trim() && !refining);

  async function submitRefinement() {
    if (!selectedScene || !canRefine) {
      return;
    }
    await onRefineScene(selectedScene.id, refineInstruction.trim());
    setRefineInstruction("");
  }

  return (
    <div className="trace-workbench" aria-label="源文追溯三栏对照">
      <section className="studio-panel trace-panel trace-panel--source" aria-label="小说原文">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Novel Source</p>
            <h2>小说原文</h2>
          </div>
          <StatusBadge label={hasExactExcerpt ? "摘录命中" : selectedChapter ? "章节定位" : "待定位"} tone={hasExactExcerpt ? "success" : selectedChapter ? "warning" : "neutral"} />
        </div>

        <div className="trace-column-body">
          {selectedChapter ? (
            <>
              <div className="trace-source-card">
                <span>{sourceChapterLabel(selectedChapter)}</span>
                <strong>{selectedChapter.title}</strong>
                <small>{selectedScene?.sourceChapterId || selectedChapter.id}</small>
              </div>
              {sourceExcerpt ? (
                <blockquote className="trace-source-excerpt">
                  <span>scene.source_excerpt</span>
                  <p>{sourceExcerpt}</p>
                </blockquote>
              ) : null}
              <article className="trace-source-text" aria-label="当前来源章节正文">
                {renderHighlightedSource(selectedChapter.content, sourceExcerpt)}
              </article>
            </>
          ) : (
            <EmptyState
              description="先导入小说并解析章节，或加载包含 chapter_index / source_chapter_id 的草稿。"
              icon={<BookOpen size={18} />}
              size="sm"
              title="暂无可定位原文"
            />
          )}
        </div>
      </section>

      <section className="studio-panel trace-panel trace-panel--yaml" aria-label="YAML 场景索引">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Structured Scenes</p>
            <h2>YAML 场景</h2>
          </div>
          <StatusBadge label={scenes.length > 0 ? `${scenes.length} 场` : yamlReady ? "无场景" : "待生成"} tone={scenes.length > 0 ? "info" : "neutral"} />
        </div>

        <div className="trace-column-body">
          {scenes.length > 0 ? (
            <div className="trace-scene-list">
              {scenes.map((scene) => (
                <button
                  className="trace-scene-card"
                  data-active={scene.id === selectedScene?.id}
                  key={scene.id}
                  onClick={() => onSelectScene(scene.id)}
                  type="button"
                >
                  <span className="trace-scene-card__index">{scene.id.replace(/^scene_?/, "") || scene.id}</span>
                  <span className="trace-scene-card__body">
                    <strong>{scene.title}</strong>
                    <small>
                      第 {scene.chapterIndex || "-"} 章 / {scene.location || "待补充"} / {scene.time || "待补充"}
                    </small>
                    <em>{scene.scenePurpose || "未写入场景目的"}</em>
                  </span>
                  <ChevronRight size={15} aria-hidden="true" />
                </button>
              ))}
            </div>
          ) : (
            <EmptyState
              description={yamlReady ? "当前 YAML 未解析到 scenes 列表，请检查缩进或字段名。" : "生成或加载 YAML 草稿后，这里会显示可点击 scene。"}
              icon={<Code2 size={18} />}
              size="sm"
              title="暂无 YAML 场景"
            />
          )}
        </div>
      </section>

      <section className="studio-panel trace-panel trace-panel--script" aria-label="可读剧本">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Readable Script</p>
            <h2>可读剧本</h2>
          </div>
          <StatusBadge label={selectedScene?.aiExpansion === true ? "AI 扩写" : selectedScene ? "原文支撑" : "待选择"} tone={selectedScene?.aiExpansion === true ? "warning" : selectedScene ? "success" : "neutral"} />
        </div>

        <div className="trace-column-body">
          {selectedScene ? (
            <article className="trace-screenplay">
              <header>
                <span>{selectedScene.id}</span>
                <h3>{selectedScene.title}</h3>
                <p>
                  {selectedScene.location || "待补充"} · {selectedScene.time || "待补充"}
                </p>
              </header>

              {selectedScene.scenePurpose ? (
                <section className="trace-script-note" aria-label="场景目的">
                  <span>场景目的</span>
                  <p>{selectedScene.scenePurpose}</p>
                </section>
              ) : null}

              <section className="trace-script-action" aria-label="动作描述">
                <span>Action</span>
                <p>{selectedScene.action || "该场景暂无动作描述。"}</p>
              </section>

              <section className="trace-dialogues" aria-label="对白">
                <div className="trace-dialogues__heading">
                  <span>Dialogues</span>
                  <strong>{selectedScene.dialogues.length}</strong>
                </div>
                {selectedScene.dialogues.length > 0 ? (
                  selectedScene.dialogues.map((dialogue, index) => (
                    <div className="trace-dialogue" key={`${dialogue.speaker}-${index}`}>
                      <strong>{dialogue.speaker || "未知说话人"}</strong>
                      <p>{dialogue.line || "未填写对白"}</p>
                      {dialogue.emotion ? <small>{dialogue.emotion}</small> : null}
                    </div>
                  ))
                ) : (
                  <p className="muted-copy">该场景暂无对白，可在后续对白精修阶段补齐。</p>
                )}
              </section>

              <footer className="trace-script-footer">
                <span>角色</span>
                <strong>{sceneCharactersLabel(selectedScene)}</strong>
              </footer>

              <section className="scene-refine-box" aria-label="局部命令式精修">
                <div className="scene-refine-box__heading">
                  <span>局部精修</span>
                  <strong>{activeDraftId ? "基于已保存草稿" : "需先保存草稿"}</strong>
                </div>
                <textarea
                  disabled={!activeDraftId || refining}
                  maxLength={1000}
                  onChange={(event) => setRefineInstruction(event.target.value)}
                  placeholder="例如：增强悬疑感，保留事实不变，补一句压低声音的对白。"
                  value={refineInstruction}
                />
                <ActionButton
                  disabled={!canRefine}
                  icon={<Sparkles aria-hidden="true" className={refining ? "spin" : ""} size={15} />}
                  onClick={submitRefinement}
                  size="sm"
                  variant="primary"
                >
                  生成 patch
                </ActionButton>
              </section>

              <section className="visualization-candidates" aria-label="心理描写局部影视化候选">
                <div className="visualization-candidates__heading">
                  <span>心理影视化</span>
                  <strong>{buildPsychologicalCandidates(selectedScene).length} 个候选</strong>
                </div>
                <div className="visualization-candidates__list">
                  {buildPsychologicalCandidates(selectedScene).map((candidate) => (
                    <article className="visualization-candidate" key={candidate.type}>
                      <header>
                        <span>{candidate.type}</span>
                        <strong>{candidate.title}</strong>
                      </header>
                      <div>
                        <small>Before</small>
                        <p>{candidate.before}</p>
                      </div>
                      <div>
                        <small>After</small>
                        <p>{candidate.after}</p>
                      </div>
                    </article>
                  ))}
                </div>
              </section>
            </article>
          ) : (
            <EmptyState
              description="选择一个 YAML scene 后，会在这里渲染成可读剧本格式。"
              icon={<ScrollText size={18} />}
              size="sm"
              title="暂无可读场景"
            />
          )}
        </div>
      </section>
    </div>
  );
}

function sourceChapterLabel(chapter: ChapterOutline) {
  return `第 ${String(chapter.index).padStart(2, "0")} 章 / ${chapter.wordCount} 字`;
}

function sceneCharactersLabel(scene: ScriptTraceScene) {
  if (scene.characters.length > 0) {
    return scene.characters.join("、");
  }
  const speakers = Array.from(new Set(scene.dialogues.map((dialogue) => dialogue.speaker).filter(Boolean)));
  return speakers.length > 0 ? speakers.join("、") : "待补充";
}

function renderHighlightedSource(content: string, excerpt: string): ReactNode {
  const body = content.trim();
  if (!body) {
    return <p className="muted-copy">当前章节暂无正文内容。</p>;
  }

  const target = excerpt.trim();
  const start = target ? body.indexOf(target) : -1;
  if (start < 0) {
    return <p>{body}</p>;
  }

  const before = body.slice(0, start);
  const after = body.slice(start + target.length);
  return (
    <p>
      {before}
      <mark>{target}</mark>
      {after}
    </p>
  );
}

type PsychologicalCandidate = {
  after: string;
  before: string;
  title: string;
  type: string;
};

function buildPsychologicalCandidates(scene: ScriptTraceScene): PsychologicalCandidate[] {
  const before = compactCandidateText(scene.action || scene.sourceExcerpt || scene.scenePurpose || scene.title);
  const evidence = compactCandidateText(scene.sourceExcerpt || scene.action || scene.title);
  const location = scene.location && scene.location !== "待补充" ? scene.location : "现场";
  return [
    {
      after: `${location}里，人物停住动作，视线落在一个具体细节上，再用一次短促的动作打断沉默。`,
      before,
      title: "把内心犹豫改成可拍动作",
      type: "动作"
    },
    {
      after: `人物没有解释情绪，只让呼吸、眼神和手部细节变化承接“${evidence}”。`,
      before,
      title: "用表情和身体反应替代心理旁白",
      type: "表情"
    },
    {
      after: `加入一个可见道具承载压力：人物触碰、握紧或避开它，让选择通过物件关系被看见。`,
      before,
      title: "用道具承接心理压力",
      type: "道具"
    },
    {
      after: `删除解释性句子，保留环境声和停顿，让对方的下一句对白推动人物做出选择。`,
      before,
      title: "用沉默和节奏制造转折",
      type: "沉默"
    }
  ];
}

function compactCandidateText(value: string) {
  const compacted = value.replace(/\s+/g, " ").trim();
  return compacted.length > 120 ? `${compacted.slice(0, 120)}...` : compacted || "当前场景暂无可对比文本。";
}

type CharacterWorkbenchProps = {
  activeDraftId: string;
  characters: ScriptCharacterCard[];
  relationships: ScriptRelationship[];
  rewritingVoice: boolean;
  scenes: ScriptTraceScene[];
  onRewriteVoice: (speaker: string, instruction: string) => Promise<void> | void;
  yamlReady: boolean;
};

type CharacterStat = {
  lineCount: number;
  sceneCount: number;
};

type RelationshipView = {
  description: string;
  id: string;
  sourceChapterIds: string[];
  sourceName: string;
  targetName: string;
  type: string;
};

export function CharacterWorkbench({
  activeDraftId,
  characters,
  onRewriteVoice,
  relationships,
  rewritingVoice,
  scenes,
  yamlReady
}: CharacterWorkbenchProps) {
  const [voiceInstruction, setVoiceInstruction] = useState("");
  const [voiceSpeaker, setVoiceSpeaker] = useState("");
  const candidateCharacters = characters.length > 0 ? characters : deriveCharacterCandidates(scenes);
  const usingCandidates = characters.length === 0 && candidateCharacters.length > 0;
  const characterStats = createCharacterStats(candidateCharacters, scenes);
  const relationshipViews =
    relationships.length > 0
      ? relationships.map((relationship) => createRelationshipView(relationship, candidateCharacters))
      : inferRelationshipViews(candidateCharacters, scenes);
  const selectedVoiceSpeaker = voiceSpeaker || candidateCharacters[0]?.name || "";
  const canRewriteVoice = Boolean(activeDraftId && selectedVoiceSpeaker && voiceInstruction.trim() && !rewritingVoice);

  async function submitVoiceRewrite() {
    if (!canRewriteVoice) {
      return;
    }
    await onRewriteVoice(selectedVoiceSpeaker, voiceInstruction.trim());
    setVoiceInstruction("");
  }

  return (
    <div className="character-workbench" aria-label="人物卡片与关系数据">
      <section className="studio-panel character-panel" aria-label="人物卡片">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Character Cards</p>
            <h2>人物卡片</h2>
          </div>
          <StatusBadge
            label={candidateCharacters.length > 0 ? `${candidateCharacters.length} 人` : yamlReady ? "无角色" : "待生成"}
            tone={candidateCharacters.length > 0 ? "info" : "neutral"}
          />
        </div>

        <div className="character-card-grid">
          {candidateCharacters.length > 0 ? (
            candidateCharacters.map((character) => {
              const stats = characterStats.get(characterIdentity(character)) || {lineCount: 0, sceneCount: 0};
              return (
                <article className="character-card" data-candidate={usingCandidates} key={character.id || character.name}>
                  <header>
                    <span>{character.id || "speaker_candidate"}</span>
                    <h3>{character.name}</h3>
                    <StatusBadge label={usingCandidates ? "候选" : character.role || "待补充"} tone={usingCandidates ? "warning" : "success"} />
                  </header>

                  <p>{character.description || (usingCandidates ? "从对白说话人自动识别，等待 AI 角色卡补全。" : "角色描述待补充。")}</p>

                  <dl className="character-facts">
                    <div>
                      <dt>别名</dt>
                      <dd>{character.aliases.length > 0 ? character.aliases.join(" / ") : "未记录"}</dd>
                    </div>
                    <div>
                      <dt>目标</dt>
                      <dd>{character.goal || character.motivation || "待补充"}</dd>
                    </div>
                    <div>
                      <dt>首次出场</dt>
                      <dd>{chapterIdLabel(character.firstAppearanceChapterId) || inferFirstChapterLabel(character, scenes)}</dd>
                    </div>
                    <div>
                      <dt>出场 / 对白</dt>
                      <dd>{stats.sceneCount} 场 / {stats.lineCount} 句</dd>
                    </div>
                  </dl>

                  <div className="character-sources">
                    {(character.sourceChapterIds.length > 0 ? character.sourceChapterIds : inferSourceChapterIds(character, scenes)).slice(0, 4).map((chapterId) => (
                      <span key={chapterId}>{chapterIdLabel(chapterId) || chapterId}</span>
                    ))}
                  </div>
                </article>
              );
            })
          ) : (
            <EmptyState
              description={yamlReady ? "当前 YAML 未提供 characters，也未从对白中识别到说话人。" : "生成或加载 YAML 草稿后，这里会显示角色卡片。"}
              icon={<ListChecks size={18} />}
              size="sm"
              title="暂无人物卡"
            />
          )}
        </div>
      </section>

      <section className="studio-panel relationship-panel" aria-label="人物关系数据">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Relationship Data</p>
            <h2>关系线索</h2>
          </div>
          <StatusBadge
            label={relationships.length > 0 ? `${relationships.length} 条结构化关系` : relationshipViews.length > 0 ? `${relationshipViews.length} 条共现线索` : "待补充"}
            tone={relationshipViews.length > 0 ? "info" : "neutral"}
          />
        </div>

        <div className="relationship-list">
          <section className="voice-rewrite-box" aria-label="对白口吻一致性与重写">
            <div className="voice-rewrite-box__heading">
              <span>口吻重写</span>
              <strong>{activeDraftId ? "多场景 patch" : "需先保存草稿"}</strong>
            </div>
            <label>
              <span>人物</span>
              <select
                disabled={!activeDraftId || rewritingVoice || candidateCharacters.length === 0}
                onChange={(event) => setVoiceSpeaker(event.target.value)}
                value={selectedVoiceSpeaker}
              >
                {candidateCharacters.length > 0 ? (
                  candidateCharacters.map((character) => (
                    <option key={characterIdentity(character)} value={character.name}>
                      {character.name}
                    </option>
                  ))
                ) : (
                  <option value="">暂无人物</option>
                )}
              </select>
            </label>
            <textarea
              disabled={!activeDraftId || rewritingVoice || candidateCharacters.length === 0}
              maxLength={1000}
              onChange={(event) => setVoiceInstruction(event.target.value)}
              placeholder="例如：统一为冷静、短句、带观察细节的侦查口吻，不改变事实。"
              value={voiceInstruction}
            />
            <ActionButton
              disabled={!canRewriteVoice}
              icon={<Sparkles aria-hidden="true" className={rewritingVoice ? "spin" : ""} size={15} />}
              onClick={submitVoiceRewrite}
              size="sm"
              variant="primary"
            >
              重写对白
            </ActionButton>
          </section>

          {relationshipViews.length > 0 ? (
            relationshipViews.map((relationship) => (
              <article className="relationship-item" key={relationship.id}>
                <header>
                  <span>{relationship.type}</span>
                  <strong>{relationship.sourceName} → {relationship.targetName}</strong>
                </header>
                <p>{relationship.description || "关系描述待补充。"}</p>
                {relationship.sourceChapterIds.length > 0 ? (
                  <div className="relationship-sources">
                    {relationship.sourceChapterIds.slice(0, 5).map((chapterId) => (
                      <span key={chapterId}>{chapterIdLabel(chapterId) || chapterId}</span>
                    ))}
                  </div>
                ) : null}
              </article>
            ))
          ) : (
            <EmptyState
              description="后续可由 AI 输出 relationships；当前页面已预留结构化关系和场景共现两种展示。"
              icon={<ShieldCheck size={18} />}
              size="sm"
              title="暂无关系数据"
            />
          )}
        </div>
      </section>
    </div>
  );
}

function deriveCharacterCandidates(scenes: ScriptTraceScene[]): ScriptCharacterCard[] {
  const speakers = Array.from(
    new Set(
      scenes.flatMap((scene) => [
        ...scene.characters,
        ...scene.dialogues.map((dialogue) => dialogue.speaker)
      ]).filter(Boolean)
    )
  );
  return speakers.map((speaker, index) => ({
    aliases: [],
    description: "",
    firstAppearanceChapterId: "",
    goal: "",
    id: `speaker_candidate_${String(index + 1).padStart(3, "0")}`,
    motivation: "",
    name: speaker,
    role: "对白候选",
    sourceChapterIds: []
  }));
}

function createCharacterStats(characters: ScriptCharacterCard[], scenes: ScriptTraceScene[]) {
  const stats = new Map<string, CharacterStat>();
  characters.forEach((character) => {
    stats.set(characterIdentity(character), {lineCount: 0, sceneCount: 0});
  });

  scenes.forEach((scene) => {
    characters.forEach((character) => {
      const identity = characterIdentity(character);
      const appearsInScene = sceneMentionsCharacter(scene, character);
      if (appearsInScene) {
        const current = stats.get(identity) || {lineCount: 0, sceneCount: 0};
        stats.set(identity, {
          lineCount: current.lineCount + scene.dialogues.filter((dialogue) => dialogueMatchesCharacter(dialogue.speaker, character)).length,
          sceneCount: current.sceneCount + 1
        });
      }
    });
  });

  return stats;
}

function inferRelationshipViews(characters: ScriptCharacterCard[], scenes: ScriptTraceScene[]): RelationshipView[] {
  const relationships = new Map<string, RelationshipView & {sceneIds: Set<string>}>();

  scenes.forEach((scene) => {
    const participants = characters.filter((character) => sceneMentionsCharacter(scene, character));
    for (let sourceIndex = 0; sourceIndex < participants.length; sourceIndex += 1) {
      for (let targetIndex = sourceIndex + 1; targetIndex < participants.length; targetIndex += 1) {
        const source = participants[sourceIndex];
        const target = participants[targetIndex];
        const key = [characterIdentity(source), characterIdentity(target)].sort().join("|");
        const current = relationships.get(key);
        const next = current || {
          description: "",
          id: key,
          sceneIds: new Set<string>(),
          sourceChapterIds: [],
          sourceName: source.name,
          targetName: target.name,
          type: "场景共现"
        };
        next.sceneIds.add(scene.id);
        next.sourceChapterIds = Array.from(new Set([...next.sourceChapterIds, scene.sourceChapterId].filter(Boolean)));
        next.description = `共同出现在 ${next.sceneIds.size} 个场景中，可作为关系图谱的候选边。`;
        relationships.set(key, next);
      }
    }
  });

  return Array.from(relationships.values()).map(({sceneIds: _sceneIds, ...relationship}) => relationship);
}

function createRelationshipView(relationship: ScriptRelationship, characters: ScriptCharacterCard[]): RelationshipView {
  return {
    description: relationship.description,
    id: relationship.id,
    sourceChapterIds: relationship.sourceChapterIds,
    sourceName: characterNameById(relationship.sourceCharacterId, characters),
    targetName: characterNameById(relationship.targetCharacterId, characters),
    type: relationship.type
  };
}

function characterNameById(characterId: string, characters: ScriptCharacterCard[]) {
  return characters.find((character) => character.id === characterId)?.name || characterId || "未知人物";
}

function sceneMentionsCharacter(scene: ScriptTraceScene, character: ScriptCharacterCard) {
  return (
    scene.characters.some((value) => valueMatchesCharacter(value, character)) ||
    scene.dialogues.some((dialogue) => dialogueMatchesCharacter(dialogue.speaker, character))
  );
}

function dialogueMatchesCharacter(speaker: string, character: ScriptCharacterCard) {
  return valueMatchesCharacter(speaker, character);
}

function valueMatchesCharacter(value: string, character: ScriptCharacterCard) {
  const normalized = value.trim();
  if (!normalized) {
    return false;
  }
  return [character.id, character.name, ...character.aliases].some((candidate) => candidate && candidate.trim() === normalized);
}

function characterIdentity(character: ScriptCharacterCard) {
  return character.id || character.name;
}

function inferFirstChapterLabel(character: ScriptCharacterCard, scenes: ScriptTraceScene[]) {
  const firstScene = scenes.find((scene) => sceneMentionsCharacter(scene, character));
  return firstScene ? chapterIdLabel(firstScene.sourceChapterId) || `第 ${firstScene.chapterIndex} 章` : "待补充";
}

function inferSourceChapterIds(character: ScriptCharacterCard, scenes: ScriptTraceScene[]) {
  return Array.from(
    new Set(scenes.filter((scene) => sceneMentionsCharacter(scene, character)).map((scene) => scene.sourceChapterId).filter(Boolean))
  );
}

function chapterIdLabel(chapterId: string) {
  const match = chapterId.match(/chapter[_-](\d+)/i);
  return match ? `第 ${Number(match[1])} 章` : "";
}

type ConsistencyWorkbenchProps = {
  characters: ScriptCharacterCard[];
  chapters: ChapterOutline[];
  scenes: ScriptTraceScene[];
  yamlReady: boolean;
};

type ConsistencyIssue = {
  detail: string;
  id: string;
  scope: string;
  severity: "fail" | "info" | "warn";
  title: string;
};

export function ConsistencyWorkbench({
  characters,
  chapters,
  scenes,
  yamlReady
}: ConsistencyWorkbenchProps) {
  const issues = yamlReady ? buildConsistencyIssues(characters, scenes, chapters) : [];
  const failCount = issues.filter((issue) => issue.severity === "fail").length;
  const warnCount = issues.filter((issue) => issue.severity === "warn").length;
  const infoCount = issues.filter((issue) => issue.severity === "info").length;

  return (
    <div className="consistency-workbench" aria-label="角色一致性自动校验">
      <section className="studio-panel consistency-summary" aria-label="一致性摘要">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Consistency Report</p>
            <h2>角色一致性</h2>
          </div>
          <StatusBadge
            label={issues.length > 0 ? `${issues.length} 项风险` : yamlReady ? "未发现风险" : "待生成"}
            tone={failCount > 0 ? "danger" : warnCount > 0 ? "warning" : yamlReady ? "success" : "neutral"}
          />
        </div>
        <div className="consistency-metrics">
          <div data-tone="danger">
            <span>阻断</span>
            <strong>{failCount}</strong>
          </div>
          <div data-tone="warning">
            <span>警告</span>
            <strong>{warnCount}</strong>
          </div>
          <div data-tone="info">
            <span>提示</span>
            <strong>{infoCount}</strong>
          </div>
          <div data-tone="success">
            <span>覆盖场景</span>
            <strong>{scenes.length}</strong>
          </div>
        </div>
      </section>

      <section className="studio-panel consistency-report" aria-label="一致性问题列表">
        <div className="panel-heading">
          <div>
            <p className="panel-kicker">Issues</p>
            <h2>检查结果</h2>
          </div>
          <StatusBadge label={`${characters.length} 人 / ${scenes.length} 场`} tone="info" />
        </div>

        <div className="consistency-list">
          {issues.length > 0 ? (
            issues.map((issue) => (
              <article className="consistency-item" data-severity={issue.severity} key={issue.id}>
                <span>{issue.scope}</span>
                <strong>{issue.title}</strong>
                <p>{issue.detail}</p>
              </article>
            ))
          ) : (
            <EmptyState
              description={yamlReady ? "当前角色卡、对白说话人、场景角色和源文摘录未触发规则风险。" : "生成或加载 YAML 后，将自动检查角色一致性。"}
              icon={<ShieldCheck size={18} />}
              size="sm"
              title="暂无一致性风险"
            />
          )}
        </div>
      </section>
    </div>
  );
}

function buildConsistencyIssues(
  characters: ScriptCharacterCard[],
  scenes: ScriptTraceScene[],
  chapters: ChapterOutline[]
): ConsistencyIssue[] {
  const issues: ConsistencyIssue[] = [];

  if (scenes.length === 0) {
    issues.push({
      detail: "YAML 未解析到 scenes，无法校验 scene characters、speaker 和 source evidence。",
      id: "scenes-empty",
      scope: "YAML",
      severity: "info",
      title: "暂无场景数据"
    });
    return issues;
  }

  if (characters.length === 0) {
    issues.push({
      detail: "characters 为空时，无法校验别名、首次出场和对白说话人是否属于已知角色。",
      id: "characters-empty",
      scope: "characters",
      severity: "warn",
      title: "缺少结构化角色卡"
    });
  }

  issues.push(...findAliasConflicts(characters));
  issues.push(...findFirstAppearanceIssues(characters, scenes));
  scenes.forEach((scene) => {
    issues.push(...findSceneCharacterIssues(scene, characters));
    issues.push(...findSpeakerIssues(scene, characters));
    issues.push(...findSourceEvidenceIssues(scene, chapters));
  });

  return issues;
}

function findAliasConflicts(characters: ScriptCharacterCard[]): ConsistencyIssue[] {
  const ownerByAlias = new Map<string, string>();
  const issues: ConsistencyIssue[] = [];
  characters.forEach((character) => {
    [character.name, ...character.aliases].filter(Boolean).forEach((alias) => {
      const normalized = alias.trim();
      const owner = ownerByAlias.get(normalized);
      if (owner && owner !== character.name) {
        issues.push({
          detail: `“${normalized}” 同时指向 ${owner} 与 ${character.name}，可能是同人多名或别名归属错误。`,
          id: `alias-${owner}-${character.name}-${normalized}`,
          scope: "aliases",
          severity: "warn",
          title: "别名存在冲突"
        });
      } else {
        ownerByAlias.set(normalized, character.name);
      }
    });
  });
  return issues;
}

function findFirstAppearanceIssues(characters: ScriptCharacterCard[], scenes: ScriptTraceScene[]): ConsistencyIssue[] {
  return characters.flatMap((character) => {
    const declaredChapterIndex = chapterIndexFromId(character.firstAppearanceChapterId);
    if (!declaredChapterIndex) {
      return [];
    }
    const firstScene = scenes.find((scene) => sceneMentionsCharacter(scene, character));
    if (!firstScene || !firstScene.chapterIndex || firstScene.chapterIndex >= declaredChapterIndex) {
      return [];
    }
    return [{
      detail: `${character.name} 声明首次出场为 ${chapterIdLabel(character.firstAppearanceChapterId)}，但已在第 ${firstScene.chapterIndex} 章场景 ${firstScene.id} 中出现。`,
      id: `first-appearance-${character.id || character.name}`,
      scope: character.name,
      severity: "warn" as const,
      title: "首次出场顺序异常"
    }];
  });
}

function findSceneCharacterIssues(scene: ScriptTraceScene, characters: ScriptCharacterCard[]): ConsistencyIssue[] {
  if (characters.length === 0 || scene.characters.length === 0) {
    return [];
  }
  return scene.characters
    .filter((characterRef) => !resolveCharacterRef(characterRef, characters))
    .map((characterRef) => ({
      detail: `scene.characters 包含 “${characterRef}”，但 characters 中没有匹配的 id、name 或 aliases。`,
      id: `${scene.id}-unknown-character-${characterRef}`,
      scope: scene.id,
      severity: "fail" as const,
      title: "场景角色引用未知"
    }));
}

function findSpeakerIssues(scene: ScriptTraceScene, characters: ScriptCharacterCard[]): ConsistencyIssue[] {
  if (characters.length === 0) {
    return [];
  }
  return scene.dialogues.flatMap<ConsistencyIssue>((dialogue, index) => {
    const speaker = dialogue.speaker.trim();
    if (!speaker || speaker === "旁白") {
      return [];
    }
    const character = resolveCharacterRef(speaker, characters);
    if (!character) {
      return [{
        detail: `第 ${index + 1} 句对白 speaker 为 “${speaker}”，但角色卡中没有匹配人物。`,
        id: `${scene.id}-unknown-speaker-${index}`,
        scope: scene.id,
        severity: "fail" as const,
        title: "对白说话人未知"
      }];
    }
    if (scene.characters.length > 0 && !scene.characters.some((value) => valueMatchesCharacter(value, character))) {
      return [{
        detail: `“${speaker}” 有对白，但未出现在该场景的 scene.characters 中。`,
        id: `${scene.id}-speaker-not-in-scene-${index}`,
        scope: scene.id,
        severity: "warn" as const,
        title: "对白人物未列入场景角色"
      }];
    }
    return [];
  });
}

function findSourceEvidenceIssues(scene: ScriptTraceScene, chapters: ChapterOutline[]): ConsistencyIssue[] {
  const issues: ConsistencyIssue[] = [];
  if (!scene.sourceChapterId || !scene.chapterIndex) {
    issues.push({
      detail: "scene 缺少 source_chapter_id 或 chapter_index，无法回溯原文。",
      id: `${scene.id}-missing-source-chapter`,
      scope: scene.id,
      severity: "fail",
      title: "来源章节缺失"
    });
  }
  if (!scene.sourceExcerpt.trim()) {
    issues.push({
      detail: "scene.source_excerpt 为空，无法判断改编依据。",
      id: `${scene.id}-missing-source-excerpt`,
      scope: scene.id,
      severity: "fail",
      title: "来源摘录缺失"
    });
    return issues;
  }

  const chapter = chapters.find((item) => item.id === scene.sourceChapterId || item.index === scene.chapterIndex);
  if (!chapter) {
    issues.push({
      detail: `当前前端没有第 ${scene.chapterIndex || "-"} 章原文，无法验证 source_excerpt 是否命中。`,
      id: `${scene.id}-source-chapter-unavailable`,
      scope: scene.id,
      severity: "info",
      title: "原文未加载，摘录待回查"
    });
    return issues;
  }
  if (!chapter.content.includes(scene.sourceExcerpt.trim())) {
    issues.push({
      detail: "source_excerpt 未在对应章节正文中精确命中，可能是模型改写了摘录或章节引用错误。",
      id: `${scene.id}-source-excerpt-mismatch`,
      scope: scene.id,
      severity: "warn",
      title: "来源摘录无法精确回查"
    });
  }
  return issues;
}

function resolveCharacterRef(value: string, characters: ScriptCharacterCard[]) {
  return characters.find((character) => valueMatchesCharacter(value, character));
}

function chapterIndexFromId(chapterId: string) {
  const match = chapterId.match(/chapter[_-](\d+)/i);
  return match ? Number(match[1]) : 0;
}

type AiChangeSummaryPanelProps = {
  summary: AiChangeSummary | null;
};

export function AiChangeSummaryPanel({summary}: AiChangeSummaryPanelProps) {
  return (
    <section className="studio-panel ai-change-panel" aria-label="AI 改动解释与差异摘要">
      <div className="panel-heading">
        <div>
          <p className="panel-kicker">AI Change Log</p>
          <h2>改动解释</h2>
        </div>
        <StatusBadge
          label={summary ? summary.validationStatus || "已生成" : "暂无改动"}
          tone={summary?.validationStatus === "valid" ? "success" : summary ? "warning" : "neutral"}
        />
      </div>

      {summary ? (
        <div className="ai-change-body">
          <p>{summary.summary || "AI 已返回 patch，等待作者复核。"}</p>
          <div className="ai-change-scenes">
            {summary.sceneIds.map((sceneId) => (
              <span key={sceneId}>{sceneId}</span>
            ))}
          </div>
          <div className="ai-change-grid">
            {summary.changes.map((change) => (
              <article className="ai-change-card" key={`${change.type}-${change.detail}`}>
                <StatusBadge label={change.type} tone={change.tone} />
                <p>{change.detail}</p>
              </article>
            ))}
          </div>
          <div className="ai-risk-list">
            <span>Risk warnings</span>
            {summary.riskWarnings.length > 0 ? (
              summary.riskWarnings.map((risk) => <p key={risk}>{risk}</p>)
            ) : (
              <p>未返回事实、关系或来源证据风险。</p>
            )}
          </div>
        </div>
      ) : (
        <EmptyState
          description="执行局部精修或对白口吻重写后，这里会显示 patch 摘要、风险和改动类型。"
          icon={<ClipboardCopy size={18} />}
          size="sm"
          title="暂无 AI 改动"
        />
      )}
    </section>
  );
}

type StyleVariantPanelProps = {
  canCreate: boolean;
  creatingStyle: ScriptStyleVariant | "";
  draftCount: number;
  onCreateVariant: (style: ScriptStyleVariant) => void;
};

export function StyleVariantPanel({
  canCreate,
  creatingStyle,
  draftCount,
  onCreateVariant
}: StyleVariantPanelProps) {
  return (
    <section className="studio-panel style-variant-panel" aria-label="多版本风格草稿">
      <div className="panel-heading">
        <div>
          <p className="panel-kicker">Style Revisions</p>
          <h2>风格版本</h2>
        </div>
        <StatusBadge label={draftCount > 0 ? `${draftCount} 个草稿` : "未保存"} tone={draftCount > 0 ? "info" : "neutral"} />
      </div>

      <div className="style-variant-body">
        <p>基于当前结构化底稿另存为新草稿，原稿不会被覆盖。</p>
        <div className="style-variant-grid">
          {scriptStyleVariantOptions.map((option) => {
            const loading = creatingStyle === option.value;
            return (
              <article className="style-variant-card" key={option.value}>
                <span className="style-variant-card__icon" aria-hidden="true">
                  {styleVariantIcon(option.value)}
                </span>
                <div>
                  <strong>{option.label}</strong>
                  <p>{option.detail}</p>
                </div>
                <ActionButton
                  disabled={!canCreate || Boolean(creatingStyle)}
                  icon={loading ? <RefreshCcw aria-hidden="true" className="spin" size={15} /> : <Sparkles aria-hidden="true" size={15} />}
                  onClick={() => onCreateVariant(option.value)}
                  size="sm"
                  variant="secondary"
                >
                  {loading ? "生成中" : "生成"}
                </ActionButton>
              </article>
            );
          })}
        </div>
      </div>
    </section>
  );
}

function styleVariantIcon(value: ScriptStyleVariant) {
  if (value === "screenplay") {
    return <Film size={17} />;
  }
  if (value === "broadcast") {
    return <Radio size={17} />;
  }
  if (value === "stage") {
    return <BookOpen size={17} />;
  }
  return <Sparkles size={17} />;
}

type ScriptPreviewPanelProps = {
  activeDraftId: string;
  canExport: boolean;
  canRefreshDrafts: boolean;
  canSave: boolean;
    taskSummary: TaskSummary;
  draftLoading: boolean;
  drafts: DraftListItem[];
  draftId: string;
  exportLoading: boolean;
  fallbackReason: string;
  generationMode: string;
  generatedTitle: string;
  onCopyYaml: () => void;
  onExportYaml: () => void;
  onLoadDraft: (draftId: string) => void;
  onRefreshDrafts: () => void;
  onSaveDraft: () => void;
  onYamlChange: (value: string) => void;
  projectId: string;
  saveLoading: boolean;
  schemaChecks: ValidationCheck[];
  schemaVersion: string;
  validationError: string;
  validationStatus: string;
  yaml: string;
  yamlMessage: string;
  yamlPlaceholder: string;
};

export function ScriptPreviewPanel({
  activeDraftId,
  canExport,
  canRefreshDrafts,
  canSave,
                                       taskSummary,
  draftLoading,
  drafts,
  draftId,
  exportLoading,
  fallbackReason,
  generationMode,
  generatedTitle,
  onCopyYaml,
  onExportYaml,
  onLoadDraft,
  onRefreshDrafts,
  onSaveDraft,
  onYamlChange,
  projectId,
  saveLoading,
  schemaChecks,
  schemaVersion,
  validationError,
  validationStatus,
  yaml,
  yamlMessage,
  yamlPlaceholder
}: ScriptPreviewPanelProps) {
  return (
    <aside className="studio-panel script-panel" id="yaml-output" aria-labelledby="yaml-output-title">
      <div className="panel-heading">
        <div>
          <p className="panel-kicker">Structured Draft</p>
          <h2 id="yaml-output-title">剧本 YAML</h2>
        </div>
        <div className="panel-actions">
          <ActionButton
            aria-label="保存 YAML 草稿"
            disabled={!canSave}
            icon={<Save aria-hidden="true" className={saveLoading ? "spin" : ""} size={15} />}
            onClick={onSaveDraft}
            size="sm"
            variant="secondary"
          >
            保存
          </ActionButton>
          <ActionButton
            aria-label="导出 YAML 文件"
            disabled={!canExport}
            icon={<FileDown aria-hidden="true" className={exportLoading ? "spin" : ""} size={15} />}
            iconOnly
            onClick={onExportYaml}
            size="sm"
            variant="ghost"
          />
          <ActionButton
            aria-label="刷新草稿列表"
            disabled={!canRefreshDrafts}
            icon={<RefreshCcw aria-hidden="true" className={draftLoading ? "spin" : ""} size={15} />}
            iconOnly
            onClick={onRefreshDrafts}
            size="sm"
            variant="ghost"
          />
          <ActionButton
            aria-label="复制 YAML"
            disabled={!yaml.trim()}
            icon={<ClipboardCopy aria-hidden="true" size={15} />}
            iconOnly
            onClick={onCopyYaml}
            size="sm"
            variant="ghost"
          />
        </div>
      </div>

      <div className="yaml-meta">
        <div className="yaml-meta__badges">
          <StatusBadge label={schemaVersion ? `Schema ${schemaVersion}` : "Schema 1.0"} tone="info" />
          <StatusBadge label={generationModeLabel(generationMode)} tone={generationModeTone(generationMode, fallbackReason)} />
        </div>
        <span>{draftId ? `Draft ${draftId}` : generatedTitle || "待生成"}</span>
      </div>

      {fallbackReason ? (
        <div className="generation-fallback">
          <ShieldCheck aria-hidden="true" size={15} />
          <span>{fallbackReason}</span>
        </div>
      ) : null}

        <section className="result-summary" data-status={taskSummary.status} aria-label="任务结果摘要">
            <div>
                <span>转换任务</span>
                <strong>{taskSummary.currentStage}</strong>
            </div>
            <div>
                <span>进度</span>
                <strong>{taskSummary.progress}%</strong>
            </div>
            <div>
                <span>状态</span>
                <strong>{taskStatusLabel(taskSummary.status)}</strong>
            </div>
            <div>
                <span>AI 用量</span>
                <strong>{usageSummaryLabel(taskSummary.usageSummary)}</strong>
            </div>
        </section>

      <YamlEditor onChange={onYamlChange} placeholder={yamlPlaceholder} value={yaml} />

      <div className="status-line yaml-status" data-valid={Boolean(yaml.trim())}>
        <ScrollText aria-hidden="true" size={16} />
        <span>{yamlMessage}</span>
      </div>

      <SchemaValidationPanel checks={schemaChecks} error={validationError} status={validationStatus} yamlReady={Boolean(yaml.trim())} />

      <section className="draft-history" aria-label="已保存草稿">
        <div className="draft-history__heading">
          <span>
            <History size={15} aria-hidden="true" />
            保存记录
          </span>
          {projectId ? <strong>{drafts.length} 条</strong> : null}
        </div>
        <div className="draft-list">
          {drafts.length > 0 ? (
            drafts.map((draft) => (
              <button
                className="draft-item"
                data-active={draft.draftId === activeDraftId}
                key={draft.draftId}
                onClick={() => onLoadDraft(draft.draftId)}
                type="button"
              >
                <span>{draft.title}</span>
                <small>
                  {draft.schemaVersion} / {draft.validationStatus}
                </small>
              </button>
            ))
          ) : (
            <EmptyState
              description="保存后可在此重新加载草稿版本。"
              icon={<History size={18} />}
              size="sm"
              title="暂无保存记录"
            />
          )}
        </div>
      </section>
    </aside>
  );
}

function generationModeLabel(generationMode: string) {
  if (generationMode === "ai_pipeline") {
    return "AI Pipeline";
  }
  if (generationMode === "rule_based_fallback") {
    return "规则兜底";
  }
  if (generationMode === "rule_based_scaffold") {
    return "规则脚手架";
  }
  return "待生成";
}

function generationModeTone(generationMode: string, fallbackReason: string): StatusTone {
  if (fallbackReason || generationMode === "rule_based_fallback") {
    return "warning";
  }
  if (generationMode === "ai_pipeline") {
    return "success";
  }
  if (generationMode === "rule_based_scaffold") {
    return "neutral";
  }
  return "neutral";
}

type YamlEditorProps = {
  onChange: (value: string) => void;
  placeholder: string;
  value: string;
};

export function YamlEditor({ onChange, placeholder, value }: YamlEditorProps) {
  return (
    <textarea
      aria-label="可编辑剧本 YAML"
      className="yaml-editor"
      onChange={(event) => onChange(event.target.value)}
      placeholder={placeholder}
      spellCheck={false}
      value={value}
    />
  );
}

type ConversionProgressProps = {
  steps: ProgressStep[];
};

export function ConversionProgress({ steps }: ConversionProgressProps) {
  return (
    <section className="sidebar__section conversion-progress" aria-label="转换进度">
      <div className="sidebar__section-title">
        <ShieldCheck size={16} aria-hidden="true" />
        <span>转换状态</span>
      </div>
      <ol>
        {steps.map((step) => (
          <li data-status={step.status} key={step.label}>
            <span className="progress-dot" aria-hidden="true" />
            <div>
              <strong>{step.label}</strong>
              <small>{step.detail}</small>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}

type SchemaValidationPanelProps = {
  checks: ValidationCheck[];
  error: string;
  status: string;
  yamlReady: boolean;
};

export function SchemaValidationPanel({ checks, error, status, yamlReady }: SchemaValidationPanelProps) {
  return (
    <section className="schema-panel" aria-label="Schema 校验结果">
      <div className="schema-panel__heading">
        <span>
          <ListChecks size={15} aria-hidden="true" />
          Schema 校验
        </span>
        <StatusBadge label={yamlReady ? status || "local" : "pending"} tone={validationTone(status, yamlReady)} />
      </div>
      {error ? <p className="schema-panel__error">{error}</p> : null}
      <div className="schema-checks">
        {checks.map((check) => (
          <div className="schema-check" data-state={check.state} key={check.label}>
            <span className="schema-check__mark" aria-hidden="true" />
            <div>
              <strong>{check.label}</strong>
              <small>{check.detail}</small>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function validationTone(status: string, yamlReady: boolean): StatusTone {
  if (!yamlReady) {
    return "neutral";
  }
  if (status === "valid") {
    return "success";
  }
  if (status === "invalid") {
    return "danger";
  }
  if (status === "edited") {
    return "warning";
  }
  return "info";
}

function taskStatusLabel(status: TaskStatus) {
    if (status === "ready") {
        return "可生成";
    }
    if (status === "running") {
        return "运行中";
    }
    if (status === "success") {
        return "已完成";
    }
    if (status === "failed") {
        return "失败";
    }
    if (status === "canceled") {
        return "已停止";
    }
    return "待输入";
}

function taskStatusTone(status: TaskStatus): StatusTone {
    if (status === "running") {
        return "active";
    }
    if (status === "success") {
        return "success";
    }
    if (status === "failed") {
        return "danger";
    }
    if (status === "canceled" || status === "ready") {
        return "warning";
    }
    return "neutral";
}
