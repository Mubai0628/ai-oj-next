# Task: ProblemEditorDrawer 三个 tab 铺满抽屉 + 响应式规范化
## Status: done
## Created by: Claude @ 2026-05-26T13:30+08:00
## Linked: outbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md

---

## Prompt

ROLE
You are Codex executing a layout + convention task designed by Claude.
Read this entire file. When done, write the report to
`docs/codex-exchange/outbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md`
per `docs/codex-exchange/README.md`. Update this file's
`## Status: new` → `## Status: done` at the end.

CONTEXT
管理端 `apps/web-admin/src/components/ProblemEditorDrawer.vue` 的三 tab
布局（基础信息 / 题面与样例 / 测试包）当前问题：

1. drawer 宽度被 `:width="'clamp(860px, 62vw, 1080px)'"` 在大屏（≥1920）
   截断在 1080px，屏占只剩 ~56%，**drawer 边界外**右侧大块页面背景空白
2. 「基础信息」tab 内部用 `1fr + minmax(260px,300px)` 双列布局，右侧
   配置预览 + 小贴士 ~300px 固定，左列表单被挤窄；表单内部又拆 2 列
   grid，单输入框只有 ~200px，看起来松散
3. 题面 + 样例 tab 已是单列 stack（OK），测试包 tab 也基本铺满（OK），
   但三 tab 间的 layout 节奏不统一
4. 没有针对移动端 / 平板的响应式校准。CLAUDE.md §5.8 缺响应式约定，
   下次设计仍可能踩坑

GOALS
- drawer 在大屏（≥1280px）至少占屏 70%，最多 1440px；在中屏自适应；
  在窄屏（<768px）几乎全屏
- 三个 tab 的内容区都「铺满 drawer 主区」，没有固定宽度卡死
- 「基础信息」表单字段在宽屏可两列、在窄屏自动堆栈；预览/小贴士
  侧栏在中屏可保留，在窄屏移到底部并自动堆栈
- 写一条响应式断点约定到 CLAUDE.md §5.8

CONSTRAINTS (hard)
- Frontend only. No backend, no api-client, no i18n keys, no
  migrations.
- 改动文件**仅限**：
  - `apps/web-admin/src/components/ProblemEditorDrawer.vue`（template 和
    可能的 scoped style 调整，但模板里 class 名优先复用现有的；如果
    现有 class 在 styles.css 中定义，编辑 styles.css 而不是新增 scoped
    styles）
  - `apps/web-admin/src/styles.css`（统一 layout / 响应式调整）
  - `CLAUDE.md`（§5.8 加一条规范）
- 不引入新 npm 依赖。
- 不删除任何 i18n key 引用、ref / function / emit。
- 不动其他 tab 内具体业务字段（标题/难度/标签/题面/样例/测试包子组件
  的内部逻辑都保留）。
- 不 commit / stage / push.

============================================================
TASK 1 — Drawer width + outer container
============================================================

In `apps/web-admin/src/components/ProblemEditorDrawer.vue`:

修改 `<a-drawer>` 的 width prop。把当前
  `:width="'clamp(860px, 62vw, 1080px)'"`
改为：
  `:width="drawerWidth"`

在 `<script setup>` 顶部附近添加 computed：

```ts
import { computed } from 'vue'; // (if not already imported)

const drawerWidth = computed(() => {
  if (typeof window === 'undefined') return 1080;
  const vw = window.innerWidth;
  if (vw <= 768) return vw;                       // mobile: fullscreen
  if (vw <= 1280) return Math.round(vw * 0.92);   // tablet
  return Math.min(1440, Math.round(vw * 0.78));   // desktop
});
```

注意：`a-drawer` 的 width prop 在 Arco 2.58 接受 number (px) 或 string。
返回 number 让 Arco 自己处理 px 转换。computed 在 window resize 时不会
自动重算 —— 加一个 `onMounted` + `onBeforeUnmount` 注册 resize 监听器
让 `drawerWidth` 重算（用 ref + handler 模式即可，computed 仍可读
window.innerWidth，但 reactivity 来自 ref triggering）。

实际实现可以更简洁：用一个 `windowWidth = ref(window.innerWidth)`，
窗口 resize 时更新它，drawerWidth 基于 windowWidth 计算。

```ts
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';

const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1080);
const drawerWidth = computed(() => {
  const vw = windowWidth.value;
  if (vw <= 768) return vw;
  if (vw <= 1280) return Math.round(vw * 0.92);
  return Math.min(1440, Math.round(vw * 0.78));
});
function syncWindowWidth() {
  windowWidth.value = window.innerWidth;
}
onMounted(() => window.addEventListener('resize', syncWindowWidth));
onBeforeUnmount(() => window.removeEventListener('resize', syncWindowWidth));
```

============================================================
TASK 2 — 「基础信息」tab 布局重排
============================================================

In `apps/web-admin/src/styles.css`：

(A) `.problem-editor-drawer` 当前 `max-width: calc(100vw - 24px)` 保留。

(B) `.basic-info-layout` 改为响应式 grid：

```css
.basic-info-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 320px);
  gap: 18px;
  align-items: start;
}

@media (max-width: 960px) {
  .basic-info-layout {
    grid-template-columns: 1fr;       /* 单列堆栈 */
  }
}
```

(C) `.basic-form-grid` 在更宽屏幕拓展到 2-3 列（但要让 wide field 仍
跨行）：

```css
.basic-form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  column-gap: 20px;
  row-gap: 14px;
}
```

`auto-fit + minmax(220px, 1fr)` 让 grid 自动按可用宽度填列，drawer
变宽时列数自动增加，无需再写 media query。`.basic-form-field--wide`
仍 `grid-column: 1 / -1` 跨满（这条保留）。

(D) `.problem-editor` padding 调整为响应式 padding：

```css
.problem-editor {
  display: grid;
  gap: 18px;
  min-height: calc(100vh - 178px);
  padding: 22px clamp(16px, 2.2vw, 32px) 98px;
}
```

(E) 检查并删除任何在 `.problem-editor-section` / `.basic-form-card`
等容器上的 `max-width` 限制（如果存在）。最终目的是：所有 tab 内的
内容区都能 stretch 到 drawer 可用宽度。

(F) `@media (max-width: 768px)`（移动端）：

```css
@media (max-width: 768px) {
  .problem-editor {
    padding: 14px 14px 80px;
  }
  .problem-editor-drawer .arco-drawer-header {
    padding: 14px 16px;
    min-height: 64px;
  }
  .basic-form-card,
  .basic-preview-card,
  .statement-editor-card,
  .sample-section {
    padding: 12px 14px;
    box-shadow: 0 4px 12px rgba(15, 23, 42, 0.04);
  }
}
```

如果 styles.css 已经有 `@media (max-width: 768px)` 区块，把新规则
**合并进去**，不要重复声明该 media 块。

============================================================
TASK 3 — 「题面与样例」+「测试包」tab 一致性检查
============================================================

读 `.problem-editor-stack`、`.statement-editor-card`、`.sample-section`、
测试包子组件相关 styles，确认：

- 所有 tab 内容区都受 drawer width 控制，不再有更窄的硬上限
- 卡片 padding、间距在新响应式下舒适（不需要修改具体内容布局，只要
  容器不收口）
- 如果发现 `.statement-editor-card` 等卡片有 `max-width`，删掉
- 测试包 tab 的子组件 `TestcasePackageUploader` 中的 `<section>` 也
  受同样宽度，无需改动 uploader 内部布局

如果什么都不需要改，在报告里明确说明 "题面 + 测试包 tab 无需调整"。

============================================================
TASK 4 — CLAUDE.md §5.8 响应式约定
============================================================

In `CLAUDE.md` section §5.8 「前端约定」，在现有 alert-slot 约束（上次
任务加的那条）之后 append 一条新规则：

```
- **响应式必选**：所有新增/调整的样式必须同时考虑桌面（≥1280px）、
  平板（768–1280px）、移动（<768px）三档断点。规范：
  - 用 `grid-template-columns: repeat(auto-fit, minmax(<min>, 1fr))`
    让网格自适应；除非必要不写固定列数
  - 用 `clamp(min, fluid, max)` 做 width / padding / font-size 弹性
    值，避免在大屏上留白或小屏溢出
  - drawer / modal 等浮层在 mobile (<768px) 必须 ≥90% 视口宽
  - 不要给容器写未带 media query 的 `max-width: <固定px>`，除非已经
    在所有目标断点下验证过不会留白
```

============================================================
TASK 5 — typecheck
============================================================

Run `npm run typecheck -w @aioj/web-admin`. Must exit 0. Show last
5 lines.

============================================================
VERIFICATION
============================================================

1. `git status --short` — expected new modifications:
     M  apps/web-admin/src/components/ProblemEditorDrawer.vue
     M  apps/web-admin/src/styles.css
     M  CLAUDE.md
     M  docs/codex-exchange/inbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md
     ?? docs/codex-exchange/outbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md
   Pre-existing baseline dirty files may remain — call them out but
   do not revert. NO new file outside above list.

2. Focused diffs for each touched file.

3. Print the final `drawerWidth` computed + resize hook block from
   ProblemEditorDrawer.vue (~15 lines).

4. Print the full updated `.basic-info-layout`, `.basic-form-grid`,
   `.problem-editor` style blocks + every `@media` block that was
   touched in styles.css.

5. typecheck last 5 lines.

6. Manual verification checklist (for the human reviewer to run later
   in a browser; Codex cannot do this part):
   [ ] Open admin → 题库 → 点任意题 → drawer 打开
   [ ] 在 1920×1080 屏：drawer 宽度 ≥1500px，主表单宽度
       明显比之前宽，没有右侧大块留白
   [ ] 「基础信息」表单字段在宽屏自动分 3-4 列，标题字段仍跨满；
       缩窄到 ~900px 浏览器宽时减为 2 列；<768px 移动模拟时单列堆栈
   [ ] 「配置预览/小贴士」在移动端移到下方而不是右侧
   [ ] 「题面与样例」tab 编辑器铺满 drawer
   [ ] 「测试包」tab 一切如旧（无回归）
   [ ] window resize → drawer width 跟随调整（不需要刷新页面）
   [ ] 关闭再开 drawer，状态正常（onMounted/onBeforeUnmount 未泄漏
       listener 应该 ok）

7. 5-sentence Chinese summary.

OUTPUT
Write into:
  `docs/codex-exchange/outbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md`
Then update inbox `## Status: new` → `## Status: done`.

DO NOT commit, stage, push.

## Constraints / Files in scope

**Touch (write)**:
- `apps/web-admin/src/components/ProblemEditorDrawer.vue`
- `apps/web-admin/src/styles.css`
- `CLAUDE.md`
- This inbox (Status only)
- The outbox (new)

**Read for reference**:
- `apps/web-admin/src/styles.css` 现有 `@media` 区块（line ~1420+）

**Hard 禁区**:
- backend/**
- packages/**
- apps/web-user/**
- 新增 npm 依赖
- 删除现有 ref / function / emit / i18n key 引用
