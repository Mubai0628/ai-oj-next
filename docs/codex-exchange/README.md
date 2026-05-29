# Codex Exchange 工作流

> 状态：历史归档。自 2026-05-29 起，新任务默认由 Codex 直接负责
> 设计、开发、验证和汇报；本目录保留用于追溯 2026-05 期间的阶段化任务。

> 这是 Claude（设计师）和 Codex（执行者）之间通过文件系统通讯的约定。
> 用户不再来回复制提示词和执行报告。

## 目录结构

```
docs/codex-exchange/
├── README.md           本文件
├── inbox/              Claude 写 Prompt 给 Codex
│   └── YYYY-MM-DD-HHmm-{slug}.md
└── outbox/             Codex 写报告给 Claude
    └── YYYY-MM-DD-HHmm-{slug}.md   （与 inbox 同名一一对应）
```

## 命名约定

文件名格式：`YYYY-MM-DD-HHmm-{slug}.md`
- 日期时间：任务创建时刻
- slug：3-6 个单词的英文短语，描述任务主题，连字符分隔
- 示例：`2026-05-25-1930-problem-detail-history-tab.md`

inbox 和 outbox **必须用同一个文件名**，方便配对。

## 文件模板

每个 inbox / outbox 文件统一用这个骨架：

```markdown
# Task: {简短题目}
## Status: new | in-progress | done | blocked
## Created by: Claude @ {ISO 时间戳}
## Linked: {inbox/outbox 对侧文件相对路径}

---

## Prompt   （只 inbox 写）
{完整可执行的 Codex prompt，自包含}

## Constraints / Files in scope   （只 inbox 写）
- 禁改区域
- 必读参考文件

---

## Report   （只 outbox 写）
{Codex 执行后的 Section 1..N，按 Prompt 末尾的 OUTPUT FORMAT 落盘}

## Next-action hint   （outbox 可选）
- Codex 留给 Claude 的提示：哪些值得二次设计、有没有发现新风险
```

## 双向流程

1. **Claude 设计**：在 inbox/ 新建文件，Status=new，填 Prompt
2. **用户触发 Codex**：对 Codex 说 "读 docs/codex-exchange/inbox/{文件名}
   执行，把报告写到 docs/codex-exchange/outbox/{同名}.md"
3. **Codex 执行**：按 inbox 里的 Prompt 改代码，把 OUTPUT FORMAT 内容
   写到对应 outbox，把 inbox 文件的 Status 改为 done（或 blocked）
4. **Claude 验收**：读 outbox，决定是否关闭 / 追加二次任务
5. **归档**：任务彻底完成后，Claude 把两个文件 Status 都标为 done，
   保留在原位作为历史，不删除

## 硬约束

- Claude 不直接改代码，只写 inbox 文件
- Codex 不主动起任务，只执行 inbox 里的现成 Prompt
- 文件 Status 字段必须维护，没标 done 的视为待办
- inbox 文件一旦给 Codex 执行就视为冻结，不再修改（要改写新文件）

## Lessons Reference

跨项目通用的工程教训（如"视觉/CSS bug 必须实测"等纪律）维护在
`~/.claude/memory/lessons/`，本项目不重复。设计 inbox 前 Claude 应：

1. 扫 `~/.claude/memory/lessons/INDEX.md`，看任务关键词是否命中
2. 命中即把对应 lesson 的 Counter-rules 转化为 inbox 的 TASK 0
   （调试/证伪步骤）或 CONSTRAINTS 段
3. 任务结束如果发现新的、跨项目可复用的教训，调用 skill
   `/distill-lesson` 沉淀到全局库（不只是写本项目 CLAUDE.md）

详见 `~/.claude/skills/distill-lesson/SKILL.md`。
