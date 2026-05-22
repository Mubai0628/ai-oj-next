<template>
  <section
    ref="containerRef"
    class="split-pane"
    :class="{ 'split-pane--stacked': isStacked, 'split-pane--dragging': dragging }"
    :style="splitStyle"
  >
    <div class="split-pane__panel split-pane__panel--left">
      <slot name="left" />
    </div>

    <ResizeDivider
      v-if="!isStacked"
      :active="dragging"
      :label="dividerLabel"
      @start="startDrag"
      @reset="resetRatio"
    />

    <div class="split-pane__panel split-pane__panel--right">
      <slot name="right" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import ResizeDivider from '@/components/problem/ResizeDivider.vue';

const props = withDefaults(defineProps<{
  defaultRatio?: number;
  minLeft?: number;
  minRight?: number;
  dividerWidth?: number;
  storageKey?: string;
  dividerLabel?: string;
}>(), {
  defaultRatio: 0.58,
  minLeft: 420,
  minRight: 420,
  dividerWidth: 12,
  storageKey: 'ai-oj:problem-workspace-ratio',
  dividerLabel: 'Resize problem and editor panels'
});

const emit = defineEmits<{
  reset: [];
}>();

const containerRef = ref<HTMLElement | null>(null);
const containerWidth = ref(0);
const ratio = ref(props.defaultRatio);
const dragging = ref(false);
let resizeObserver: ResizeObserver | null = null;

const minTotalWidth = computed(() => props.minLeft + props.minRight + props.dividerWidth);
const isStacked = computed(() => containerWidth.value > 0 && containerWidth.value < minTotalWidth.value);
const availableWidth = computed(() => Math.max(0, containerWidth.value - props.dividerWidth));

const leftWidth = computed(() => {
  if (isStacked.value || availableWidth.value <= 0) return 0;
  return clamp(ratio.value * availableWidth.value, props.minLeft, availableWidth.value - props.minRight);
});

const splitStyle = computed(() => ({
  '--split-left-width': `${Math.round(leftWidth.value)}px`,
  '--split-divider-width': `${props.dividerWidth}px`
}));

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function loadStoredRatio() {
  const raw = window.localStorage.getItem(props.storageKey);
  const stored = raw ? Number(raw) : Number.NaN;
  if (Number.isFinite(stored)) {
    ratio.value = clamp(stored, 0.25, 0.75);
  }
}

function persistRatio() {
  window.localStorage.setItem(props.storageKey, String(Number(ratio.value.toFixed(4))));
}

function updateContainerWidth() {
  containerWidth.value = containerRef.value?.getBoundingClientRect().width ?? 0;
}

function applyClientX(clientX: number) {
  const rect = containerRef.value?.getBoundingClientRect();
  if (!rect || availableWidth.value <= 0) return;
  const desiredLeft = clientX - rect.left;
  const nextLeft = clamp(desiredLeft, props.minLeft, availableWidth.value - props.minRight);
  ratio.value = nextLeft / availableWidth.value;
}

function startDrag(event: PointerEvent) {
  if (isStacked.value || event.button !== 0) return;
  event.preventDefault();
  dragging.value = true;
  document.body.classList.add('is-resizing-problem-workspace');
  document.addEventListener('pointermove', onPointerMove);
  document.addEventListener('pointerup', stopDrag);
  document.addEventListener('pointercancel', stopDrag);
}

function onPointerMove(event: PointerEvent) {
  if (!dragging.value) return;
  event.preventDefault();
  applyClientX(event.clientX);
}

function stopDrag() {
  if (!dragging.value) return;
  dragging.value = false;
  document.body.classList.remove('is-resizing-problem-workspace');
  document.removeEventListener('pointermove', onPointerMove);
  document.removeEventListener('pointerup', stopDrag);
  document.removeEventListener('pointercancel', stopDrag);
  persistRatio();
}

function resetRatio() {
  ratio.value = props.defaultRatio;
  persistRatio();
  emit('reset');
}

onMounted(async () => {
  loadStoredRatio();
  await nextTick();
  updateContainerWidth();
  if (containerRef.value) {
    resizeObserver = new ResizeObserver(updateContainerWidth);
    resizeObserver.observe(containerRef.value);
  }
  window.addEventListener('resize', updateContainerWidth);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  window.removeEventListener('resize', updateContainerWidth);
  stopDrag();
});
</script>
