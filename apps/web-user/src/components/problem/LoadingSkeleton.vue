<template>
  <div class="problem-skeleton" aria-hidden="true">
    <div class="problem-skeleton__header">
      <span />
      <strong />
      <i />
    </div>
    <div class="problem-workspace">
      <section class="problem-card problem-skeleton__card">
        <span v-for="item in 7" :key="item" />
      </section>
      <aside class="solve-panel">
        <section class="solve-card problem-skeleton__card">
          <span v-for="item in 5" :key="item" />
        </section>
        <section class="ai-hint-card problem-skeleton__card">
          <span v-for="item in 4" :key="item" />
        </section>
      </aside>
    </div>
  </div>
</template>
