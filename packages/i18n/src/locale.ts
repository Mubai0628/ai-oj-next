import type { App } from 'vue';
import { computed } from 'vue';
import { createI18n, useI18n } from 'vue-i18n';
import { messages, type Locale } from './messages';

export const LOCALE_STORAGE_KEY = 'aioj-locale';
export const DEFAULT_LOCALE: Locale = 'zh-CN';

export const localeOptions: Array<{ value: Locale; label: string }> = [
  { value: 'zh-CN', label: '中文' },
  { value: 'en-US', label: 'English' }
];

function isLocale(value: string | null): value is Locale {
  return value === 'zh-CN' || value === 'en-US';
}

function storedLocale(): Locale {
  if (typeof localStorage === 'undefined') return DEFAULT_LOCALE;
  const value = localStorage.getItem(LOCALE_STORAGE_KEY);
  return isLocale(value) ? value : DEFAULT_LOCALE;
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: storedLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  messages
});

export function setLocale(locale: Locale) {
  i18n.global.locale.value = locale;
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  }
  if (typeof document !== 'undefined') {
    document.documentElement.lang = locale;
  }
}

export function installI18n(app: App) {
  setLocale(storedLocale());
  app.use(i18n);
}

export function useLocaleSwitcher() {
  const composer = useI18n({ useScope: 'global' });
  const currentLocale = computed<Locale>({
    get: () => composer.locale.value as Locale,
    set: (value) => setLocale(value)
  });

  return {
    currentLocale,
    localeOptions
  };
}
