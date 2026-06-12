import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { NCard, NForm, NFormItem, NInput, NButton, NH1, NSpace } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import { useAuthStore } from '@/stores/auth';
import { useUiStore } from '@/stores/ui';
const { t } = useI18n();
const router = useRouter();
const route = useRoute();
const auth = useAuthStore();
const ui = useUiStore();
const username = ref('');
const password = ref('');
const loading = ref(false);
const showPassword = ref(false);
async function handleLogin() {
    if (!username.value || !password.value)
        return;
    loading.value = true;
    try {
        await auth.login(username.value, password.value);
        const redirect = route.query.redirect || '/dashboard';
        router.push(redirect);
    }
    catch {
        // 拦截器已经处理了错误提示
    }
    finally {
        loading.value = false;
    }
}
const __VLS_ctx = {
    ...{},
    ...{},
};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
(__VLS_ctx.ui.isDark ? '#0a0a0a' : '#fafafa');
// @ts-ignore
[ui,];
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "login-container" },
});
/** @type {__VLS_StyleScopedClasses['login-container']} */ ;
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
NCard;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    ...{ style: {} },
}));
const __VLS_2 = __VLS_1({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
const { default: __VLS_5 } = __VLS_3.slots;
let __VLS_6;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    vertical: true,
    size: (24),
    align: "center",
}));
const __VLS_8 = __VLS_7({
    vertical: true,
    size: (24),
    align: "center",
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
const { default: __VLS_11 } = __VLS_9.slots;
let __VLS_12;
/** @ts-ignore @type { | typeof __VLS_components.NH1 | typeof __VLS_components.NH1} */
NH1;
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent1(__VLS_12, new __VLS_12({
    ...{ style: {} },
}));
const __VLS_14 = __VLS_13({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_13));
const { default: __VLS_17 } = __VLS_15.slots;
(__VLS_ctx.t('auth.title'));
// @ts-ignore
[t,];
var __VLS_15;
let __VLS_18;
/** @ts-ignore @type { | typeof __VLS_components.NForm | typeof __VLS_components.NForm} */
NForm;
// @ts-ignore
const __VLS_19 = __VLS_asFunctionalComponent1(__VLS_18, new __VLS_18({
    ...{ 'onSubmit': {} },
    ...{ style: {} },
}));
const __VLS_20 = __VLS_19({
    ...{ 'onSubmit': {} },
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_19));
let __VLS_23;
const __VLS_24 = {
    ...{ submit: {} },
    onSubmit: (__VLS_ctx.handleLogin),
};
const { default: __VLS_25 } = __VLS_21.slots;
let __VLS_26;
/** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
NFormItem;
// @ts-ignore
const __VLS_27 = __VLS_asFunctionalComponent1(__VLS_26, new __VLS_26({
    label: (__VLS_ctx.t('auth.username')),
}));
const __VLS_28 = __VLS_27({
    label: (__VLS_ctx.t('auth.username')),
}, ...__VLS_functionalComponentArgsRest(__VLS_27));
const { default: __VLS_31 } = __VLS_29.slots;
let __VLS_32;
/** @ts-ignore @type { | typeof __VLS_components.NInput} */
NInput;
// @ts-ignore
const __VLS_33 = __VLS_asFunctionalComponent1(__VLS_32, new __VLS_32({
    ...{ 'onKeyup': {} },
    value: (__VLS_ctx.username),
    placeholder: (__VLS_ctx.t('auth.username')),
}));
const __VLS_34 = __VLS_33({
    ...{ 'onKeyup': {} },
    value: (__VLS_ctx.username),
    placeholder: (__VLS_ctx.t('auth.username')),
}, ...__VLS_functionalComponentArgsRest(__VLS_33));
let __VLS_37;
const __VLS_38 = {
    ...{ keyup: {} },
    onKeyup: (__VLS_ctx.handleLogin),
};
var __VLS_35;
var __VLS_36;
// @ts-ignore
[t, t, handleLogin, handleLogin, username,];
var __VLS_29;
let __VLS_39;
/** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
NFormItem;
// @ts-ignore
const __VLS_40 = __VLS_asFunctionalComponent1(__VLS_39, new __VLS_39({
    label: (__VLS_ctx.t('auth.password')),
}));
const __VLS_41 = __VLS_40({
    label: (__VLS_ctx.t('auth.password')),
}, ...__VLS_functionalComponentArgsRest(__VLS_40));
const { default: __VLS_44 } = __VLS_42.slots;
let __VLS_45;
/** @ts-ignore @type { | typeof __VLS_components.NInput} */
NInput;
// @ts-ignore
const __VLS_46 = __VLS_asFunctionalComponent1(__VLS_45, new __VLS_45({
    ...{ 'onKeyup': {} },
    value: (__VLS_ctx.password),
    type: (__VLS_ctx.showPassword ? 'text' : 'password'),
    showPasswordOn: "click",
    placeholder: (__VLS_ctx.t('auth.password')),
}));
const __VLS_47 = __VLS_46({
    ...{ 'onKeyup': {} },
    value: (__VLS_ctx.password),
    type: (__VLS_ctx.showPassword ? 'text' : 'password'),
    showPasswordOn: "click",
    placeholder: (__VLS_ctx.t('auth.password')),
}, ...__VLS_functionalComponentArgsRest(__VLS_46));
let __VLS_50;
const __VLS_51 = {
    ...{ keyup: {} },
    onKeyup: (__VLS_ctx.handleLogin),
};
var __VLS_48;
var __VLS_49;
// @ts-ignore
[t, t, handleLogin, password, showPassword,];
var __VLS_42;
let __VLS_52;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_53 = __VLS_asFunctionalComponent1(__VLS_52, new __VLS_52({
    ...{ 'onClick': {} },
    type: "primary",
    block: true,
    loading: (__VLS_ctx.loading),
    ...{ style: {} },
}));
const __VLS_54 = __VLS_53({
    ...{ 'onClick': {} },
    type: "primary",
    block: true,
    loading: (__VLS_ctx.loading),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_53));
let __VLS_57;
const __VLS_58 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.handleLogin),
};
const { default: __VLS_59 } = __VLS_55.slots;
(__VLS_ctx.loading ? __VLS_ctx.t('auth.loggingIn') : __VLS_ctx.t('auth.login'));
// @ts-ignore
[t, t, handleLogin, loading, loading,];
var __VLS_55;
var __VLS_56;
// @ts-ignore
[];
var __VLS_21;
var __VLS_22;
// @ts-ignore
[];
var __VLS_9;
// @ts-ignore
[];
var __VLS_3;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
