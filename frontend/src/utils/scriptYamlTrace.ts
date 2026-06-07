export type ScriptTraceDialogue = {
  emotion: string;
  line: string;
  sourceExcerpt: string;
  speaker: string;
};

export type ScriptCharacterCard = {
  aliases: string[];
  description: string;
  firstAppearanceChapterId: string;
  goal: string;
  id: string;
  motivation: string;
  name: string;
  role: string;
  sourceChapterIds: string[];
};

export type ScriptTraceScene = {
  actId: string;
  action: string;
  aiExpansion?: boolean;
  chapterIndex: number;
  characters: string[];
  dialogues: ScriptTraceDialogue[];
  id: string;
  location: string;
  scenePurpose: string;
  sourceChapterId: string;
  sourceExcerpt: string;
  time: string;
  title: string;
};

const sceneStartPattern = /^\s{2}-\s+id:\s*(.+)\s*$/;
const topLevelKeyPattern = /^[a-zA-Z_][\w-]*:\s*(?:.*)?$/;

export function parseScriptYamlCharacters(yaml: string): ScriptCharacterCard[] {
  return collectTopLevelListBlocks(yaml, "characters")
    .map(parseCharacterBlock)
    .filter((character): character is ScriptCharacterCard => Boolean(character));
}

export function parseScriptYamlScenes(yaml: string): ScriptTraceScene[] {
  const lines = yaml.replace(/\r\n?/g, "\n").split("\n");
  const sceneBlocks = collectSceneBlocks(lines);
  return sceneBlocks.map(parseSceneBlock).filter((scene): scene is ScriptTraceScene => Boolean(scene));
}

function collectTopLevelListBlocks(yaml: string, sectionName: string) {
  const lines = yaml.replace(/\r\n?/g, "\n").split("\n");
  const blocks: string[][] = [];
  const sectionPattern = new RegExp(`^${sectionName}:\\s*$`);
  const itemPattern = /^ {2}-\s+[a-zA-Z_][\w-]*:\s*(.*)$/;
  let inSection = false;
  let current: string[] = [];

  for (const line of lines) {
    if (!inSection) {
      if (sectionPattern.test(line)) {
        inSection = true;
      }
      continue;
    }

    if (topLevelKeyPattern.test(line)) {
      break;
    }

    if (itemPattern.test(line)) {
      if (current.length > 0) {
        blocks.push(current);
      }
      current = [line];
      continue;
    }

    if (current.length > 0) {
      current.push(line);
    }
  }

  if (current.length > 0) {
    blocks.push(current);
  }

  return blocks;
}

function parseCharacterBlock(block: string[]): ScriptCharacterCard | null {
  const parsed = parseYamlItemBlock(block);
  const id = parsed.fields.get("id") || "";
  const name = parsed.fields.get("name") || id;
  if (!id && !name) {
    return null;
  }

  return {
    aliases: getListField(parsed.lists, "aliases"),
    description: parsed.fields.get("description") || "",
    firstAppearanceChapterId:
      parsed.fields.get("first_appearance_chapter_id") || parsed.fields.get("firstAppearanceChapterId") || "",
    goal: parsed.fields.get("goal") || parsed.fields.get("objective") || "",
    id,
    motivation: parsed.fields.get("motivation") || "",
    name,
    role: parsed.fields.get("role") || "待补充",
    sourceChapterIds: getListField(parsed.lists, "source_chapter_ids", "sourceChapterIds")
  };
}

function parseYamlItemBlock(block: string[]) {
  const fields = new Map<string, string>();
  const lists = new Map<string, string[]>();
  const first = block[0]?.match(/^ {2}-\s+([a-zA-Z_][\w-]*):\s*(.*)$/);
  if (first) {
    fields.set(first[1], cleanScalar(first[2] || ""));
  }

  for (let index = 1; index < block.length; index += 1) {
    const line = block[index];
    const field = line.match(/^ {4}([a-zA-Z_][\w-]*):\s*(.*)$/);
    if (!field) {
      continue;
    }

    const key = field[1];
    const rawValue = field[2] || "";
    if (rawValue.startsWith("|")) {
      const collected = collectIndentedBlock(block, index + 1, 6);
      fields.set(key, collected.value);
      index = collected.endIndex - 1;
      continue;
    }

    if (rawValue.trim().startsWith("[") && rawValue.trim().endsWith("]")) {
      lists.set(key, parseInlineList(rawValue));
      continue;
    }

    if (!rawValue.trim()) {
      const collected = collectNestedLines(block, index + 1, 6);
      const blockList = parseBlockList(collected.lines);
      if (blockList.length > 0) {
        lists.set(key, blockList);
        index = collected.endIndex - 1;
        continue;
      }
    }

    fields.set(key, cleanScalar(rawValue));
  }

  return {
    fields,
    lists
  };
}

function collectSceneBlocks(lines: string[]) {
  const blocks: string[][] = [];
  let inScenes = false;
  let current: string[] = [];

  for (const line of lines) {
    if (!inScenes) {
      if (/^scenes:\s*$/.test(line)) {
        inScenes = true;
      }
      continue;
    }

    if (current.length > 0 && topLevelKeyPattern.test(line)) {
      break;
    }

    if (sceneStartPattern.test(line)) {
      if (current.length > 0) {
        blocks.push(current);
      }
      current = [line];
      continue;
    }

    if (current.length > 0) {
      current.push(line);
    }
  }

  if (current.length > 0) {
    blocks.push(current);
  }

  return blocks;
}

function parseSceneBlock(block: string[]): ScriptTraceScene | null {
  const firstLine = block[0] || "";
  const id = cleanScalar(firstLine.match(sceneStartPattern)?.[1] || "");
  if (!id) {
    return null;
  }

  const fields = new Map<string, string>();
  let characters: string[] = [];
  let dialogues: ScriptTraceDialogue[] = [];

  for (let index = 1; index < block.length; index += 1) {
    const line = block[index];
    const field = line.match(/^ {4}([a-zA-Z_][\w-]*):\s*(.*)$/);
    if (!field) {
      continue;
    }

    const key = field[1];
    const rawValue = field[2] || "";

    if (rawValue.startsWith("|")) {
      const collected = collectIndentedBlock(block, index + 1, 6);
      fields.set(key, collected.value);
      index = collected.endIndex - 1;
      continue;
    }

    if (key === "dialogues" && !rawValue.trim().startsWith("[]")) {
      const collected = collectNestedLines(block, index + 1, 6);
      dialogues = parseDialogues(collected.lines);
      index = collected.endIndex - 1;
      continue;
    }

    if (key === "characters") {
      if (rawValue.trim()) {
        characters = parseInlineList(rawValue);
      } else {
        const collected = collectNestedLines(block, index + 1, 6);
        characters = parseBlockList(collected.lines);
        index = collected.endIndex - 1;
      }
      continue;
    }

    fields.set(key, cleanScalar(rawValue));
  }

  return {
    actId: fields.get("act_id") || "",
    action: fields.get("action") || "",
    aiExpansion: parseBoolean(fields.get("ai_expansion")),
    chapterIndex: Number(fields.get("chapter_index")) || 0,
    characters,
    dialogues,
    id,
    location: fields.get("location") || "待补充",
    scenePurpose: fields.get("scene_purpose") || "",
    sourceChapterId: fields.get("source_chapter_id") || "",
    sourceExcerpt: fields.get("source_excerpt") || "",
    time: fields.get("time") || "待补充",
    title: fields.get("title") || id
  };
}

function collectIndentedBlock(lines: string[], startIndex: number, indent: number) {
  const collected: string[] = [];
  let index = startIndex;

  while (index < lines.length) {
    const line = lines[index];
    if (line.trim() && leadingSpaces(line) < indent) {
      break;
    }
    collected.push(line.length >= indent ? line.slice(indent) : "");
    index += 1;
  }

  return {
    endIndex: index,
    value: trimYamlBlock(collected.join("\n"))
  };
}

function collectNestedLines(lines: string[], startIndex: number, indent: number) {
  const collected: string[] = [];
  let index = startIndex;

  while (index < lines.length) {
    const line = lines[index];
    if (line.trim() && leadingSpaces(line) < indent) {
      break;
    }
    collected.push(line);
    index += 1;
  }

  return {
    endIndex: index,
    lines: collected
  };
}

function parseDialogues(lines: string[]): ScriptTraceDialogue[] {
  const dialogues: ScriptTraceDialogue[] = [];
  let current = createEmptyDialogue();

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const item = line.match(/^ {6}-\s+([a-zA-Z_][\w-]*):\s*(.*)$/);
    if (item) {
      if (hasDialogueContent(current)) {
        dialogues.push(current);
      }
      current = createEmptyDialogue();
      assignDialogueField(current, item[1], cleanScalar(item[2] || ""));
      continue;
    }

    const field = line.match(/^ {8}([a-zA-Z_][\w-]*):\s*(.*)$/);
    if (!field) {
      continue;
    }

    const key = field[1];
    const value = field[2] || "";
    if (value.startsWith("|")) {
      const collected = collectIndentedBlock(lines, index + 1, 10);
      assignDialogueField(current, key, collected.value);
      index = collected.endIndex - 1;
      continue;
    }

    assignDialogueField(current, key, cleanScalar(value));
  }

  if (hasDialogueContent(current)) {
    dialogues.push(current);
  }

  return dialogues;
}

function assignDialogueField(dialogue: ScriptTraceDialogue, key: string, value: string) {
  if (key === "speaker") {
    dialogue.speaker = value;
    return;
  }
  if (key === "line") {
    dialogue.line = value;
    return;
  }
  if (key === "emotion") {
    dialogue.emotion = value;
    return;
  }
  if (key === "source_excerpt") {
    dialogue.sourceExcerpt = value;
  }
}

function createEmptyDialogue(): ScriptTraceDialogue {
  return {
    emotion: "",
    line: "",
    sourceExcerpt: "",
    speaker: ""
  };
}

function hasDialogueContent(dialogue: ScriptTraceDialogue) {
  return Boolean(dialogue.speaker || dialogue.line || dialogue.emotion || dialogue.sourceExcerpt);
}

function parseInlineList(value: string) {
  const trimmed = value.trim();
  if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
    return cleanScalar(trimmed) ? [cleanScalar(trimmed)] : [];
  }

  return trimmed
    .slice(1, -1)
    .split(",")
    .map((item) => cleanScalar(item))
    .filter(Boolean);
}

function parseBlockList(lines: string[]) {
  return lines
    .map((line) => cleanScalar(line.match(/^ {6}-\s*(.*)$/)?.[1] || ""))
    .filter(Boolean);
}

function getListField(lists: Map<string, string[]>, ...keys: string[]) {
  for (const key of keys) {
    const value = lists.get(key);
    if (value) {
      return value;
    }
  }
  return [];
}

function parseBoolean(value?: string) {
  if (!value) {
    return undefined;
  }
  if (/^true$/i.test(value)) {
    return true;
  }
  if (/^false$/i.test(value)) {
    return false;
  }
  return undefined;
}

function cleanScalar(value: string) {
  const trimmed = value.trim();
  if (
    (trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
    (trimmed.startsWith("'") && trimmed.endsWith("'"))
  ) {
    return trimmed.slice(1, -1);
  }
  return trimmed;
}

function trimYamlBlock(value: string) {
  return value.replace(/^\n+|\s+$/g, "");
}

function leadingSpaces(value: string) {
  return value.length - value.trimStart().length;
}
