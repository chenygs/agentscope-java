import { onMounted, watch, defineComponent } from 'vue';
import { NConfigProvider, NMessageProvider, NDialogProvider, darkTheme, useMessage } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import { useUiStore } from '@/stores/ui';
import { useAuthStore } from '@/stores/auth';
import { useFactoriesStore } from '@/stores/factories';
import router from '@/router';
const ui = useUiStore();
const auth = useAuthStore();
const factories = useFactoriesStore();
const { locale: i18nLocale } = useI18n();
// 同步 ui store locale 到 vue-i18n
watch(() => ui.locale, (v) => { i18nLocale.value = v; }, { immediate: true });
// 同步 body 背景色与主题
watch(() => ui.isDark, (dark) => {
    document.body.style.backgroundColor = dark ? '#0a0a0a' : '#fafafa';
    document.body.style.colorScheme = dark ? 'dark' : 'light';
}, { immediate: true });
// 登录后自动加载 factories 元数据
watch(() => auth.isLoggedIn, (loggedIn) => { if (loggedIn)
    factories.loadAll(); }, { immediate: true });
// 挂载全局工具到 window（供 axios 拦截器使用）
onMounted(() => {
    window.$authStore = auth;
    window.$router = router;
});
// 内部组件：把 useMessage() 挂到 window.$message
const MessageApiRegister = defineComponent({
    name: 'MessageApiRegister',
    setup() {
        const message = useMessage();
        onMounted(() => { window.$message = message; });
        return () => null;
    },
});
const __VLS_ctx = {
    ...{},
    ...{},
};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NConfigProvider | typeof __VLS_components.NConfigProvider} */
NConfigProvider;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    theme: (__VLS_ctx.ui.isDark ? __VLS_ctx.darkTheme : null),
    themeOverrides: ({}),
}));
const __VLS_2 = __VLS_1({
    theme: (__VLS_ctx.ui.isDark ? __VLS_ctx.darkTheme : null),
    themeOverrides: ({}),
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
var __VLS_5;
const { default: __VLS_6 } = __VLS_3.slots;
let __VLS_7;
/** @ts-ignore @type { | typeof __VLS_components.NMessageProvider | typeof __VLS_components.NMessageProvider} */
NMessageProvider;
// @ts-ignore
const __VLS_8 = __VLS_asFunctionalComponent1(__VLS_7, new __VLS_7({}));
const __VLS_9 = __VLS_8({}, ...__VLS_functionalComponentArgsRest(__VLS_8));
const { default: __VLS_12 } = __VLS_10.slots;
let __VLS_13;
/** @ts-ignore @type { | typeof __VLS_components.NDialogProvider | typeof __VLS_components.NDialogProvider} */
NDialogProvider;
// @ts-ignore
const __VLS_14 = __VLS_asFunctionalComponent1(__VLS_13, new __VLS_13({}));
const __VLS_15 = __VLS_14({}, ...__VLS_functionalComponentArgsRest(__VLS_14));
const { default: __VLS_18 } = __VLS_16.slots;
let __VLS_19;
/** @ts-ignore @type { | typeof __VLS_components.MessageApiRegister} */
MessageApiRegister;
// @ts-ignore
const __VLS_20 = __VLS_asFunctionalComponent1(__VLS_19, new __VLS_19({}));
const __VLS_21 = __VLS_20({}, ...__VLS_functionalComponentArgsRest(__VLS_20));
let __VLS_24;
/** @ts-ignore @type { | typeof __VLS_components.RouterView} */
RouterView;
// @ts-ignore
const __VLS_25 = __VLS_asFunctionalComponent1(__VLS_24, new __VLS_24({}));
const __VLS_26 = __VLS_25({}, ...__VLS_functionalComponentArgsRest(__VLS_25));
// @ts-ignore
[ui, darkTheme,];
var __VLS_16;
// @ts-ignore
[];
var __VLS_10;
// @ts-ignore
[];
var __VLS_3;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
