<template>
  <form class="ai-composer" @submit.prevent="send">
    <div class="ai-composer-box">
      <textarea
        ref="textareaRef"
        class="ai-composer-textarea"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="sending"
        @input="$emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
        @keydown="handleKeydown"
      />
      <div class="ai-composer-footer">
        <span>{{ t('aiAssistant.ruleShort') }}</span>
        <button class="ai-send-button" type="submit" :disabled="sending || !modelValue.trim()">
          {{ sending ? t('ai.thinking') : t('ai.send') }}
        </button>
      </div>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';

const props = defineProps<{
  modelValue: string;
  sending: boolean;
  placeholder: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: string];
  send: [];
}>();

const { t } = useI18n();
const textareaRef = ref<HTMLTextAreaElement | null>(null);

function send() {
  if (!props.sending && props.modelValue.trim()) emit('send');
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    send();
  }
}

defineExpose({
  focus: () => textareaRef.value?.focus()
});
</script>
