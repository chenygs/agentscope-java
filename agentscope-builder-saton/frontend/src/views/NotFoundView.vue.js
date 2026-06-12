import { useRouter } from 'vue-router';
import { NButton, NResult } from 'naive-ui';
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
    ...{ style: {} },
});
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NResult | typeof __VLS_components.NResult} */
NResult;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    status: "404",
    title: (__VLS_ctx.t('notFound.title')),
    description: (__VLS_ctx.t('notFound.message')),
}));
const __VLS_2 = __VLS_1({
    status: "404",
    title: (__VLS_ctx.t('notFound.title')),
    description: (__VLS_ctx.t('notFound.message')),
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
const { default: __VLS_5 } = __VLS_3.slots;
{
    const { footer: __VLS_6 } = __VLS_3.slots;
    let __VLS_7;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_8 = __VLS_asFunctionalComponent1(__VLS_7, new __VLS_7({
        ...{ 'onClick': {} },
    }));
    const __VLS_9 = __VLS_8({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_8));
    let __VLS_12;
    const __VLS_13 = {
        ...{ click: {} },
        onClick: (...[$event]) => {
            __VLS_ctx.router.push('/');
            // @ts-ignore
            [t, t, router,];
        },
    };
    const { default: __VLS_14 } = __VLS_10.slots;
    (__VLS_ctx.t('notFound.back'));
    // @ts-ignore
    [t,];
    var __VLS_10;
    var __VLS_11;
    // @ts-ignore
    [];
}
// @ts-ignore
[];
var __VLS_3;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
