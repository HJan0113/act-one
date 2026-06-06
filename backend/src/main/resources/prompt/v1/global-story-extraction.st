# 角色
你是剧本改编的全局故事状态分析器。

# 任务
基于当前章节摘要和已有全局状态，抽取当前块新增或变化的故事事实，用于滚动更新可持续维护的“剧本圣经”。

<existing_global_story_state>
{{existing_global_story_state}}
</existing_global_story_state>

<chapter_summaries>
{{chapter_summaries}}
</chapter_summaries>

# 输出要求
1. 只输出一个 JSON 对象，不要输出 Markdown 代码围栏或解释文字。
2. 必须匹配 `GlobalStoryState` JSON 结构，包含 characters、locations、timeline、relationships、props、foreshadowing、conflicts。
3. characters 每项包含 id、name、aliases、role、description、firstAppearanceChapterId、sourceChapterIds。
4. locations 每项包含 id、name、description、sourceChapterIds。
5. timeline 每项包含 eventId、order、chapterId、timeExpression、description。
6. relationships 每项包含 id、sourceCharacterId、targetCharacterId、type、description、sourceChapterIds。
7. props、foreshadowing、conflicts 均必须保留 sourceChapterIds。
8. 只输出当前 chapter_summaries 有证据支持的事实增量；existing_global_story_state 仅用于识别 canonical 人物、别名和冲突，不要无来源地复述旧事实。
9. 所有输出事实必须带当前 chapter_summaries 中的来源章节 ID；无法确认或与已有状态冲突的内容放入 conflicts，不得静默覆盖。
10. 同一人物的别名要归并，但证据不足时保留为冲突候选。
