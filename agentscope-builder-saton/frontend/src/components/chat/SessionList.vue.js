import { computed } from 'vue';
import { NButton, NEmpty, NSpin, NScrollbar, useThemeVars } from 'naive-ui';
import { useI18n } from 'vue-i18n';
const props = withDefaults(defineProps(), { loading: false });
const emit = defineEmits();
const { t } = useI18n();
const themeVars = useThemeVars();
const items = computed(() => props.sessions);
/**
 * 显示用标题：title 非空就截到 24 字符，否则回落到 sessionKey。
 * 后端给的 title 不截断，截断逻辑全在前端做。
 */
const TITLE_MAX = 24;
function displayTitle(s) {
    const t = (s.title ?? '').trim();
    if (!t)
        return s.sessionKey;
    return t.length > TITLE_MAX ? t.slice(0, TITLE_MAX) + '…' : t;
}
/**
 * 通过 useThemeVars 取响应式主题色：暗黑模式下自动切换。
 * 直接写 CSS var(--n-*) 不可靠 —— 这些变量只在某些 Naive UI 组件（NCard 等）上下文里注入，
 * NLayoutSider 里不存在，会落到 fallback 写死颜色，破坏暗黑模式。
 */
const styleVars = computed(() => ({
    '--ss-bg': themeVars.value.cardColor,
    '--ss-border': themeVars.value.dividerColor,
    '--ss-hover': themeVars.value.hoverColor,
    '--ss-active': themeVars.value.primaryColorSuppl,
    '--ss-active-text': themeVars.value.primaryColor,
    '--ss-text': themeVars.value.textColor1,
}));
const __VLS_defaults = { loading: false };
const __VLS_ctx = {
    ...{},
    ...{},
    ...{},
    ...{},
    ...{},
};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
/** @type {__VLS_StyleScopedClasses['session-list__item']} */ ;
/** @type {__VLS_StyleScopedClasses['session-list__item']} */ ;
/** @type {__VLS_StyleScopedClasses['session-list__delete']} */ ;
/** @type {__VLS_StyleScopedClasses['session-list__delete']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "session-list" },
    ...{ style: (__VLS_ctx.styleVars) },
});
/** @type {__VLS_StyleScopedClasses['session-list']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "session-list__header" },
});
/** @type {__VLS_StyleScopedClasses['session-list__header']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
    ...{ class: "session-list__title" },
});
/** @type {__VLS_StyleScopedClasses['session-list__title']} */ ;
(__VLS_ctx.t('chat.sessions'));
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    ...{ 'onClick': {} },
    size: "small",
    type: "primary",
}));
const __VLS_2 = __VLS_1({
    ...{ 'onClick': {} },
    size: "small",
    type: "primary",
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
let __VLS_5;
const __VLS_6 = {
    ...{ click: {} },
    onClick: (...[$event]) => {
        __VLS_ctx.emit('new');
        // @ts-ignore
        [styleVars, t, emit,];
    },
};
const { default: __VLS_7 } = __VLS_3.slots;
(__VLS_ctx.t('chat.newSession'));
// @ts-ignore
[t,];
var __VLS_3;
var __VLS_4;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "session-list__body" },
});
/** @type {__VLS_StyleScopedClasses['session-list__body']} */ ;
if (__VLS_ctx.loading) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "session-list__center" },
    });
    /** @type {__VLS_StyleScopedClasses['session-list__center']} */ ;
    let __VLS_8;
    /** @ts-ignore @type { | typeof __VLS_components.NSpin} */
    NSpin;
    // @ts-ignore
    const __VLS_9 = __VLS_asFunctionalComponent1(__VLS_8, new __VLS_8({
        size: "small",
    }));
    const __VLS_10 = __VLS_9({
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_9));
}
else if (__VLS_ctx.items.length === 0) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "session-list__center" },
    });
    /** @type {__VLS_StyleScopedClasses['session-list__center']} */ ;
    let __VLS_13;
    /** @ts-ignore @type { | typeof __VLS_components.NEmpty} */
    NEmpty;
    // @ts-ignore
    const __VLS_14 = __VLS_asFunctionalComponent1(__VLS_13, new __VLS_13({
        description: (__VLS_ctx.t('chat.sessionsEmpty')),
        size: "small",
    }));
    const __VLS_15 = __VLS_14({
        description: (__VLS_ctx.t('chat.sessionsEmpty')),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_14));
}
else {
    let __VLS_18;
    /** @ts-ignore @type { | typeof __VLS_components.NScrollbar | typeof __VLS_components.NScrollbar} */
    NScrollbar;
    // @ts-ignore
    const __VLS_19 = __VLS_asFunctionalComponent1(__VLS_18, new __VLS_18({
        ...{ style: {} },
    }));
    const __VLS_20 = __VLS_19({
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_19));
    const { default: __VLS_23 } = __VLS_21.slots;
    for (const [s] of __VLS_vFor((__VLS_ctx.items))) {
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
            ...{ onClick: (...[$event]) => {
                    if (!!(__VLS_ctx.loading))
                        return;
                    if (!!(__VLS_ctx.items.length === 0))
                        return;
                    __VLS_ctx.emit('select', s.sessionKey);
                    // @ts-ignore
                    [t, emit, loading, items, items,];
                } },
            key: (s.sessionKey),
            ...{ class: "session-list__item" },
            ...{ class: ({ 'session-list__item--active': s.sessionKey === __VLS_ctx.activeKey }) },
        });
        /** @type {__VLS_StyleScopedClasses['session-list__item']} */ ;
        /** @type {__VLS_StyleScopedClasses['session-list__item--active']} */ ;
        __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
            ...{ class: "session-list__title-text" },
            title: (s.title || s.sessionKey),
        });
        /** @type {__VLS_StyleScopedClasses['session-list__title-text']} */ ;
        (__VLS_ctx.displayTitle(s));
        let __VLS_24;
        /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
        NButton;
        // @ts-ignore
        const __VLS_25 = __VLS_asFunctionalComponent1(__VLS_24, new __VLS_24({
            ...{ 'onClick': {} },
            quaternary: true,
            circle: true,
            size: "tiny",
            ...{ class: "session-list__delete" },
        }));
        const __VLS_26 = __VLS_25({
            ...{ 'onClick': {} },
            quaternary: true,
            circle: true,
            size: "tiny",
            ...{ class: "session-list__delete" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_25));
        let __VLS_29;
        const __VLS_30 = {
            ...{ click: {} },
            onClick: (...[$event]) => {
                if (!!(__VLS_ctx.loading))
                    return;
                if (!!(__VLS_ctx.items.length === 0))
                    return;
                __VLS_ctx.emit('delete', s.sessionKey);
                // @ts-ignore
                [emit, activeKey, displayTitle,];
            },
        };
        /** @type {__VLS_StyleScopedClasses['session-list__delete']} */ ;
        const { default: __VLS_31 } = __VLS_27.slots;
        // @ts-ignore
        [];
        var __VLS_27;
        var __VLS_28;
        // @ts-ignore
        [];
    }
    // @ts-ignore
    [];
    var __VLS_21;
}
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({
    __typeEmits: {},
    __typeProps: {},
    props: {},
});
export default {};
