import LanguageSwitcher from './LanguageSwitcher.vue';

export { LanguageSwitcher };
export { resolveApiErrorMessage } from './apiErrorResolver';
export { messages } from './messages';
export type { Locale } from './messages';
export { DEFAULT_LOCALE, LOCALE_STORAGE_KEY, i18n, installI18n, localeOptions, setLocale, useLocaleSwitcher } from './locale';
