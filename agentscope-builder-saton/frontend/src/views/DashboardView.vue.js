import { useRouter } from 'vue-router';
import { NButton, NSpace, NH1, NP } from 'naive-ui';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();
const router = useRouter();
const __VLS_ctx = {
    ...{},
    ...{},
};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "dashboard-container" },
});
/** @type {__VLS_StyleScopedClasses['dashboard-container']} */ ;
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    vertical: true,
    size: (24),
    align: "center",
    ...{ style: {} },
}));
const __VLS_2 = __VLS_1({
    vertical: true,
    size: (24),
    align: "center",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
const { default: __VLS_5 } = __VLS_3.slots;
let __VLS_6;
/** @ts-ignore @type { | typeof __VLS_components.NH1 | typeof __VLS_components.NH1} */
NH1;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    ...{ style: {} },
}));
const __VLS_8 = __VLS_7({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
const { default: __VLS_11 } = __VLS_9.slots;
(__VLS_ctx.t('dashboard.title'));
// @ts-ignore
[t,];
var __VLS_9;
let __VLS_12;
/** @ts-ignore @type { | typeof __VLS_components.NP | typeof __VLS_components.NP} */
NP;
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent1(__VLS_12, new __VLS_12({
    ...{ style: {} },
}));
const __VLS_14 = __VLS_13({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_13));
const { default: __VLS_17 } = __VLS_15.slots;
(__VLS_ctx.t('dashboard.subtitle'));
// @ts-ignore
[t,];
var __VLS_15;
let __VLS_18;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_19 = __VLS_asFunctionalComponent1(__VLS_18, new __VLS_18({
    ...{ 'onClick': {} },
    type: "primary",
    size: "large",
}));
const __VLS_20 = __VLS_19({
    ...{ 'onClick': {} },
    type: "primary",
    size: "large",
}, ...__VLS_functionalComponentArgsRest(__VLS_19));
let __VLS_23;
const __VLS_24 = {
    ...{ click: {} },
    onClick: (...[$event]) => {
        __VLS_ctx.router.push('/agents/new');
        // @ts-ignore
        [router,];
    },
};
const { default: __VLS_25 } = __VLS_21.slots;
(__VLS_ctx.t('dashboard.cta'));
// @ts-ignore
[t,];
var __VLS_21;
var __VLS_22;
let __VLS_26;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_27 = __VLS_asFunctionalComponent1(__VLS_26, new __VLS_26({
    ...{ 'onClick': {} },
    text: true,
    type: "primary",
}));
const __VLS_28 = __VLS_27({
    ...{ 'onClick': {} },
    text: true,
    type: "primary",
}, ...__VLS_functionalComponentArgsRest(__VLS_27));
let __VLS_31;
const __VLS_32 = {
    ...{ click: {} },
    onClick: (...[$event]) => {
        __VLS_ctx.router.push('/agents');
        // @ts-ignore
        [router,];
    },
};
const { default: __VLS_33 } = __VLS_29.slots;
(__VLS_ctx.t('dashboard.secondary'));
// @ts-ignore
[t,];
var __VLS_29;
var __VLS_30;
// @ts-ignore
[];
var __VLS_3;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
