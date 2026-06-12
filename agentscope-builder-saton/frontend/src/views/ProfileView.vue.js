import { NCard, NH2, NText, NSpace } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import { useAuthStore } from '@/stores/auth';
const { t } = useI18n();
const auth = useAuthStore();
const __VLS_ctx = {
    ...{},
    ...{},
};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NH2 | typeof __VLS_components.NH2} */
NH2;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({}));
const __VLS_2 = __VLS_1({}, ...__VLS_functionalComponentArgsRest(__VLS_1));
const { default: __VLS_5 } = __VLS_3.slots;
(__VLS_ctx.t('profile.title'));
// @ts-ignore
[t,];
var __VLS_3;
let __VLS_6;
/** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
NCard;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    ...{ style: {} },
}));
const __VLS_8 = __VLS_7({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
const { default: __VLS_11 } = __VLS_9.slots;
let __VLS_12;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent1(__VLS_12, new __VLS_12({
    vertical: true,
    size: (12),
}));
const __VLS_14 = __VLS_13({
    vertical: true,
    size: (12),
}, ...__VLS_functionalComponentArgsRest(__VLS_13));
const { default: __VLS_17 } = __VLS_15.slots;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
let __VLS_18;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_19 = __VLS_asFunctionalComponent1(__VLS_18, new __VLS_18({
    depth: "3",
}));
const __VLS_20 = __VLS_19({
    depth: "3",
}, ...__VLS_functionalComponentArgsRest(__VLS_19));
const { default: __VLS_23 } = __VLS_21.slots;
(__VLS_ctx.t('profile.username'));
// @ts-ignore
[t,];
var __VLS_21;
let __VLS_24;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_25 = __VLS_asFunctionalComponent1(__VLS_24, new __VLS_24({}));
const __VLS_26 = __VLS_25({}, ...__VLS_functionalComponentArgsRest(__VLS_25));
const { default: __VLS_29 } = __VLS_27.slots;
(__VLS_ctx.auth.user?.username ?? '-');
// @ts-ignore
[auth,];
var __VLS_27;
// @ts-ignore
[];
var __VLS_15;
// @ts-ignore
[];
var __VLS_9;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
