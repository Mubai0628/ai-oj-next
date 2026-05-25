import { createApp } from 'vue';
import { createPinia } from 'pinia';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Divider,
  Drawer,
  Dropdown,
  Doption,
  Empty,
  Form,
  FormItem,
  Input,
  InputNumber,
  InputPassword,
  InputSearch,
  Modal,
  Option,
  Popconfirm,
  Radio,
  RadioGroup,
  Select,
  Space,
  Spin,
  Switch,
  TabPane,
  Table,
  TableColumn,
  Tabs,
  Tag,
  Textarea,
  Tooltip
} from '@arco-design/web-vue';
import '@arco-design/web-vue/dist/arco.css';
import { i18n, installI18n } from '@aioj/i18n';
import { installErrorReporter, setApiErrorMessageResolver } from '@aioj/api-client';
import App from './App.vue';
import router from './router';
import { useAuthStore } from '@/stores/auth';
import './styles.css';

const app = createApp(App);

Object.entries({
  'a-alert': Alert,
  'a-button': Button,
  'a-card': Card,
  'a-checkbox': Checkbox,
  'a-divider': Divider,
  'a-drawer': Drawer,
  'a-dropdown': Dropdown,
  'a-doption': Doption,
  'a-empty': Empty,
  'a-form': Form,
  'a-form-item': FormItem,
  'a-input': Input,
  'a-input-number': InputNumber,
  'a-input-password': InputPassword,
  'a-input-search': InputSearch,
  'a-modal': Modal,
  'a-option': Option,
  'a-popconfirm': Popconfirm,
  'a-radio': Radio,
  'a-radio-group': RadioGroup,
  'a-select': Select,
  'a-space': Space,
  'a-spin': Spin,
  'a-switch': Switch,
  'a-tab-pane': TabPane,
  'a-table': Table,
  'a-table-column': TableColumn,
  'a-tabs': Tabs,
  'a-tag': Tag,
  'a-textarea': Textarea,
  'a-tooltip': Tooltip
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
installErrorReporter({ appName: 'web-admin' });
app.use(router);

function handleAuthExpired() {
  const auth = useAuthStore();
  const currentRoute = router.currentRoute.value;
  auth.clearLocal();
  if (currentRoute.name === 'login' || currentRoute.name === 'register') return;
  const redirect = typeof currentRoute.fullPath === 'string' && currentRoute.fullPath
    ? currentRoute.fullPath
    : '/dashboard';
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
