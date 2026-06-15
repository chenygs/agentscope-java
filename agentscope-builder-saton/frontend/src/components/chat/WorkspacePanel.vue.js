import { computed, onMounted, reactive, ref } from 'vue';
import { NButton, NEmpty, NInput, NScrollbar, NSpace, NSpin, NText, NTag, NTooltip, useDialog, useMessage, useThemeVars, } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import * as workspaceApi from '@/api/workspace';
const props = defineProps();
const { t } = useI18n();
const dialog = useDialog();
const message = useMessage();
const themeVars = useThemeVars();
// ── 摘要 ──
const summary = ref(null);
const loadingSummary = ref(false);
// ── 文件树状态 ──
/**
 * 路径 → 该路径下的子节点(后端已解析,path 字段是相对 agent root 的完整相对路径)。
 * '' 表示根目录。已加载过的 key 不会重新拉,除非 refresh()。
 */
const childrenByPath = reactive({});
const expandedPaths = reactive(new Set([''])); // 根目录默认展开
const loadingPaths = reactive(new Set());
// ── 编辑器 ──
const editingPath = ref(null);
const editingContent = ref('');
const editingDirty = ref(false);
/** 后端 WorkspaceService.MAX_READ 超限时返回的字符串前缀 */
const TOO_LARGE_PREFIX = '(file too large to display:';
const editingTooLarge = computed(() => editingContent.value.startsWith(TOO_LARGE_PREFIX));
const saving = ref(false);
/**
 * 把 childrenByPath 这个 map 拍成一份扁平、带 depth 的列表用于渲染:
 * 只展开的目录会贡献它的子节点,未展开的目录只贡献自己一行。
 * 这避免了 Vue 3 递归组件的样板,所有缩进直接靠 paddingLeft 算。
 */
const flatRows = computed(() => {
    const out = [];
    function walk(parentPath, depth) {
        const list = childrenByPath[parentPath] ?? [];
        for (const node of list) {
            out.push({ node, depth });
            if (node.type === 'dir' && expandedPaths.has(node.path)) {
                walk(node.path, depth + 1);
            }
        }
    }
    walk('', 0);
    return out;
});
async function loadDir(path) {
    loadingPaths.add(path);
    try {
        const resp = await workspaceApi.listWorkspaceFiles(props.agentId, path || undefined);
        childrenByPath[path] = resp.data.data ?? [];
    }
    catch {
        childrenByPath[path] = [];
    }
    finally {
        loadingPaths.delete(path);
    }
}
async function refreshSummary() {
    loadingSummary.value = true;
    try {
        const resp = await workspaceApi.getWorkspaceSummary(props.agentId);
        summary.value = resp.data.data;
    }
    catch {
        // interceptor handled
    }
    finally {
        loadingSummary.value = false;
    }
}
/** 全量刷新:summary + 当前展开过的所有目录(保留展开状态)。 */
async function refresh() {
    const paths = Array.from(expandedPaths);
    if (!paths.includes(''))
        paths.push('');
    await Promise.all([refreshSummary(), ...paths.map(p => loadDir(p))]);
}
onMounted(refresh);
const __VLS_exposed = { refresh };
defineExpose(__VLS_exposed);
async function toggleDir(node) {
    if (expandedPaths.has(node.path)) {
        expandedPaths.delete(node.path);
        return;
    }
    expandedPaths.add(node.path);
    if (!(node.path in childrenByPath)) {
        await loadDir(node.path);
    }
}
function formatSize(bytes) {
    if (bytes < 1024)
        return bytes + ' B';
    if (bytes < 1024 * 1024)
        return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1024 / 1024).toFixed(2) + ' MB';
}
async function onPickFile(node) {
    try {
        const resp = await workspaceApi.readWorkspaceFile(props.agentId, node.path);
        editingPath.value = node.path;
        editingContent.value = typeof resp.data === 'string' ? resp.data : String(resp.data ?? '');
        editingDirty.value = false;
    }
    catch {
        // interceptor handled
    }
}
async function onSave() {
    if (!editingPath.value)
        return;
    saving.value = true;
    try {
        await workspaceApi.writeWorkspaceFile(props.agentId, editingPath.value, editingContent.value);
        message.success(t('chat.workspaceFileSaved'));
        editingDirty.value = false;
        await refresh();
    }
    catch {
        // interceptor handled
    }
    finally {
        saving.value = false;
    }
}
function onDelete(node) {
    dialog.warning({
        title: t('common.delete'),
        content: t('chat.workspaceDeleteConfirm', { path: node.path }),
        positiveText: t('common.confirm'),
        negativeText: t('common.cancel'),
        onPositiveClick: async () => {
            try {
                await workspaceApi.deleteWorkspaceFile(props.agentId, node.path);
                message.success(t('chat.workspaceFileDeleted'));
                if (editingPath.value === node.path) {
                    editingPath.value = null;
                    editingContent.value = '';
                    editingDirty.value = false;
                }
                await refresh();
            }
            catch {
                // interceptor handled
            }
        },
    });
}
function closeEditor() {
    if (editingDirty.value) {
        dialog.warning({
            title: t('agent.unsavedWarning'),
            positiveText: t('common.discard'),
            negativeText: t('common.cancel'),
            onPositiveClick: () => {
                editingPath.value = null;
                editingContent.value = '';
                editingDirty.value = false;
            },
        });
    }
    else {
        editingPath.value = null;
        editingContent.value = '';
    }
}
const styleVars = computed(() => ({
    '--ws-border': themeVars.value.dividerColor,
    '--ws-hover': themeVars.value.hoverColor,
    '--ws-text-2': themeVars.value.textColor2,
    '--ws-text-3': themeVars.value.textColor3,
}));
const isRootEmpty = computed(() => '' in childrenByPath && (childrenByPath[''] ?? []).length === 0);
const __VLS_ctx = {
    ...{},
    ...{},
    ...{},
    ...{},
};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
/** @type {__VLS_StyleScopedClasses['ws-file']} */ ;
/** @type {__VLS_StyleScopedClasses['ws-file']} */ ;
/** @type {__VLS_StyleScopedClasses['ws-file__del']} */ ;
/** @type {__VLS_StyleScopedClasses['ws-file__del']} */ ;
/** @type {__VLS_StyleScopedClasses['ws-editor__textarea']} */ ;
/** @type {__VLS_StyleScopedClasses['ws-editor__textarea']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "ws-panel" },
    ...{ style: (__VLS_ctx.styleVars) },
});
/** @type {__VLS_StyleScopedClasses['ws-panel']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "ws-summary" },
});
/** @type {__VLS_StyleScopedClasses['ws-summary']} */ ;
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    vertical: true,
    size: (4),
}));
const __VLS_2 = __VLS_1({
    vertical: true,
    size: (4),
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
const { default: __VLS_5 } = __VLS_3.slots;
let __VLS_6;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    align: "center",
    size: (6),
    wrap: (false),
}));
const __VLS_8 = __VLS_7({
    align: "center",
    size: (6),
    wrap: (false),
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
const { default: __VLS_11 } = __VLS_9.slots;
let __VLS_12;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent1(__VLS_12, new __VLS_12({
    depth: (3),
    ...{ style: {} },
}));
const __VLS_14 = __VLS_13({
    depth: (3),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_13));
const { default: __VLS_17 } = __VLS_15.slots;
(__VLS_ctx.t('chat.workspacePath'));
// @ts-ignore
[styleVars, t,];
var __VLS_15;
let __VLS_18;
/** @ts-ignore @type { | typeof __VLS_components.NTooltip | typeof __VLS_components.NTooltip} */
NTooltip;
// @ts-ignore
const __VLS_19 = __VLS_asFunctionalComponent1(__VLS_18, new __VLS_18({
    placement: "bottom-start",
    trigger: "hover",
}));
const __VLS_20 = __VLS_19({
    placement: "bottom-start",
    trigger: "hover",
}, ...__VLS_functionalComponentArgsRest(__VLS_19));
const { default: __VLS_23 } = __VLS_21.slots;
{
    const { trigger: __VLS_24 } = __VLS_21.slots;
    let __VLS_25;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_26 = __VLS_asFunctionalComponent1(__VLS_25, new __VLS_25({
        ...{ class: "ws-path" },
    }));
    const __VLS_27 = __VLS_26({
        ...{ class: "ws-path" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_26));
    /** @type {__VLS_StyleScopedClasses['ws-path']} */ ;
    const { default: __VLS_30 } = __VLS_28.slots;
    (__VLS_ctx.summary?.root ?? '—');
    // @ts-ignore
    [summary,];
    var __VLS_28;
    // @ts-ignore
    [];
}
(__VLS_ctx.summary?.root ?? '—');
// @ts-ignore
[summary,];
var __VLS_21;
// @ts-ignore
[];
var __VLS_9;
let __VLS_31;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_32 = __VLS_asFunctionalComponent1(__VLS_31, new __VLS_31({
    align: "center",
    size: (6),
}));
const __VLS_33 = __VLS_32({
    align: "center",
    size: (6),
}, ...__VLS_functionalComponentArgsRest(__VLS_32));
const { default: __VLS_36 } = __VLS_34.slots;
let __VLS_37;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_38 = __VLS_asFunctionalComponent1(__VLS_37, new __VLS_37({
    depth: (3),
    ...{ style: {} },
}));
const __VLS_39 = __VLS_38({
    depth: (3),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_38));
const { default: __VLS_42 } = __VLS_40.slots;
(__VLS_ctx.t('chat.workspaceFileCount'));
// @ts-ignore
[t,];
var __VLS_40;
let __VLS_43;
/** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
NTag;
// @ts-ignore
const __VLS_44 = __VLS_asFunctionalComponent1(__VLS_43, new __VLS_43({
    size: "small",
    type: "info",
    bordered: (false),
}));
const __VLS_45 = __VLS_44({
    size: "small",
    type: "info",
    bordered: (false),
}, ...__VLS_functionalComponentArgsRest(__VLS_44));
const { default: __VLS_48 } = __VLS_46.slots;
(__VLS_ctx.summary?.fileCount ?? 0);
// @ts-ignore
[summary,];
var __VLS_46;
let __VLS_49;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_50 = __VLS_asFunctionalComponent1(__VLS_49, new __VLS_49({
    ...{ 'onClick': {} },
    size: "tiny",
    quaternary: true,
    loading: (__VLS_ctx.loadingSummary),
}));
const __VLS_51 = __VLS_50({
    ...{ 'onClick': {} },
    size: "tiny",
    quaternary: true,
    loading: (__VLS_ctx.loadingSummary),
}, ...__VLS_functionalComponentArgsRest(__VLS_50));
let __VLS_54;
const __VLS_55 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.refresh),
};
const { default: __VLS_56 } = __VLS_52.slots;
(__VLS_ctx.t('common.refresh'));
// @ts-ignore
[t, loadingSummary, refresh,];
var __VLS_52;
var __VLS_53;
// @ts-ignore
[];
var __VLS_34;
// @ts-ignore
[];
var __VLS_3;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "ws-files" },
});
/** @type {__VLS_StyleScopedClasses['ws-files']} */ ;
if (__VLS_ctx.loadingSummary && __VLS_ctx.flatRows.length === 0 && !('' in __VLS_ctx.childrenByPath)) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "ws-center" },
    });
    /** @type {__VLS_StyleScopedClasses['ws-center']} */ ;
    let __VLS_57;
    /** @ts-ignore @type { | typeof __VLS_components.NSpin} */
    NSpin;
    // @ts-ignore
    const __VLS_58 = __VLS_asFunctionalComponent1(__VLS_57, new __VLS_57({
        size: "small",
    }));
    const __VLS_59 = __VLS_58({
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_58));
}
else if (__VLS_ctx.isRootEmpty) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "ws-center" },
    });
    /** @type {__VLS_StyleScopedClasses['ws-center']} */ ;
    let __VLS_62;
    /** @ts-ignore @type { | typeof __VLS_components.NEmpty} */
    NEmpty;
    // @ts-ignore
    const __VLS_63 = __VLS_asFunctionalComponent1(__VLS_62, new __VLS_62({
        description: (__VLS_ctx.t('chat.workspaceEmpty')),
        size: "small",
    }));
    const __VLS_64 = __VLS_63({
        description: (__VLS_ctx.t('chat.workspaceEmpty')),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_63));
}
else {
    let __VLS_67;
    /** @ts-ignore @type { | typeof __VLS_components.NScrollbar | typeof __VLS_components.NScrollbar} */
    NScrollbar;
    // @ts-ignore
    const __VLS_68 = __VLS_asFunctionalComponent1(__VLS_67, new __VLS_67({}));
    const __VLS_69 = __VLS_68({}, ...__VLS_functionalComponentArgsRest(__VLS_68));
    const { default: __VLS_72 } = __VLS_70.slots;
    for (const [row] of __VLS_vFor((__VLS_ctx.flatRows))) {
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
            ...{ onClick: (...[$event]) => {
                    if (!!(__VLS_ctx.loadingSummary && __VLS_ctx.flatRows.length === 0 && !('' in __VLS_ctx.childrenByPath)))
                        return;
                    if (!!(__VLS_ctx.isRootEmpty))
                        return;
                    row.node.type === 'dir' ? __VLS_ctx.toggleDir(row.node) : __VLS_ctx.onPickFile(row.node);
                    // @ts-ignore
                    [t, loadingSummary, flatRows, flatRows, childrenByPath, isRootEmpty, toggleDir, onPickFile,];
                } },
            key: (row.node.path),
            ...{ class: "ws-file" },
            ...{ class: ({ 'ws-file--active': __VLS_ctx.editingPath === row.node.path && row.node.type === 'file' }) },
            ...{ style: ({ paddingLeft: 12 + row.depth * 14 + 'px' }) },
        });
        /** @type {__VLS_StyleScopedClasses['ws-file']} */ ;
        /** @type {__VLS_StyleScopedClasses['ws-file--active']} */ ;
        __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
            ...{ class: "ws-file__caret" },
        });
        /** @type {__VLS_StyleScopedClasses['ws-file__caret']} */ ;
        if (row.node.type === 'dir') {
            if (__VLS_ctx.loadingPaths.has(row.node.path)) {
            }
            else if (__VLS_ctx.expandedPaths.has(row.node.path)) {
            }
            else {
            }
        }
        __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
            ...{ class: "ws-file__icon" },
        });
        /** @type {__VLS_StyleScopedClasses['ws-file__icon']} */ ;
        (row.node.type === 'dir' ? '📁' : '📄');
        __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
            ...{ class: "ws-file__name" },
            title: (row.node.path),
        });
        /** @type {__VLS_StyleScopedClasses['ws-file__name']} */ ;
        (row.node.name);
        __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
            ...{ class: "ws-file__size" },
        });
        /** @type {__VLS_StyleScopedClasses['ws-file__size']} */ ;
        if (row.node.type === 'file') {
            (__VLS_ctx.formatSize(row.node.size));
        }
        if (row.node.type === 'file') {
            let __VLS_73;
            /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
            NButton;
            // @ts-ignore
            const __VLS_74 = __VLS_asFunctionalComponent1(__VLS_73, new __VLS_73({
                ...{ 'onClick': {} },
                quaternary: true,
                size: "tiny",
                ...{ class: "ws-file__del" },
            }));
            const __VLS_75 = __VLS_74({
                ...{ 'onClick': {} },
                quaternary: true,
                size: "tiny",
                ...{ class: "ws-file__del" },
            }, ...__VLS_functionalComponentArgsRest(__VLS_74));
            let __VLS_78;
            const __VLS_79 = {
                ...{ click: {} },
                onClick: (...[$event]) => {
                    if (!!(__VLS_ctx.loadingSummary && __VLS_ctx.flatRows.length === 0 && !('' in __VLS_ctx.childrenByPath)))
                        return;
                    if (!!(__VLS_ctx.isRootEmpty))
                        return;
                    if (!(row.node.type === 'file'))
                        return;
                    __VLS_ctx.onDelete(row.node);
                    // @ts-ignore
                    [editingPath, loadingPaths, expandedPaths, formatSize, onDelete,];
                },
            };
            /** @type {__VLS_StyleScopedClasses['ws-file__del']} */ ;
            const { default: __VLS_80 } = __VLS_76.slots;
            // @ts-ignore
            [];
            var __VLS_76;
            var __VLS_77;
        }
        // @ts-ignore
        [];
    }
    // @ts-ignore
    [];
    var __VLS_70;
}
if (__VLS_ctx.editingPath) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "ws-editor" },
    });
    /** @type {__VLS_StyleScopedClasses['ws-editor']} */ ;
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "ws-editor__header" },
    });
    /** @type {__VLS_StyleScopedClasses['ws-editor__header']} */ ;
    let __VLS_81;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_82 = __VLS_asFunctionalComponent1(__VLS_81, new __VLS_81({
        strong: true,
        ...{ style: {} },
        title: (__VLS_ctx.editingPath),
    }));
    const __VLS_83 = __VLS_82({
        strong: true,
        ...{ style: {} },
        title: (__VLS_ctx.editingPath),
    }, ...__VLS_functionalComponentArgsRest(__VLS_82));
    const { default: __VLS_86 } = __VLS_84.slots;
    (__VLS_ctx.editingPath);
    // @ts-ignore
    [editingPath, editingPath, editingPath,];
    var __VLS_84;
    let __VLS_87;
    /** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
    NSpace;
    // @ts-ignore
    const __VLS_88 = __VLS_asFunctionalComponent1(__VLS_87, new __VLS_87({
        size: (6),
        wrap: (false),
    }));
    const __VLS_89 = __VLS_88({
        size: (6),
        wrap: (false),
    }, ...__VLS_functionalComponentArgsRest(__VLS_88));
    const { default: __VLS_92 } = __VLS_90.slots;
    if (__VLS_ctx.editingTooLarge) {
        let __VLS_93;
        /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
        NTag;
        // @ts-ignore
        const __VLS_94 = __VLS_asFunctionalComponent1(__VLS_93, new __VLS_93({
            size: "small",
            type: "warning",
            bordered: (false),
        }));
        const __VLS_95 = __VLS_94({
            size: "small",
            type: "warning",
            bordered: (false),
        }, ...__VLS_functionalComponentArgsRest(__VLS_94));
        const { default: __VLS_98 } = __VLS_96.slots;
        (__VLS_ctx.t('chat.workspaceFileTooLarge'));
        // @ts-ignore
        [t, editingTooLarge,];
        var __VLS_96;
    }
    else if (__VLS_ctx.editingDirty) {
        let __VLS_99;
        /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
        NTag;
        // @ts-ignore
        const __VLS_100 = __VLS_asFunctionalComponent1(__VLS_99, new __VLS_99({
            size: "small",
            type: "warning",
            bordered: (false),
        }));
        const __VLS_101 = __VLS_100({
            size: "small",
            type: "warning",
            bordered: (false),
        }, ...__VLS_functionalComponentArgsRest(__VLS_100));
        const { default: __VLS_104 } = __VLS_102.slots;
        // @ts-ignore
        [editingDirty,];
        var __VLS_102;
    }
    let __VLS_105;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_106 = __VLS_asFunctionalComponent1(__VLS_105, new __VLS_105({
        ...{ 'onClick': {} },
        size: "tiny",
        type: "primary",
        disabled: (!__VLS_ctx.editingDirty || __VLS_ctx.editingTooLarge),
        loading: (__VLS_ctx.saving),
    }));
    const __VLS_107 = __VLS_106({
        ...{ 'onClick': {} },
        size: "tiny",
        type: "primary",
        disabled: (!__VLS_ctx.editingDirty || __VLS_ctx.editingTooLarge),
        loading: (__VLS_ctx.saving),
    }, ...__VLS_functionalComponentArgsRest(__VLS_106));
    let __VLS_110;
    const __VLS_111 = {
        ...{ click: {} },
        onClick: (__VLS_ctx.onSave),
    };
    const { default: __VLS_112 } = __VLS_108.slots;
    (__VLS_ctx.t('common.save'));
    // @ts-ignore
    [t, editingTooLarge, editingDirty, saving, onSave,];
    var __VLS_108;
    var __VLS_109;
    let __VLS_113;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_114 = __VLS_asFunctionalComponent1(__VLS_113, new __VLS_113({
        ...{ 'onClick': {} },
        size: "tiny",
        quaternary: true,
    }));
    const __VLS_115 = __VLS_114({
        ...{ 'onClick': {} },
        size: "tiny",
        quaternary: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_114));
    let __VLS_118;
    const __VLS_119 = {
        ...{ click: {} },
        onClick: (__VLS_ctx.closeEditor),
    };
    const { default: __VLS_120 } = __VLS_116.slots;
    // @ts-ignore
    [closeEditor,];
    var __VLS_116;
    var __VLS_117;
    // @ts-ignore
    [];
    var __VLS_90;
    let __VLS_121;
    /** @ts-ignore @type { | typeof __VLS_components.NInput} */
    NInput;
    // @ts-ignore
    const __VLS_122 = __VLS_asFunctionalComponent1(__VLS_121, new __VLS_121({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.editingContent),
        type: "textarea",
        readonly: (__VLS_ctx.editingTooLarge),
        ...{ class: "ws-editor__textarea" },
    }));
    const __VLS_123 = __VLS_122({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.editingContent),
        type: "textarea",
        readonly: (__VLS_ctx.editingTooLarge),
        ...{ class: "ws-editor__textarea" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_122));
    let __VLS_126;
    const __VLS_127 = {
        ...{ 'update:value': {} },
        'onUpdate:value': (...[$event]) => {
            if (!(__VLS_ctx.editingPath))
                return;
            __VLS_ctx.editingDirty = !__VLS_ctx.editingTooLarge;
            // @ts-ignore
            [editingTooLarge, editingTooLarge, editingDirty, editingContent,];
        },
    };
    /** @type {__VLS_StyleScopedClasses['ws-editor__textarea']} */ ;
    var __VLS_124;
    var __VLS_125;
}
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({
    setup: () => __VLS_exposed,
    __typeProps: {},
});
export default {};
