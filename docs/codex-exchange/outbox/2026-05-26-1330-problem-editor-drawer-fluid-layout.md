# Task: ProblemEditorDrawer 三个 tab 铺满抽屉 + 响应式规范化
## Status: done
## Created by: Codex @ 2026-05-26T00:38+08:00
## Linked: inbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md

---

## Report

### Section 1: git status --short

```text
 M CLAUDE.md
 M apps/web-admin/src/components/ProblemEditorDrawer.vue
 M apps/web-admin/src/components/TestcasePackageUploader.vue
 M apps/web-admin/src/styles.css
 M apps/web-admin/src/views/AiDraftsView.vue
 M apps/web-admin/src/views/DashboardView.vue
 M apps/web-admin/src/views/ProblemsView.vue
 M apps/web-admin/src/views/RegisterView.vue
 M apps/web-admin/src/views/RolesView.vue
 M apps/web-admin/src/views/UsersView.vue
 M apps/web-user/src/components/problem/ProblemPane.vue
 M apps/web-user/src/components/problem/ProblemTabs.vue
 M apps/web-user/src/main.ts
 M apps/web-user/src/styles.css
 M apps/web-user/src/types/problem-workspace.ts
 M apps/web-user/src/views/ProblemDetailView.vue
 M apps/web-user/src/views/SubmissionsView.vue
 M docs/HANDOVER.md
 M docs/deployment.md
 M packages/i18n/src/messages.ts
?? apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue
?? apps/web-user/src/components/submission/SubmissionDetailModal.vue
?? docs/codex-exchange/README.md
?? docs/codex-exchange/inbox/.gitkeep
?? docs/codex-exchange/inbox/2026-05-25-1930-problem-detail-history-tab.md
?? docs/codex-exchange/inbox/2026-05-25-2100-submissions-row-layout.md
?? docs/codex-exchange/inbox/2026-05-25-2200-alert-content-slot-normalize.md
?? docs/codex-exchange/inbox/2026-05-25-2200-submission-modal-dedup-copy.md
?? docs/codex-exchange/inbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md
?? docs/codex-exchange/inbox/2026-05-26-1330-testcase-manifest-frontend-builder.md
?? docs/codex-exchange/outbox/.gitkeep
?? docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md
?? docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md
?? docs/codex-exchange/outbox/2026-05-25-2200-alert-content-slot-normalize.md
?? docs/codex-exchange/outbox/2026-05-25-2200-submission-modal-dedup-copy.md
?? docs/codex-exchange/outbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md
```

本任务新增/修改：
- `apps/web-admin/src/components/ProblemEditorDrawer.vue`
- `apps/web-admin/src/styles.css`
- `CLAUDE.md`
- `docs/codex-exchange/inbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md`
- `docs/codex-exchange/outbox/2026-05-26-1330-problem-editor-drawer-fluid-layout.md`

其余条目为进入本任务前已存在的 baseline dirty files，已保留未回滚。

### Section 2: focused diffs

#### ProblemEditorDrawer.vue

```diff
@@
   <a-drawer
     v-model:visible="visibleProxy"
-    :width="'clamp(860px, 62vw, 1080px)'"
+    :width="drawerWidth"
@@
-import { computed, reactive, ref, watch } from 'vue';
+import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
@@
 const loadingDetail = ref(false);
 const fieldErrors = ref<Record<string, string>>({});
+const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1080);
@@
+const drawerWidth = computed(() => {
+  const vw = windowWidth.value;
+  if (vw <= 768) return vw;
+  if (vw <= 1280) return Math.round(vw * 0.92);
+  return Math.min(1440, Math.round(vw * 0.78));
+});
@@
+function syncWindowWidth() {
+  if (typeof window !== 'undefined') {
+    windowWidth.value = window.innerWidth;
+  }
+}
+
+onMounted(() => {
+  syncWindowWidth();
+  window.addEventListener('resize', syncWindowWidth);
+});
+
+onBeforeUnmount(() => {
+  window.removeEventListener('resize', syncWindowWidth);
+});
```

#### apps/web-admin/src/styles.css

```diff
@@
 .problem-editor {
   display: grid;
   gap: 18px;
   min-height: calc(100vh - 178px);
-  padding: 22px 26px 98px;
+  padding: 22px clamp(16px, 2.2vw, 32px) 98px;
 }
@@
 .basic-info-layout {
   display: grid;
-  grid-template-columns: minmax(0, 1fr) minmax(260px, 300px);
+  grid-template-columns: minmax(0, 1fr) minmax(240px, 320px);
   gap: 18px;
   align-items: start;
 }
@@
 .basic-form-grid {
   display: grid;
-  grid-template-columns: repeat(2, minmax(0, 1fr));
+  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
   column-gap: 20px;
   row-gap: 14px;
 }
@@
-  .basic-info-layout,
   .statement-sample-layout,
@@
+@media (max-width: 960px) {
+  .basic-info-layout {
+    grid-template-columns: 1fr;
+  }
+}
+
+@media (max-width: 768px) {
+  .problem-editor-drawer {
+    width: 100vw !important;
+    max-width: 100vw;
+    border-radius: 0;
+  }
+
+  .problem-editor-drawer .arco-drawer-header {
+    min-height: 64px;
+    padding: 14px 16px;
+  }
+
+  .problem-editor {
+    padding: 14px 14px 80px;
+  }
+
+  .basic-form-grid {
+    grid-template-columns: 1fr;
+  }
+
+  .basic-form-card,
+  .basic-preview-card,
+  .statement-editor-card,
+  .sample-section {
+    padding: 12px 14px;
+    box-shadow: 0 4px 12px rgba(15, 23, 42, 0.04);
+  }
+}
```

#### CLAUDE.md

```diff
@@
 - `<a-alert>` 必须用 **default slot** 渲染文本（`<a-alert>{{ message }}</a-alert>`），
   **不要**用 `:content="..."` prop 写自闭合 `<a-alert ... />`——Arco 2.58 + Vue 3.5
   组合下 self-closing + `:content` 会出现「红框无文字」渲染异常。`<a-popconfirm>`
   / `<a-tooltip>` 的 `:content` 不受影响，保持原写法。
+- **响应式必选**：所有新增/调整的样式必须同时考虑桌面（≥1280px）、
+  平板（768–1280px）、移动（<768px）三档断点。规范：
+  - 用 `grid-template-columns: repeat(auto-fit, minmax(<min>, 1fr))`
+    让网格自适应；除非必要不写固定列数
+  - 用 `clamp(min, fluid, max)` 做 width / padding / font-size 弹性
+    值，避免在大屏上留白或小屏溢出
+  - drawer / modal 等浮层在 mobile (<768px) 必须 ≥90% 视口宽
+  - 不要给容器写未带 media query 的 `max-width: <固定px>`，除非已经
+    在所有目标断点下验证过不会留白
```

### Section 3: final drawerWidth block

```ts
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1080);

const drawerWidth = computed(() => {
  const vw = windowWidth.value;
  if (vw <= 768) return vw;
  if (vw <= 1280) return Math.round(vw * 0.92);
  return Math.min(1440, Math.round(vw * 0.78));
});

function syncWindowWidth() {
  if (typeof window !== 'undefined') {
    windowWidth.value = window.innerWidth;
  }
}

onMounted(() => {
  syncWindowWidth();
  window.addEventListener('resize', syncWindowWidth);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncWindowWidth);
});
```

### Section 4: updated style blocks

```css
.problem-editor {
  display: grid;
  gap: 18px;
  min-height: calc(100vh - 178px);
  padding: 22px clamp(16px, 2.2vw, 32px) 98px;
}

.basic-info-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 320px);
  gap: 18px;
  align-items: start;
}

.basic-form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  column-gap: 20px;
  row-gap: 14px;
}

@media (max-width: 960px) {
  .basic-info-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .problem-editor-drawer {
    width: 100vw !important;
    max-width: 100vw;
    border-radius: 0;
  }

  .problem-editor-drawer .arco-drawer-header {
    min-height: 64px;
    padding: 14px 16px;
  }

  .problem-editor {
    padding: 14px 14px 80px;
  }

  .basic-form-grid {
    grid-template-columns: 1fr;
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

题面 + 样例 tab、测试包 tab 无需业务结构调整；核对样式后未发现 `statement-editor-card`、`sample-section` 或 `TestcasePackageUploader` 对 drawer 主区有额外固定 `max-width` 收口。

### Section 5: typecheck last 5 lines

```text
Active code page: 65001

> @aioj/web-admin@0.1.0 typecheck
> vue-tsc --noEmit
```

### Section 6: manual verification checklist

```text
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
```

### Section 7: Chinese summary

本次把 `ProblemEditorDrawer` 的静态 `clamp(860px, 62vw, 1080px)` 宽度改为响应式 `drawerWidth`，并通过 resize listener 实现窗口缩放时实时重算。基础信息 tab 改为主表单 + 预览侧栏的流式 grid，表单字段使用 `auto-fit/minmax` 自适应列数，移动端强制单列。新增的移动端规则让抽屉近全屏、头部更紧凑、编辑区和卡片 padding 更适合窄屏。题面与样例、测试包 tab 没有业务结构调整，核对后确认没有额外固定宽度导致内容区收窄。`CLAUDE.md` 已补响应式三断点约定，后续 UI 改动要同步考虑桌面、平板和移动。

## Next-action hint

- 人工验收时建议重点拖动浏览器宽度观察 drawer 是否跟随变化，因为这是本次新增 resize listener 的核心行为。
