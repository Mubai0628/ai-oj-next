# Task: ProblemEditorDrawer 内容铺满修复（grid 无列声明导致子元素不 stretch）
## Status: done
## Created by: Claude @ 2026-05-26T14:30+08:00
## Linked: outbox/2026-05-26-1430-drawer-content-stretch-fix.md

---

## Prompt

ROLE
You are Codex executing a targeted CSS bug fix designed by Claude.
Read this entire file. When done, write the report to
`docs/codex-exchange/outbox/2026-05-26-1430-drawer-content-stretch-fix.md`
per `docs/codex-exchange/README.md`. Update this file's
`## Status: new` → `## Status: done` at the end.

CONTEXT — Claude has already diagnosed the bug
上一轮任务（inbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md）
把 drawer width 改成响应式，并加了响应式断点 — 但用户实测后发现：

- 「题面与样例」tab 内容**正常铺满** drawer
- 「基础信息」+「测试包」tab 内容**只占 drawer 左半**，右侧大块留白

Claude 已经定位到三个根因：

**根因 A（核心）**：`.problem-editor-section` 在 styles.css line ~824
是 `display: grid; gap: 16px;` —— **没有声明 `grid-template-columns`**。
CSS Grid 在无 explicit columns 时不像 flex column 那样让子元素 stretch；
implicit grid track 算法按子元素 intrinsic content 排，导致子元素只占
自然内容宽度。题面 tab 之所以铺满，是因为它额外有 `.problem-editor-stack`
（`display: flex; flex-direction: column;`）覆盖了行为。

**根因 B**：`.testcase-uploader` (styles.css line ~761) 同样
`display: grid` 无 columns 声明，导致测试包子组件内部所有 section
（section-title / upload-panel / package-summary 等）只占自然宽度。

**根因 C**：drawer width 公式 desktop 段 `Math.min(1440, vw*0.78)`，
1920 屏被截到 1440 = 75%，1366 屏占 78%。用户期望"更铺满抽屉区域"
意味着 drawer 本身也要更宽。1440 max 拍脑袋，没必要。

CONSTRAINTS (hard)
- Frontend only.
- 改动文件**仅限**：
  - `apps/web-admin/src/components/ProblemEditorDrawer.vue`（drawerWidth
    公式调整）
  - `apps/web-admin/src/styles.css`（grid 容器 stretch 修复 + 侧栏微调）
- 不动 backend / api-client / i18n / 其他组件 / V*.sql
- 不引入新 npm 依赖
- 不删除已有 ref / function / emit / i18n key 引用
- 不 commit, stage, push

============================================================
TASK 1 — Fix `.problem-editor-section` stretch
============================================================

In `apps/web-admin/src/styles.css`，找到当前规则：

```css
.problem-editor-section {
  display: grid;
  gap: 16px;
}
```

改为（**加一行 grid-template-columns 显式声明单列 stretch**）：

```css
.problem-editor-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
}
```

`minmax(0, 1fr)` 让单列从 0 拉伸到 100%，子元素强制 stretch 满列宽。
这是 Grid 单列的标准 stretch 写法。

============================================================
TASK 2 — Fix `.testcase-uploader` stretch
============================================================

In `apps/web-admin/src/styles.css`，找到：

```css
.testcase-uploader {
  display: grid;
  gap: 14px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #e5e7eb;
}
```

改为：

```css
.testcase-uploader {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #e5e7eb;
}
```

============================================================
TASK 3 — Sweep for similar latent issues
============================================================

在 `apps/web-admin/src/styles.css` 内 grep 所有 `display: grid;` 规则。
对**每一处**检查紧随其后是否声明了 `grid-template-columns`（在同 rule
块内）：

- 如果有 → 跳过
- 如果没有 → 评估该容器是否预期"子元素纵向堆叠并 stretch 满父宽"
  - 若是（典型如 list / stack 类容器）：加 `grid-template-columns: minmax(0, 1fr);`
  - 若否（容器仅用 gap 但子元素自然宽度合适）：跳过，但在报告中
    列出该 selector 并说明保留理由

在报告 "Audit summary" 段列出所有 `display: grid` 出现位置 + 你的
判断（修 / 不修 + 原因）。

============================================================
TASK 4 — Widen drawer (remove arbitrary 1440 cap)
============================================================

In `apps/web-admin/src/components/ProblemEditorDrawer.vue`，修改
`drawerWidth` computed：

当前：

```ts
const drawerWidth = computed(() => {
  const vw = windowWidth.value;
  if (vw <= 768) return vw;
  if (vw <= 1280) return Math.round(vw * 0.92);
  return Math.min(1440, Math.round(vw * 0.78));
});
```

改为：

```ts
const drawerWidth = computed(() => {
  const vw = windowWidth.value;
  if (vw <= 768) return vw;                          // mobile: fullscreen
  if (vw <= 1280) return Math.round(vw * 0.94);      // tablet: 94%
  return Math.round(vw * 0.9);                       // desktop: 90%, no hard cap
});
```

理由：去掉 1440 hard cap 让 4K / 大屏用户能利用全部空间；desktop 段
统一 90% 而不是 78%，保留少量左边背景让用户感知 "在 drawer 内" 但
不再有大块留白。

============================================================
TASK 5 — Tighten the basic-info side panel
============================================================

In styles.css，`.basic-info-layout` 当前：

```css
.basic-info-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 320px);
  gap: 18px;
  align-items: start;
}
```

改为：

```css
.basic-info-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 280px);
  gap: 18px;
  align-items: start;
}
```

右侧预览/小贴士面板从 240-320 收到 220-280，给左侧表单更多空间。
保留响应式断点 `@media (max-width: 960px)` 的单列堆栈不动。

============================================================
TASK 6 — typecheck
============================================================

`npm run typecheck -w @aioj/web-admin` MUST exit 0. Show last 5 lines.

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED (new modifications this task):
     M  apps/web-admin/src/components/ProblemEditorDrawer.vue
     M  apps/web-admin/src/styles.css
     M  docs/codex-exchange/inbox/2026-05-26-1430-drawer-content-stretch-fix.md
     ?? docs/codex-exchange/outbox/2026-05-26-1430-drawer-content-stretch-fix.md
   Pre-existing baseline dirty files may remain. NO new file outside
   this list.

2. Audit summary table (Section "Audit summary") for TASK 3 — list
   every `display: grid` in styles.css with verdict 修/不修:

   | line | selector | columns declared? | verdict | reason |
   |---:|---|---|---|---|

3. Focused diffs for the two modified files.

4. Print final `.problem-editor-section`, `.testcase-uploader`,
   `.basic-info-layout` rules, and the final `drawerWidth` computed
   block.

5. typecheck last 5 lines.

6. Manual verification checklist (for human reviewer in browser):
   [ ] Open admin → 编辑任意题 → drawer 打开
   [ ] 在 1920 屏：drawer 宽度 ≥1700px（90% of 1920）
   [ ] 「基础信息」tab：表单卡片 + 右侧预览卡之间没有大块留白；
       form-grid 实际展示 3-4 列（标题字段独占一行）
   [ ] 「题面与样例」tab：编辑器卡片仍铺满（无回归）
   [ ] 「测试包」tab：testcase saved card / TestcasePackageUploader 的
       section-title / upload-panel / 历史包列表都铺满 drawer 内容区，
       右侧不再有大块留白
   [ ] 缩到 1280 屏：drawer 94% ≈ 1203px，内容仍铺满
   [ ] 缩到 600 屏（移动模拟）：drawer 全屏，基础信息单列，预览卡移
       到下方
   [ ] window resize 时 drawer 宽度跟随变化
   [ ] 关闭再开 drawer，无报错；F12 console 无 listener leak 警告

7. 5-sentence Chinese summary：覆盖核心根因（grid 无 columns 不 stretch）、
   修复点、drawer 宽度新策略、其他 display: grid 容器扫描结论、
   是否触发回归。

OUTPUT
Write into:
  `docs/codex-exchange/outbox/2026-05-26-1430-drawer-content-stretch-fix.md`
Then update inbox top `## Status: new` → `## Status: done`.

DO NOT commit, stage, push.

## Constraints / Files in scope

**Touch (write)**:
- `apps/web-admin/src/components/ProblemEditorDrawer.vue` (drawerWidth only)
- `apps/web-admin/src/styles.css` (selector stretches + side panel + audit)
- This inbox (Status only)
- The outbox (new)

**Read for reference**:
- `node_modules/@arco-design/web-vue/es/drawer/drawer.js` (confirm width
  prop behavior; Claude has verified: number → `${N}px`)
- `apps/web-admin/src/components/ProblemEditorDrawer.vue` template
  (confirm which tab uses which class)
- `apps/web-admin/src/components/TestcasePackageUploader.vue` template

**Hard 禁区**:
- backend/**
- packages/**
- apps/web-user/**
- 任何 .vue 文件除了 `ProblemEditorDrawer.vue`
- 新增 npm 依赖
- CLAUDE.md / HANDOVER.md（本次纯 bug 修，规范上次已写）
