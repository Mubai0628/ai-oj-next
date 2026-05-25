import { createApp } from 'vue';
import { createPinia } from 'pinia';
import {
  Alert,
  Button,
  Card,
  Divider,
  Empty,
  Form,
  FormItem,
  Input,
  InputPassword,
  InputSearch,
  Option,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  TableColumn,
  Tag,
  Textarea
} from '@arco-design/web-vue';
import '@arco-design/web-vue/dist/arco.css';
import { i18n, installI18n } from '@aioj/i18n';
import { installErrorReporter, setApiErrorMessageResolver } from '@aioj/api-client';
import App from './App.vue';
import router from './router';
import { useAuthStore } from '@/stores/auth';
import './styles/tokens.css';
import './styles/global.css';
import './styles/layout.css';
import './styles.css';

const app = createApp(App);

Object.entries({
  'a-alert': Alert,
  'a-button': Button,
  'a-card': Card,
  'a-divider': Divider,
  'a-empty': Empty,
  'a-form': Form,
  'a-form-item': FormItem,
  'a-input': Input,
  'a-input-password': InputPassword,
  'a-input-search': InputSearch,
  'a-option': Option,
  'a-select': Select,
  'a-space': Space,
  'a-spin': Spin,
  'a-statistic': Statistic,
  'a-table': Table,
  'a-table-column': TableColumn,
  'a-tag': Tag,
  'a-textarea': Textarea
}).forEach(([name, component]) => {
  app.component(name, component);
});

app.use(createPinia());
installI18n(app);
setApiErrorMessageResolver((code, fallback) => {
  const key = `errors.${code}`;
  const localized = i18n.global.t(key);
  if (localized && localized !== key) return localized;
  const generic = i18n.global.t('errors.unknown');
  return generic !== 'errors.unknown' ? generic : fallback;
});
installErrorReporter({ appName: 'web-user' });
app.use(router);

function handleAuthExpired() {
  const auth = useAuthStore();
  const currentRoute = router.currentRoute.value;
  auth.clearLocal();
  if (currentRoute.name === 'login' || currentRoute.name === 'register') return;
  const redirect = typeof currentRoute.fullPath === 'string' && currentRoute.fullPath
    ? currentRoute.fullPath
    : '/';
  router.replace({ name: 'login', query: { expired: '1', redirect } })
    .catch(() => {
      // AppShell overlay still covers protected content if navigation is aborted.
    });
}

window.addEventListener('aioj:auth-expired', handleAuthExpired);
if (import.meta.hot) {
  import.meta.hot.dispose(() => {
    window.removeEventListener('aioj:auth-expired', handleAuthExpired);
  });
}

app.mount('#app');
