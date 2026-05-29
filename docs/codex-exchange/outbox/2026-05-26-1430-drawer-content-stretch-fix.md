# Task: ProblemEditorDrawer 内容铺满修复（grid 无列声明导致子元素不 stretch）
## Status: done
## Created by: Codex @ 2026-05-26T01:20+08:00
## Linked: inbox/2026-05-26-1430-drawer-content-stretch-fix.md

---

## Report

### Section 1: git status --short

本任务新增/修改文件：

```text
 M apps/web-admin/src/components/ProblemEditorDrawer.vue
 M apps/web-admin/src/styles.css
 M docs/codex-exchange/inbox/2026-05-26-1430-drawer-content-stretch-fix.md
?? docs/codex-exchange/outbox/2026-05-26-1430-drawer-content-stretch-fix.md
```

既有 baseline dirty files 保留，未回滚：

```text
 M CLAUDE.md
 M apps/web-admin/src/components/TestcasePackageUploader.vue
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

### Section 2: Audit summary

| line | selector | columns declared? | verdict | reason |
|---:|---|---|---|---|
| 43 | `.auth-page, .blocked-page` | no | 不修 | 独立认证/阻断页布局，非题目编辑 drawer 内容收缩路径。 |
| 54 | `.auth-panel` | yes | 不修 | 已声明列。 |
| 134 | `.admin-layout` | yes | 不修 | 已声明列。 |
| 163 | `.brand > span` | no | 不修 | 小型居中图标/文字栈，不是内容容器。 |
| 182 | `.nav-list` | no | 不修 | 纵向导航列表，不参与 drawer 内容 stretch。 |
| 288 | `.admin-avatar` | no | 不修 | 固定头像居中容器，无需列声明。 |
| 297 | `.admin-user-copy` | no | 不修 | 小型文字栈，不是宽度铺满问题来源。 |
| 335 | `.sidebar-collapse span` | no | 不修 | 图标/文本居中，非内容面板。 |
| 353 | `.admin-menu-profile div` | no | 不修 | 菜单内部文字栈，不影响 drawer。 |
| 362 | `.view-stack` | no | 不修 | 页面级纵向栈，当前无收缩异常。 |
| 432 | `.stats-grid` | yes | 不修 | 已声明列。 |
| 439 | `.dashboard-grid, .role-grid` | yes | 不修 | 已声明列。 |
| 487 | `.health-list` | no | 不修 | 健康检查列表纵向栈，非 drawer。 |
| 492 | `.ai-drafts-layout` | yes | 不修 | 已声明列。 |
| 537 | `.draft-icon` | no | 不修 | 固定图标盒。 |
| 550 | `.draft-count-stepper` | yes | 不修 | 已声明列。 |
| 630 | `.draft-tip span` | no | 不修 | 小型图标/文本行。 |
| 652 | `.draft-empty-state` | no | 不修 | 空态居中容器。 |
| 666 | `.empty-illustration` | no | 不修 | 图标容器。 |
| 686 | `.draft-flow` | yes | 不修 | 已声明列。 |
| 705 | `.draft-flow span` | no | 不修 | 流程数字图标。 |
| 718 | `.form-grid` | no | 不修 | 旧表单容器，目前不是本次 drawer bug 路径。 |
| 740 | `.case-list` | no | 不修 | 纵向列表。 |
| 762 | `.testcase-uploader` | no -> yes | 修 | 测试包 tab 根容器无列声明，会让 section-title/upload-panel/历史列表只占自然宽度。 |
| 794 | `.problem-editor-header` | no | 不修 | header 横向内容，非主体内容 stretch。 |
| 815 | `.problem-editor` | no -> yes | 修 | drawer 主体根栈显式单列，保证后续内容继承完整宽度。 |
| 827 | `.problem-editor-section` | no -> yes | 修 | 核心根因；无 explicit columns 导致 implicit track 按内容宽度收缩。 |
| 852 | `.basic-info-layout` | yes | 修 | 已有列声明，但侧栏从 `240-320` 收紧为 `220-280`，给主表单更多空间。 |
| 879 | `.basic-form-grid` | yes | 不修 | 已在上一轮改为 `auto-fit/minmax`，本轮不再调整。 |
| 903 | `.basic-preview-list` | no | 不修 | 预览卡内部列表，宽度由父卡控制。 |
| 909 | `.basic-preview-list > div` | no | 不修 | 预览项内部栈。 |
| 976 | `.problem-editor-grid` | no -> yes | 修 | 通用编辑区 grid helper 需要默认单列铺满；`.two` 仍覆盖为双列。 |
| 1005 | `.problem-editor-tip-card` | no | 不修 | 小贴士卡内部居中布局。 |
| 1025 | `.statement-sample-layout` | yes | 不修 | 已声明列，题面与样例 tab 原本正常。 |
| 1112 | `.sample-list` | no | 不修 | 样例纵向列表。 |
| 1139 | `.sample-card-body` | no | 不修 | 样例卡内部内容栈。 |
| 1183 | `.testcase-unsaved-card > span, .testcase-saved-card > span` | no | 不修 | 标签/元信息小栈。 |
| 1200 | `.testcase-steps, .testcase-empty-grid` | yes | 不修 | 已声明列。 |
| 1220 | `.testcase-step-card span` | no | 不修 | 步骤数字图标。 |
| 1237 | `.testcase-upload-placeholder` | no | 不修 | 上传占位内容居中。 |
| 1250 | `.upload-cloud` | no | 不修 | 上传图标盒。 |
| 1312 | `.file-facts` | yes | 不修 | 已声明列。 |
| 1334 | `.upload-status` | no -> yes | 修 | 上传状态块属于测试包区域，补单列避免自然宽度收缩。 |
| 1363 | `.package-summary` | no -> yes | 修 | 包摘要应铺满上传器内容区。 |
| 1374 | `.package-list` | no -> yes | 修 | 历史包列表应铺满上传器内容区。 |
| 1397 | `.package-main, .package-card > div` | no | 不修 | flex 卡片内部文本栈，父列表已修。 |
| 1598 | `.draft-preview-panel, .draft-edit-form, .draft-regenerate-form, .draft-cases-section, .draft-case-card` | no | 不修 | AI 草稿抽屉内部栈，与本次 ProblemEditorDrawer bug 无关。 |
| 1615 | `.draft-edit-grid, .draft-case-grid` | yes | 不修 | 已声明列。 |
| 1665 | `@media (max-width: 760px) .draft-detail-head, .draft-edit-grid, .draft-case-grid` | yes | 不修 | 已声明列。 |
| 1673 | `.auth-overlay` | no | 不修 | 认证页背景/遮罩容器。 |
| 1699 | `.auth-overlay-icon` | no | 不修 | 图标盒。 |

### Section 3: focused diffs

`apps/web-admin/src/components/ProblemEditorDrawer.vue`:

```diff
 const drawerWidth = computed(() => {
   const vw = windowWidth.value;
   if (vw <= 768) return vw;
-  if (vw <= 1280) return Math.round(vw * 0.92);
-  return Math.min(1440, Math.round(vw * 0.78));
+  if (vw <= 1280) return Math.round(vw * 0.94);
+  return Math.round(vw * 0.9);
 });
```

`apps/web-admin/src/styles.css`:

```diff
 .testcase-uploader {
   display: grid;
+  grid-template-columns: minmax(0, 1fr);
   gap: 14px;
   margin-top: 18px;
   padding-top: 18px;
   border-top: 1px solid #e5e7eb;
 }

 .problem-editor {
   display: grid;
+  grid-template-columns: minmax(0, 1fr);
   gap: 18px;
   min-height: calc(100vh - 178px);
   padding: 22px clamp(16px, 2.2vw, 32px) 98px;
 }

 .problem-editor-section {
   display: grid;
+  grid-template-columns: minmax(0, 1fr);
   gap: 16px;
 }

 .basic-info-layout {
   display: grid;
-  grid-template-columns: minmax(0, 1fr) minmax(240px, 320px);
+  grid-template-columns: minmax(0, 1fr) minmax(220px, 280px);
   gap: 18px;
   align-items: start;
 }

 .problem-editor-grid {
   display: grid;
+  grid-template-columns: minmax(0, 1fr);
   gap: 18px;
 }

 .upload-status {
   display: grid;
+  grid-template-columns: minmax(0, 1fr);
   gap: 8px;
   padding: 14px;
   border: 1px solid #dbe7f5;
 }

 .package-summary {
   display: grid;
+  grid-template-columns: minmax(0, 1fr);
   gap: 10px;
 }

 .package-list {
   display: grid;
+  grid-template-columns: minmax(0, 1fr);
   gap: 10px;
 }
```

`docs/codex-exchange/inbox/2026-05-26-1430-drawer-content-stretch-fix.md`:

```diff
 # Task: ProblemEditorDrawer 内容铺满修复（grid 无列声明导致子元素不 stretch）
-## Status: new
+## Status: done
 ## Created by: Claude @ 2026-05-26T14:30+08:00
```

### Section 4: final code blocks

Final `drawerWidth` computed block:

```ts
const drawerWidth = computed(() => {
  const vw = windowWidth.value;
  if (vw <= 768) return vw;
  if (vw <= 1280) return Math.round(vw * 0.94);
  return Math.round(vw * 0.9);
});
```

Final `.problem-editor-section`:

```css
.problem-editor-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
}
```

Final `.testcase-uploader`:

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

Final `.basic-info-layout`:

```css
.basic-info-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 280px);
  gap: 18px;
  align-items: start;
}
```

Additional touched stretch rules:

```css
.problem-editor {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
  min-height: calc(100vh - 178px);
  padding: 22px clamp(16px, 2.2vw, 32px) 98px;
}

.problem-editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
}

.upload-status {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
  padding: 14px;
  border: 1px solid #dbe7f5;
  border-radius: 8px;
  background: #f7fbff;
}

.package-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}

.package-list {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}
```

### Section 5: typecheck last 5 lines

```text
Active code page: 65001

> @aioj/web-admin@0.1.0 typecheck
> vue-tsc --noEmit
```

### Section 6: manual verification checklist

```text
[ ] Open admin → 编辑任意题 → drawer 打开
[ ] 在 1920 屏：drawer 宽度 ≥1700px（90% of 1920）
[ ] 「基础信息」tab：表单卡片 + 右侧预览卡之间没有大块留白；form-grid 实际展示 3-4 列（标题字段独占一行）
[ ] 「题面与样例」tab：编辑器卡片仍铺满（无回归）
[ ] 「测试包」tab：testcase saved card / TestcasePackageUploader 的 section-title / upload-panel / 历史包列表都铺满 drawer 内容区，右侧不再有大块留白
[ ] 缩到 1280 屏：drawer 94% ≈ 1203px，内容仍铺满
[ ] 缩到 600 屏（移动模拟）：drawer 全屏，基础信息单列，预览卡移到下方
[ ] window resize 时 drawer 宽度跟随变化
[ ] 关闭再开 drawer，无报错；F12 console 无 listener leak 警告
```

### Section 7: Chinese summary

本次问题的核心根因是多个 `display: grid` 容器没有显式声明 `grid-template-columns`，在 Grid implicit track 算法下子元素会按自然内容宽度收缩，而不是稳定铺满 drawer 内容区。已给 `.problem-editor-section`、`.testcase-uploader`、`.problem-editor`、`.problem-editor-grid`、上传状态与包列表相关容器补上 `grid-template-columns: minmax(0, 1fr)`，并把基础信息侧栏从 `220-280px` 收紧以释放主表单空间。drawer 宽度策略同步改为移动端全屏、1280px 以下 94%、桌面 90% 且无 1440px 硬上限，更贴合大屏编辑场景。对 `apps/web-admin/src/styles.css` 中所有 `display: grid` 规则做了审计：只有题目编辑 drawer 链路及测试包上传链路需要本轮修复，其余要么已声明列，要么是图标/列表/非 drawer 场景。`npm run typecheck -w @aioj/web-admin` 已通过，未触碰 backend、api-client、i18n、其他组件或依赖。

## Next-action hint

- 建议人工在 1920px、1280px、600px 三档浏览器宽度下验证 drawer 宽度和基础信息/测试包 tab 的真实铺满效果。
