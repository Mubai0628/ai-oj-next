<template>
  <button
    class="resize-divider"
    :class="{ 'resize-divider--active': active }"
    type="button"
    :aria-label="label"
    @pointerdown="$emit('start', $event)"
    @dblclick="$emit('reset')"
  >
    <span class="resize-divider__handle" />
  </button>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  active?: boolean;
  label?: string;
}>(), {
  active: false,
  label: 'Resize panels'
});

defineEmits<{
  start: [event: PointerEvent];
  reset: [];
}>();
</script>
