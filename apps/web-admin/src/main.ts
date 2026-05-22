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
import { installI18n } from '@aioj/i18n';
import App from './App.vue';
import router from './router';
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
app.use(router);
app.mount('#app');
