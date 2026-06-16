import { computed, onMounted, reactive, ref, watch } from 'vue';
import { NAlert, NButton, NEmpty, NH2, NInput, NSpace, NSpin, NTabPane, NTabs, NTag, NText, NTooltip, useDialog, useMessage, useThemeVars, } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import * as memoryApi from '@/api/memory';
const { t } = useI18n();
const dialog = useDialog();
const message = useMessage();
const themeVars = useThemeVars();
/** 后端 WorkspaceService.MAX_READ 超限时返回的字符串前缀 */
const TOO_LARGE_PREFIX = '(file too large to display:';
const KINDS = [
    { key: 'persona', tab: 'memory.personaTab', hint: 'memory.personaHint' },
    { key: 'long-term', tab: 'memory.longTermTab', hint: 'memory.longTermHint' },
];
// ── 状态 ──
const summary = ref(null);
const loadingSummary = ref(false);
const activeKind = ref('persona');
const states = reactive({
    persona: { content: '', loaded: false, loading: false, saving: false, dirty: false },
    'long-term': { content: '', loaded: false, loading: false, saving: false, dirty: false },
});
// ── 派生 ──
const currentState = computed(() => states[activeKind.value]);
const currentMeta = computed(() => summary.value?.files.find((f) => f.kind === activeKind.value) ?? null);
const currentTooLarge = computed(() => currentState.value.content.startsWith(TOO_LARGE_PREFIX));
// ── 加载 ──
async function loadSummary() {
    loadingSummary.value = true;
    try {
        const resp = await memoryApi.getMemorySummary();
        summary.value = resp.data.data;
    }
    catch {
        // interceptor handled
    }
    finally {
        loadingSummary.value = false;
    }
}
async function loadKind(kind) {
    const s = states[kind];
    s.loading = true;
    try {
        const resp = await memoryApi.readMemory(kind);
        s.content = typeof resp.data === 'string' ? resp.data : String(resp.data ?? '');
        s.loaded = true;
        s.dirty = false;
    }
    catch {
        // interceptor handled
    }
    finally {
        s.loading = false;
    }
}
async function refresh() {
    await loadSummary();
    // 已加载过的 kind 重新拉一次,未加载过的等切到才拉
    await Promise.all(KINDS.filter((k) => states[k.key].loaded).map((k) => loadKind(k.key)));
}
// 切 Tab 时按需加载
watch(activeKind, (kind) => {
    if (!states[kind].loaded && !states[kind].loading) {
        loadKind(kind);
    }
});
onMounted(async () => {
    await loadSummary();
    await loadKind(activeKind.value);
});
// ── 保存 ──
async function onSave() {
    const kind = activeKind.value;
    const s = states[kind];
    s.saving = true;
    try {
        await memoryApi.writeMemory(kind, s.content);
        s.dirty = false;
        message.success(t('memory.savedOk'));
        await loadSummary();
    }
    catch {
        // interceptor handled
    }
    finally {
        s.saving = false;
    }
}
// ── 切 Tab 前如果脏则确认 ──
function beforeLeave(toName, fromName) {
    const fromKind = fromName;
    if (!states[fromKind]?.dirty)
        return true;
    dialog.warning({
        title: t('memory.unsavedTitle'),
        content: t('memory.unsavedConfirm'),
        positiveText: t('common.discard'),
        negativeText: t('common.cancel'),
        onPositiveClick: async () => {
            states[fromKind].dirty = false;
            // 用户选择丢弃后重新拉一份原始内容
            await loadKind(fromKind);
            activeKind.value = toName;
        },
    });
    return false;
}
// ── 工具 ──
function formatSize(bytes) {
    if (bytes < 1024)
        return bytes + ' B';
    if (bytes < 1024 * 1024)
        return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1024 / 1024).toFixed(2) + ' MB';
}
function formatTime(ms) {
    if (!ms)
        return '—';
    return new Date(ms).toLocaleString();
}
const styleVars = computed(() => ({
    '--mem-border': themeVars.value.dividerColor,
    '--mem-text-2': themeVars.value.textColor2,
    '--mem-text-3': themeVars.value.textColor3,
}));
const __VLS_ctx = {
    ...{},
    ...{},
};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "mem-view" },
    ...{ style: (__VLS_ctx.styleVars) },
});
/** @type {__VLS_StyleScopedClasses['mem-view']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "mem-head" },
});
/** @type {__VLS_StyleScopedClasses['mem-head']} */ ;
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NH2 | typeof __VLS_components.NH2} */
NH2;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    ...{ style: {} },
}));
const __VLS_2 = __VLS_1({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
const { default: __VLS_5 } = __VLS_3.slots;
(__VLS_ctx.t('memory.title'));
// @ts-ignore
[styleVars, t,];
var __VLS_3;
let __VLS_6;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    depth: (3),
    ...{ style: {} },
}));
const __VLS_8 = __VLS_7({
    depth: (3),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
const { default: __VLS_11 } = __VLS_9.slots;
(__VLS_ctx.t('memory.subtitle'));
// @ts-ignore
[t,];
var __VLS_9;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "mem-summary" },
});
/** @type {__VLS_StyleScopedClasses['mem-summary']} */ ;
let __VLS_12;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent1(__VLS_12, new __VLS_12({
    align: "center",
    size: (12),
    wrap: (false),
}));
const __VLS_14 = __VLS_13({
    align: "center",
    size: (12),
    wrap: (false),
}, ...__VLS_functionalComponentArgsRest(__VLS_13));
const { default: __VLS_17 } = __VLS_15.slots;
let __VLS_18;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_19 = __VLS_asFunctionalComponent1(__VLS_18, new __VLS_18({
    depth: (3),
    ...{ style: {} },
}));
const __VLS_20 = __VLS_19({
    depth: (3),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_19));
const { default: __VLS_23 } = __VLS_21.slots;
(__VLS_ctx.t('memory.rootPath'));
// @ts-ignore
[t,];
var __VLS_21;
let __VLS_24;
/** @ts-ignore @type { | typeof __VLS_components.NTooltip | typeof __VLS_components.NTooltip} */
NTooltip;
// @ts-ignore
const __VLS_25 = __VLS_asFunctionalComponent1(__VLS_24, new __VLS_24({
    placement: "bottom-start",
    trigger: "hover",
}));
const __VLS_26 = __VLS_25({
    placement: "bottom-start",
    trigger: "hover",
}, ...__VLS_functionalComponentArgsRest(__VLS_25));
const { default: __VLS_29 } = __VLS_27.slots;
{
    const { trigger: __VLS_30 } = __VLS_27.slots;
    let __VLS_31;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_32 = __VLS_asFunctionalComponent1(__VLS_31, new __VLS_31({
        ...{ class: "mem-path" },
    }));
    const __VLS_33 = __VLS_32({
        ...{ class: "mem-path" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_32));
    /** @type {__VLS_StyleScopedClasses['mem-path']} */ ;
    const { default: __VLS_36 } = __VLS_34.slots;
    (__VLS_ctx.summary?.root ?? '—');
    // @ts-ignore
    [summary,];
    var __VLS_34;
    // @ts-ignore
    [];
}
(__VLS_ctx.summary?.root ?? '—');
// @ts-ignore
[summary,];
var __VLS_27;
let __VLS_37;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_38 = __VLS_asFunctionalComponent1(__VLS_37, new __VLS_37({
    ...{ 'onClick': {} },
    size: "tiny",
    quaternary: true,
    loading: (__VLS_ctx.loadingSummary),
}));
const __VLS_39 = __VLS_38({
    ...{ 'onClick': {} },
    size: "tiny",
    quaternary: true,
    loading: (__VLS_ctx.loadingSummary),
}, ...__VLS_functionalComponentArgsRest(__VLS_38));
let __VLS_42;
const __VLS_43 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.refresh),
};
const { default: __VLS_44 } = __VLS_40.slots;
(__VLS_ctx.t('common.refresh'));
// @ts-ignore
[t, loadingSummary, refresh,];
var __VLS_40;
var __VLS_41;
// @ts-ignore
[];
var __VLS_15;
let __VLS_45;
/** @ts-ignore @type { | typeof __VLS_components.NTabs | typeof __VLS_components.NTabs} */
NTabs;
// @ts-ignore
const __VLS_46 = __VLS_asFunctionalComponent1(__VLS_45, new __VLS_45({
    value: (__VLS_ctx.activeKind),
    type: "line",
    animated: true,
    onBeforeLeave: (__VLS_ctx.beforeLeave),
    ...{ class: "mem-tabs" },
}));
const __VLS_47 = __VLS_46({
    value: (__VLS_ctx.activeKind),
    type: "line",
    animated: true,
    onBeforeLeave: (__VLS_ctx.beforeLeave),
    ...{ class: "mem-tabs" },
}, ...__VLS_functionalComponentArgsRest(__VLS_46));
/** @type {__VLS_StyleScopedClasses['mem-tabs']} */ ;
const { default: __VLS_50 } = __VLS_48.slots;
for (const [k] of __VLS_vFor((__VLS_ctx.KINDS))) {
    let __VLS_51;
    /** @ts-ignore @type { | typeof __VLS_components.NTabPane | typeof __VLS_components.NTabPane} */
    NTabPane;
    // @ts-ignore
    const __VLS_52 = __VLS_asFunctionalComponent1(__VLS_51, new __VLS_51({
        key: (k.key),
        name: (k.key),
        tab: (__VLS_ctx.t(k.tab)),
    }));
    const __VLS_53 = __VLS_52({
        key: (k.key),
        name: (k.key),
        tab: (__VLS_ctx.t(k.tab)),
    }, ...__VLS_functionalComponentArgsRest(__VLS_52));
    const { default: __VLS_56 } = __VLS_54.slots;
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "mem-pane" },
    });
    /** @type {__VLS_StyleScopedClasses['mem-pane']} */ ;
    let __VLS_57;
    /** @ts-ignore @type { | typeof __VLS_components.NAlert | typeof __VLS_components.NAlert} */
    NAlert;
    // @ts-ignore
    const __VLS_58 = __VLS_asFunctionalComponent1(__VLS_57, new __VLS_57({
        showIcon: (false),
        type: "default",
        ...{ class: "mem-hint" },
    }));
    const __VLS_59 = __VLS_58({
        showIcon: (false),
        type: "default",
        ...{ class: "mem-hint" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_58));
    /** @type {__VLS_StyleScopedClasses['mem-hint']} */ ;
    const { default: __VLS_62 } = __VLS_60.slots;
    let __VLS_63;
    /** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
    NSpace;
    // @ts-ignore
    const __VLS_64 = __VLS_asFunctionalComponent1(__VLS_63, new __VLS_63({
        align: "center",
        wrap: (false),
        size: (8),
    }));
    const __VLS_65 = __VLS_64({
        align: "center",
        wrap: (false),
        size: (8),
    }, ...__VLS_functionalComponentArgsRest(__VLS_64));
    const { default: __VLS_68 } = __VLS_66.slots;
    let __VLS_69;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_70 = __VLS_asFunctionalComponent1(__VLS_69, new __VLS_69({
        depth: (2),
        ...{ style: {} },
    }));
    const __VLS_71 = __VLS_70({
        depth: (2),
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_70));
    const { default: __VLS_74 } = __VLS_72.slots;
    (__VLS_ctx.t(k.hint));
    // @ts-ignore
    [t, t, activeKind, beforeLeave, KINDS,];
    var __VLS_72;
    if (__VLS_ctx.currentMeta) {
        if (!__VLS_ctx.currentMeta.exists) {
            let __VLS_75;
            /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
            NTag;
            // @ts-ignore
            const __VLS_76 = __VLS_asFunctionalComponent1(__VLS_75, new __VLS_75({
                size: "small",
                bordered: (false),
            }));
            const __VLS_77 = __VLS_76({
                size: "small",
                bordered: (false),
            }, ...__VLS_functionalComponentArgsRest(__VLS_76));
            const { default: __VLS_80 } = __VLS_78.slots;
            (__VLS_ctx.t('memory.notExists'));
            // @ts-ignore
            [t, currentMeta, currentMeta,];
            var __VLS_78;
        }
        else {
            let __VLS_81;
            /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
            NTag;
            // @ts-ignore
            const __VLS_82 = __VLS_asFunctionalComponent1(__VLS_81, new __VLS_81({
                size: "small",
                type: "info",
                bordered: (false),
            }));
            const __VLS_83 = __VLS_82({
                size: "small",
                type: "info",
                bordered: (false),
            }, ...__VLS_functionalComponentArgsRest(__VLS_82));
            const { default: __VLS_86 } = __VLS_84.slots;
            (__VLS_ctx.t('memory.fileSize'));
            (__VLS_ctx.formatSize(__VLS_ctx.currentMeta.size));
            // @ts-ignore
            [t, currentMeta, formatSize,];
            var __VLS_84;
            let __VLS_87;
            /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
            NTag;
            // @ts-ignore
            const __VLS_88 = __VLS_asFunctionalComponent1(__VLS_87, new __VLS_87({
                size: "small",
                bordered: (false),
            }));
            const __VLS_89 = __VLS_88({
                size: "small",
                bordered: (false),
            }, ...__VLS_functionalComponentArgsRest(__VLS_88));
            const { default: __VLS_92 } = __VLS_90.slots;
            (__VLS_ctx.t('memory.lastModified'));
            (__VLS_ctx.formatTime(__VLS_ctx.currentMeta.modifiedAt));
            // @ts-ignore
            [t, currentMeta, formatTime,];
            var __VLS_90;
        }
    }
    // @ts-ignore
    [];
    var __VLS_66;
    // @ts-ignore
    [];
    var __VLS_60;
    if (__VLS_ctx.currentState.loading && !__VLS_ctx.currentState.loaded) {
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
            ...{ class: "mem-loading" },
        });
        /** @type {__VLS_StyleScopedClasses['mem-loading']} */ ;
        let __VLS_93;
        /** @ts-ignore @type { | typeof __VLS_components.NSpin} */
        NSpin;
        // @ts-ignore
        const __VLS_94 = __VLS_asFunctionalComponent1(__VLS_93, new __VLS_93({
            size: "small",
        }));
        const __VLS_95 = __VLS_94({
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_94));
    }
    else {
        let __VLS_98;
        /** @ts-ignore @type { | typeof __VLS_components.NInput} */
        NInput;
        // @ts-ignore
        const __VLS_99 = __VLS_asFunctionalComponent1(__VLS_98, new __VLS_98({
            ...{ 'onUpdate:value': {} },
            value: (__VLS_ctx.currentState.content),
            type: "textarea",
            placeholder: (__VLS_ctx.t('memory.placeholder')),
            readonly: (__VLS_ctx.currentTooLarge),
            rows: (20),
            ...{ class: "mem-textarea" },
        }));
        const __VLS_100 = __VLS_99({
            ...{ 'onUpdate:value': {} },
            value: (__VLS_ctx.currentState.content),
            type: "textarea",
            placeholder: (__VLS_ctx.t('memory.placeholder')),
            readonly: (__VLS_ctx.currentTooLarge),
            rows: (20),
            ...{ class: "mem-textarea" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_99));
        let __VLS_103;
        const __VLS_104 = {
            ...{ 'update:value': {} },
            'onUpdate:value': (...[$event]) => {
                if (!!(__VLS_ctx.currentState.loading && !__VLS_ctx.currentState.loaded))
                    return;
                __VLS_ctx.currentState.dirty = !__VLS_ctx.currentTooLarge;
                // @ts-ignore
                [t, currentState, currentState, currentState, currentState, currentTooLarge, currentTooLarge,];
            },
        };
        /** @type {__VLS_StyleScopedClasses['mem-textarea']} */ ;
        var __VLS_101;
        var __VLS_102;
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
            ...{ class: "mem-actions" },
        });
        /** @type {__VLS_StyleScopedClasses['mem-actions']} */ ;
        if (__VLS_ctx.currentTooLarge) {
            let __VLS_105;
            /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
            NTag;
            // @ts-ignore
            const __VLS_106 = __VLS_asFunctionalComponent1(__VLS_105, new __VLS_105({
                size: "small",
                type: "warning",
                bordered: (false),
            }));
            const __VLS_107 = __VLS_106({
                size: "small",
                type: "warning",
                bordered: (false),
            }, ...__VLS_functionalComponentArgsRest(__VLS_106));
            const { default: __VLS_110 } = __VLS_108.slots;
            (__VLS_ctx.t('memory.fileTooLarge'));
            // @ts-ignore
            [t, currentTooLarge,];
            var __VLS_108;
        }
        else if (__VLS_ctx.currentState.dirty) {
            let __VLS_111;
            /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
            NTag;
            // @ts-ignore
            const __VLS_112 = __VLS_asFunctionalComponent1(__VLS_111, new __VLS_111({
                size: "small",
                type: "warning",
                bordered: (false),
            }));
            const __VLS_113 = __VLS_112({
                size: "small",
                type: "warning",
                bordered: (false),
            }, ...__VLS_functionalComponentArgsRest(__VLS_112));
            const { default: __VLS_116 } = __VLS_114.slots;
            // @ts-ignore
            [currentState,];
            var __VLS_114;
        }
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
            ...{ style: {} },
        });
        let __VLS_117;
        /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
        NButton;
        // @ts-ignore
        const __VLS_118 = __VLS_asFunctionalComponent1(__VLS_117, new __VLS_117({
            ...{ 'onClick': {} },
            type: "primary",
            disabled: (!__VLS_ctx.currentState.dirty || __VLS_ctx.currentTooLarge),
            loading: (__VLS_ctx.currentState.saving),
        }));
        const __VLS_119 = __VLS_118({
            ...{ 'onClick': {} },
            type: "primary",
            disabled: (!__VLS_ctx.currentState.dirty || __VLS_ctx.currentTooLarge),
            loading: (__VLS_ctx.currentState.saving),
        }, ...__VLS_functionalComponentArgsRest(__VLS_118));
        let __VLS_122;
        const __VLS_123 = {
            ...{ click: {} },
            onClick: (__VLS_ctx.onSave),
        };
        const { default: __VLS_124 } = __VLS_120.slots;
        (__VLS_ctx.t('common.save'));
        // @ts-ignore
        [t, currentState, currentState, currentTooLarge, onSave,];
        var __VLS_120;
        var __VLS_121;
    }
    // @ts-ignore
    [];
    var __VLS_54;
    // @ts-ignore
    [];
}
// @ts-ignore
[];
var __VLS_48;
if (!__VLS_ctx.summary && !__VLS_ctx.loadingSummary) {
    let __VLS_125;
    /** @ts-ignore @type { | typeof __VLS_components.NEmpty} */
    NEmpty;
    // @ts-ignore
    const __VLS_126 = __VLS_asFunctionalComponent1(__VLS_125, new __VLS_125({
        ...{ style: {} },
    }));
    const __VLS_127 = __VLS_126({
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_126));
}
// @ts-ignore
[summary, loadingSummary,];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
