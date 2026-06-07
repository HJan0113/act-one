# 剧本 YAML Schema

## 目标

本 Schema 用于承接小说文本到剧本初稿的转换结果。它优先保证结构清晰、可编辑、可继续生成，方便作者在 AI 生成后继续调整人物、场景、动作和对白。

当前版本：`1.0`

## 顶层结构

```yaml
schema_version: "1.0"
kind: "screenplay_draft"
metadata:
  title: "剧本标题"
  author: "作者"
  logline: "一句话梗概"
source:
  type: "novel"
  chapter_count: 3
  generation_mode: "rule_based_scaffold"
characters: []
acts:
  - id: "act_1"
    title: "第一幕"
    chapter_range:
      start: 1
      end: 1
    dramatic_function: "建立人物、目标与初始冲突"
scenes:
  - id: "scene_001"
    act_id: "act_1"
    chapter_index: 1
    source_chapter_id: "chapter_1"
    title: "第 1 章 开端"
    location: "待补充"
    time: "待补充"
    scene_purpose: "承接原章节情节，形成可继续打磨的场景初稿"
    action: |-
      角色动作和场景描述。
    dialogues: []
    source_excerpt: |-
      对应小说章节摘录。
notes:
  - "人物、地点、对白可在后续精修阶段继续补全。"
```

## 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schema_version` | string | 是 | Schema 版本，便于后续升级和兼容旧草稿。 |
| `kind` | string | 是 | 固定为 `screenplay_draft`，标识内容类型。 |
| `metadata` | object | 是 | 剧本基础信息。 |
| `metadata.title` | string | 是 | 剧本标题。 |
| `metadata.author` | string | 否 | 作者名称，未填写时使用占位值。 |
| `metadata.logline` | string | 否 | 一句话梗概，后续可由 AI 精修。 |
| `source` | object | 是 | 原始文本来源信息。 |
| `source.type` | string | 是 | 当前固定为 `novel`。 |
| `source.chapter_count` | number | 是 | 识别到的小说章节数。 |
| `source.generation_mode` | string | 是 | 标识生成方式，当前规则版为 `rule_based_scaffold`。 |
| `characters` | array | 是 | 人物列表，初稿阶段可为空。 |
| `acts` | array | 是 | 幕结构列表，用于建立剧本整体节奏。 |
| `scenes` | array | 是 | 场景列表，是作者主要编辑区域。 |
| `notes` | array | 否 | 生成说明或待办提醒。 |

## 人物字段

`characters` 初稿阶段允许为空。后续补全时建议使用以下结构：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 人物唯一标识，如 `character_001`。 |
| `name` | string | 是 | 人物姓名或称呼。 |
| `role` | string | 否 | 人物功能，如主角、反派、配角。 |
| `description` | string | 否 | 人物身份、性格或关系说明。 |

## 场景字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 场景唯一标识，如 `scene_001`。 |
| `act_id` | string | 是 | 所属幕，如 `act_1`。 |
| `chapter_index` | number | 是 | 来源章节序号。 |
| `source_chapter_id` | string | 是 | 来源章节 ID，便于回溯小说文本。 |
| `title` | string | 是 | 场景标题，默认继承章节标题。 |
| `location` | string | 是 | 场景地点，未知时使用 `待补充`。 |
| `time` | string | 是 | 场景时间，未知时使用 `待补充`。 |
| `scene_purpose` | string | 是 | 场景在剧情中的作用。 |
| `action` | string | 是 | 动作、环境和叙事描述。 |
| `dialogues` | array | 是 | 对白列表，初稿阶段可为空。 |
| `source_excerpt` | string | 是 | 对应小说文本摘录，便于作者校对。 |

## 对白字段

`dialogues` 初稿阶段允许为空。后续补全时建议使用以下结构：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `speaker` | string | 是 | 说话人，应对应人物名或旁白。 |
| `line` | string | 是 | 对白内容。 |
| `emotion` | string | 否 | 表演情绪或语气提示。 |

## 设计原因

1. **可编辑优先**：YAML 比纯文本更容易定位字段，作者可以直接修改标题、场景、对白和备注。
2. **可追溯**：每个场景保留 `chapter_index` 和 `source_chapter_id`，方便从剧本回查小说章节。
3. **可渐进生成**：`characters` 和 `dialogues` 允许先为空，后续可以由 AI 分批补全人物表和对白。
4. **可版本演进**：`schema_version` 让后续多模块化、校验器和草稿版本系统可以识别不同 Schema。
5. **适合剧本工作流**：`acts` 管整体节奏，`scenes` 管具体可拍内容，符合作者从故事结构到场景细化的编辑路径。
