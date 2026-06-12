import { ref, computed, onMounted, watch } from 'vue';
import { NH2, NButton, NSpace, NTabs, NTabPane, NEmpty, NDrawer, NDrawerContent, NInput, NSelect, NText, NSpin, NGrid, NGi, NCard, NTag, NDivider, } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import * as resourceApi from '@/api/resource';
import { useFactoriesStore } from '@/stores/factories';
import ResourceCard from '@/components/ResourceCard.vue';
import JsonSchemaForm from '@/components/JsonSchemaForm.vue';
const { t } = useI18n();
const factories = useFactoriesStore();
// ── 当前 tab ──
const activeTab = ref('mcp');
// ── MCP Servers ──
const mcpLoading = ref(false);
const mcpList = ref([]);
async function loadMcpServers() {
    mcpLoading.value = true;
    try {
        const resp = await resourceApi.listMcpServers();
        mcpList.value = resp.data.data ?? [];
    }
    catch { /* interceptor handles */ }
    mcpLoading.value = false;
}
// ── Skill Repositories ──
const repoLoading = ref(false);
const repoList = ref([]);
async function loadSkillRepos() {
    repoLoading.value = true;
    try {
        const resp = await resourceApi.listSkillRepos();
        repoList.value = resp.data.data ?? [];
    }
    catch { /* interceptor handles */ }
    repoLoading.value = false;
}
// ── Builtin Tools (HarnessAgent 内省) ──
const builtinToolsLoading = ref(false);
const builtinTools = ref([]);
async function loadBuiltinTools() {
    builtinToolsLoading.value = true;
    try {
        const resp = await resourceApi.listBuiltinTools();
        builtinTools.value = resp.data.data ?? [];
    }
    catch { /* interceptor handles */ }
    builtinToolsLoading.value = false;
}
// ── Drawer ──
const drawerVisible = ref(false);
const drawerMode = ref('mcp');
const isEdit = ref(false);
const saving = ref(false);
const formName = ref('');
const formType = ref('');
const formProps = ref({});
const editingId = ref(null);
const mcpTransport = ref('streamable-http');
const mcpUrl = ref('');
const mcpAuth = ref('none');
const mcpAuthToken = ref('');
const transportOptions = [
    { label: 'HTTP', value: 'streamable-http' },
    { label: '标准 IO', value: 'stdio' },
];
const authOptions = [
    { label: 'None', value: 'none' },
    { label: 'OAuth', value: 'oauth' },
    { label: 'Header', value: 'header' },
];
// 技能仓库类型选项
const skillTypeOptions = computed(() => factories.skillRepoTypes.map((t) => ({ label: t.displayName, value: t.type })));
const currentSkillTypeMeta = computed(() => factories.skillRepoTypes.find((t) => t.type === formType.value));
const currentSkillSchema = computed(() => currentSkillTypeMeta.value?.schema ?? { type: 'object', properties: {} });
function onSkillTypeChange() {
    formProps.value = {};
}
// ── 打开新建/编辑 MCP ──
function openCreateMcp() {
    drawerMode.value = 'mcp';
    isEdit.value = false;
    editingId.value = null;
    formName.value = '';
    mcpTransport.value = 'streamable-http';
    mcpUrl.value = '';
    mcpAuth.value = 'none';
    mcpAuthToken.value = '';
    drawerVisible.value = true;
}
function openEditMcp(item) {
    drawerMode.value = 'mcp';
    isEdit.value = true;
    editingId.value = item.id;
    formName.value = item.name;
    mcpTransport.value = item.type || 'streamable-http';
    mcpUrl.value = item.props.url || '';
    mcpAuth.value = item.props.auth || 'none';
    mcpAuthToken.value = item.props.authToken || '';
    drawerVisible.value = true;
}
// ── 打开新建/编辑 Skill ──
function openCreateSkill() {
    drawerMode.value = 'skill';
    isEdit.value = false;
    editingId.value = null;
    formName.value = '';
    formType.value = '';
    formProps.value = {};
    drawerVisible.value = true;
}
function openEditSkill(item) {
    drawerMode.value = 'skill';
    isEdit.value = true;
    editingId.value = item.id;
    formName.value = item.name;
    formType.value = item.type;
    formProps.value = { ...item.props };
    drawerVisible.value = true;
}
// ── 保存 ──
async function handleSave() {
    if (!formName.value.trim()) {
        window.$message?.error(`${t('common.name')} ${t('common.required')}`);
        return;
    }
    if (drawerMode.value === 'mcp') {
        // MCP: HTTP 时 URL 必填
        if (mcpTransport.value === 'streamable-http' && !mcpUrl.value.trim()) {
            window.$message?.error(`URL ${t('common.required')}`);
            return;
        }
        // 构建 props
        const props = {};
        if (mcpTransport.value === 'streamable-http') {
            props.url = mcpUrl.value;
        }
        if (mcpAuth.value !== 'none') {
            props.auth = mcpAuth.value;
            if (mcpAuthToken.value)
                props.authToken = mcpAuthToken.value;
        }
        const req = { name: formName.value, type: mcpTransport.value, props };
        saving.value = true;
        try {
            if (isEdit.value && editingId.value) {
                await resourceApi.updateMcpServer(editingId.value, req);
            }
            else {
                await resourceApi.createMcpServer(req);
            }
            window.$message?.success(t('tools.saveSuccess'));
            await loadMcpServers();
            drawerVisible.value = false;
        }
        catch { /* interceptor handles */ }
    }
    else {
        // Skill Repository
        if (!formType.value) {
            window.$message?.error(`${t('common.type')} ${t('common.required')}`);
            return;
        }
        const requiredFields = currentSkillSchema.value.required ?? [];
        const schemaProps = currentSkillSchema.value.properties ?? {};
        for (const field of requiredFields) {
            const val = formProps.value[field];
            if (val === undefined || val === null || val === '') {
                const label = schemaProps[field]?.title ?? field;
                window.$message?.error(`${label} ${t('common.required')}`);
                return;
            }
        }
        const req = { name: formName.value, type: formType.value, props: formProps.value };
        saving.value = true;
        try {
            if (isEdit.value && editingId.value) {
                await resourceApi.updateSkillRepo(editingId.value, req);
            }
            else {
                await resourceApi.createSkillRepo(req);
            }
            window.$message?.success(t('tools.saveSuccess'));
            await loadSkillRepos();
            drawerVisible.value = false;
        }
        catch { /* interceptor handles */ }
    }
    saving.value = false;
}
// ── 删除 ──
async function handleDeleteMcp(item) {
    try {
        await resourceApi.deleteMcpServer(item.id);
        window.$message?.success(t('tools.deleteSuccess'));
        await loadMcpServers();
    }
    catch { /* interceptor handles */ }
}
async function handleDeleteSkill(item) {
    try {
        await resourceApi.deleteSkillRepo(item.id);
        window.$message?.success(t('tools.deleteSuccess'));
        await loadSkillRepos();
    }
    catch { /* interceptor handles */ }
}
// ── 初始化 ──
onMounted(() => {
    loadMcpServers();
    loadSkillRepos();
    loadBuiltinTools();
});
watch(activeTab, (tab) => {
    if (tab === 'mcp' && mcpList.value.length === 0)
        loadMcpServers();
    if (tab === 'skill' && repoList.value.length === 0)
        loadSkillRepos();
    if (tab === 'tools' && builtinTools.value.length === 0)
        loadBuiltinTools();
});
const __VLS_ctx = {
    ...{},
    ...{},
};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ style: {} },
});
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
(__VLS_ctx.t('tools.title'));
// @ts-ignore
[t,];
var __VLS_3;
let __VLS_6;
/** @ts-ignore @type { | typeof __VLS_components.NTabs | typeof __VLS_components.NTabs} */
NTabs;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    value: (__VLS_ctx.activeTab),
    type: "line",
    animated: true,
}));
const __VLS_8 = __VLS_7({
    value: (__VLS_ctx.activeTab),
    type: "line",
    animated: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
const { default: __VLS_11 } = __VLS_9.slots;
let __VLS_12;
/** @ts-ignore @type { | typeof __VLS_components.NTabPane | typeof __VLS_components.NTabPane} */
NTabPane;
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent1(__VLS_12, new __VLS_12({
    name: "mcp",
    tab: (__VLS_ctx.t('tools.tabMcp')),
}));
const __VLS_14 = __VLS_13({
    name: "mcp",
    tab: (__VLS_ctx.t('tools.tabMcp')),
}, ...__VLS_functionalComponentArgsRest(__VLS_13));
const { default: __VLS_17 } = __VLS_15.slots;
let __VLS_18;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_19 = __VLS_asFunctionalComponent1(__VLS_18, new __VLS_18({
    justify: "end",
    ...{ style: {} },
}));
const __VLS_20 = __VLS_19({
    justify: "end",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_19));
const { default: __VLS_23 } = __VLS_21.slots;
let __VLS_24;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_25 = __VLS_asFunctionalComponent1(__VLS_24, new __VLS_24({
    ...{ 'onClick': {} },
}));
const __VLS_26 = __VLS_25({
    ...{ 'onClick': {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_25));
let __VLS_29;
const __VLS_30 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.loadMcpServers),
};
const { default: __VLS_31 } = __VLS_27.slots;
(__VLS_ctx.t('common.refresh'));
// @ts-ignore
[t, t, activeTab, loadMcpServers,];
var __VLS_27;
var __VLS_28;
let __VLS_32;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_33 = __VLS_asFunctionalComponent1(__VLS_32, new __VLS_32({
    ...{ 'onClick': {} },
    type: "primary",
}));
const __VLS_34 = __VLS_33({
    ...{ 'onClick': {} },
    type: "primary",
}, ...__VLS_functionalComponentArgsRest(__VLS_33));
let __VLS_37;
const __VLS_38 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.openCreateMcp),
};
const { default: __VLS_39 } = __VLS_35.slots;
(__VLS_ctx.t('tools.add'));
// @ts-ignore
[t, openCreateMcp,];
var __VLS_35;
var __VLS_36;
// @ts-ignore
[];
var __VLS_21;
let __VLS_40;
/** @ts-ignore @type { | typeof __VLS_components.NSpin | typeof __VLS_components.NSpin} */
NSpin;
// @ts-ignore
const __VLS_41 = __VLS_asFunctionalComponent1(__VLS_40, new __VLS_40({
    show: (__VLS_ctx.mcpLoading),
}));
const __VLS_42 = __VLS_41({
    show: (__VLS_ctx.mcpLoading),
}, ...__VLS_functionalComponentArgsRest(__VLS_41));
const { default: __VLS_45 } = __VLS_43.slots;
if (__VLS_ctx.mcpList.length) {
    let __VLS_46;
    /** @ts-ignore @type { | typeof __VLS_components.NGrid | typeof __VLS_components.NGrid} */
    NGrid;
    // @ts-ignore
    const __VLS_47 = __VLS_asFunctionalComponent1(__VLS_46, new __VLS_46({
        cols: (4),
        xGap: (16),
        yGap: (16),
        responsive: "screen",
        itemResponsive: true,
    }));
    const __VLS_48 = __VLS_47({
        cols: (4),
        xGap: (16),
        yGap: (16),
        responsive: "screen",
        itemResponsive: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_47));
    const { default: __VLS_51 } = __VLS_49.slots;
    for (const [item] of __VLS_vFor((__VLS_ctx.mcpList))) {
        let __VLS_52;
        /** @ts-ignore @type { | typeof __VLS_components.NGi | typeof __VLS_components.NGi} */
        NGi;
        // @ts-ignore
        const __VLS_53 = __VLS_asFunctionalComponent1(__VLS_52, new __VLS_52({
            key: (item.id),
            span: (1),
        }));
        const __VLS_54 = __VLS_53({
            key: (item.id),
            span: (1),
        }, ...__VLS_functionalComponentArgsRest(__VLS_53));
        const { default: __VLS_57 } = __VLS_55.slots;
        const __VLS_58 = ResourceCard;
        // @ts-ignore
        const __VLS_59 = __VLS_asFunctionalComponent1(__VLS_58, new __VLS_58({
            ...{ 'onEdit': {} },
            ...{ 'onDelete': {} },
            title: (item.name),
            type: (item.type),
            propsSummary: (item.props),
        }));
        const __VLS_60 = __VLS_59({
            ...{ 'onEdit': {} },
            ...{ 'onDelete': {} },
            title: (item.name),
            type: (item.type),
            propsSummary: (item.props),
        }, ...__VLS_functionalComponentArgsRest(__VLS_59));
        let __VLS_63;
        const __VLS_64 = {
            ...{ edit: {} },
            onEdit: (...[$event]) => {
                if (!(__VLS_ctx.mcpList.length))
                    return;
                __VLS_ctx.openEditMcp(item);
                // @ts-ignore
                [mcpLoading, mcpList, mcpList, openEditMcp,];
            },
            ...{ delete: {} },
            onDelete: (...[$event]) => {
                if (!(__VLS_ctx.mcpList.length))
                    return;
                __VLS_ctx.handleDeleteMcp(item);
                // @ts-ignore
                [handleDeleteMcp,];
            },
        };
        var __VLS_61;
        var __VLS_62;
        // @ts-ignore
        [];
        var __VLS_55;
        // @ts-ignore
        [];
    }
    // @ts-ignore
    [];
    var __VLS_49;
}
else {
    let __VLS_65;
    /** @ts-ignore @type { | typeof __VLS_components.NEmpty} */
    NEmpty;
    // @ts-ignore
    const __VLS_66 = __VLS_asFunctionalComponent1(__VLS_65, new __VLS_65({
        description: (__VLS_ctx.t('tools.noData')),
        ...{ style: {} },
    }));
    const __VLS_67 = __VLS_66({
        description: (__VLS_ctx.t('tools.noData')),
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_66));
}
// @ts-ignore
[t,];
var __VLS_43;
// @ts-ignore
[];
var __VLS_15;
let __VLS_70;
/** @ts-ignore @type { | typeof __VLS_components.NTabPane | typeof __VLS_components.NTabPane} */
NTabPane;
// @ts-ignore
const __VLS_71 = __VLS_asFunctionalComponent1(__VLS_70, new __VLS_70({
    name: "skill",
    tab: (__VLS_ctx.t('tools.tabSkills')),
}));
const __VLS_72 = __VLS_71({
    name: "skill",
    tab: (__VLS_ctx.t('tools.tabSkills')),
}, ...__VLS_functionalComponentArgsRest(__VLS_71));
const { default: __VLS_75 } = __VLS_73.slots;
let __VLS_76;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_77 = __VLS_asFunctionalComponent1(__VLS_76, new __VLS_76({
    justify: "end",
    ...{ style: {} },
}));
const __VLS_78 = __VLS_77({
    justify: "end",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_77));
const { default: __VLS_81 } = __VLS_79.slots;
let __VLS_82;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_83 = __VLS_asFunctionalComponent1(__VLS_82, new __VLS_82({
    ...{ 'onClick': {} },
}));
const __VLS_84 = __VLS_83({
    ...{ 'onClick': {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_83));
let __VLS_87;
const __VLS_88 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.loadSkillRepos),
};
const { default: __VLS_89 } = __VLS_85.slots;
(__VLS_ctx.t('common.refresh'));
// @ts-ignore
[t, t, loadSkillRepos,];
var __VLS_85;
var __VLS_86;
let __VLS_90;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_91 = __VLS_asFunctionalComponent1(__VLS_90, new __VLS_90({
    ...{ 'onClick': {} },
    type: "primary",
}));
const __VLS_92 = __VLS_91({
    ...{ 'onClick': {} },
    type: "primary",
}, ...__VLS_functionalComponentArgsRest(__VLS_91));
let __VLS_95;
const __VLS_96 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.openCreateSkill),
};
const { default: __VLS_97 } = __VLS_93.slots;
(__VLS_ctx.t('tools.add'));
// @ts-ignore
[t, openCreateSkill,];
var __VLS_93;
var __VLS_94;
// @ts-ignore
[];
var __VLS_79;
let __VLS_98;
/** @ts-ignore @type { | typeof __VLS_components.NSpin | typeof __VLS_components.NSpin} */
NSpin;
// @ts-ignore
const __VLS_99 = __VLS_asFunctionalComponent1(__VLS_98, new __VLS_98({
    show: (__VLS_ctx.repoLoading),
}));
const __VLS_100 = __VLS_99({
    show: (__VLS_ctx.repoLoading),
}, ...__VLS_functionalComponentArgsRest(__VLS_99));
const { default: __VLS_103 } = __VLS_101.slots;
if (__VLS_ctx.repoList.length) {
    let __VLS_104;
    /** @ts-ignore @type { | typeof __VLS_components.NGrid | typeof __VLS_components.NGrid} */
    NGrid;
    // @ts-ignore
    const __VLS_105 = __VLS_asFunctionalComponent1(__VLS_104, new __VLS_104({
        cols: (4),
        xGap: (16),
        yGap: (16),
        responsive: "screen",
        itemResponsive: true,
    }));
    const __VLS_106 = __VLS_105({
        cols: (4),
        xGap: (16),
        yGap: (16),
        responsive: "screen",
        itemResponsive: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_105));
    const { default: __VLS_109 } = __VLS_107.slots;
    for (const [item] of __VLS_vFor((__VLS_ctx.repoList))) {
        let __VLS_110;
        /** @ts-ignore @type { | typeof __VLS_components.NGi | typeof __VLS_components.NGi} */
        NGi;
        // @ts-ignore
        const __VLS_111 = __VLS_asFunctionalComponent1(__VLS_110, new __VLS_110({
            key: (item.id),
            span: (1),
        }));
        const __VLS_112 = __VLS_111({
            key: (item.id),
            span: (1),
        }, ...__VLS_functionalComponentArgsRest(__VLS_111));
        const { default: __VLS_115 } = __VLS_113.slots;
        const __VLS_116 = ResourceCard;
        // @ts-ignore
        const __VLS_117 = __VLS_asFunctionalComponent1(__VLS_116, new __VLS_116({
            ...{ 'onEdit': {} },
            ...{ 'onDelete': {} },
            title: (item.name),
            type: (item.type),
            propsSummary: (item.props),
        }));
        const __VLS_118 = __VLS_117({
            ...{ 'onEdit': {} },
            ...{ 'onDelete': {} },
            title: (item.name),
            type: (item.type),
            propsSummary: (item.props),
        }, ...__VLS_functionalComponentArgsRest(__VLS_117));
        let __VLS_121;
        const __VLS_122 = {
            ...{ edit: {} },
            onEdit: (...[$event]) => {
                if (!(__VLS_ctx.repoList.length))
                    return;
                __VLS_ctx.openEditSkill(item);
                // @ts-ignore
                [repoLoading, repoList, repoList, openEditSkill,];
            },
            ...{ delete: {} },
            onDelete: (...[$event]) => {
                if (!(__VLS_ctx.repoList.length))
                    return;
                __VLS_ctx.handleDeleteSkill(item);
                // @ts-ignore
                [handleDeleteSkill,];
            },
        };
        var __VLS_119;
        var __VLS_120;
        // @ts-ignore
        [];
        var __VLS_113;
        // @ts-ignore
        [];
    }
    // @ts-ignore
    [];
    var __VLS_107;
}
else {
    let __VLS_123;
    /** @ts-ignore @type { | typeof __VLS_components.NEmpty} */
    NEmpty;
    // @ts-ignore
    const __VLS_124 = __VLS_asFunctionalComponent1(__VLS_123, new __VLS_123({
        description: (__VLS_ctx.t('tools.noData')),
        ...{ style: {} },
    }));
    const __VLS_125 = __VLS_124({
        description: (__VLS_ctx.t('tools.noData')),
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_124));
}
// @ts-ignore
[t,];
var __VLS_101;
// @ts-ignore
[];
var __VLS_73;
let __VLS_128;
/** @ts-ignore @type { | typeof __VLS_components.NTabPane | typeof __VLS_components.NTabPane} */
NTabPane;
// @ts-ignore
const __VLS_129 = __VLS_asFunctionalComponent1(__VLS_128, new __VLS_128({
    name: "tools",
    tab: (__VLS_ctx.t('tools.tabTools')),
}));
const __VLS_130 = __VLS_129({
    name: "tools",
    tab: (__VLS_ctx.t('tools.tabTools')),
}, ...__VLS_functionalComponentArgsRest(__VLS_129));
const { default: __VLS_133 } = __VLS_131.slots;
let __VLS_134;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_135 = __VLS_asFunctionalComponent1(__VLS_134, new __VLS_134({
    justify: "end",
    ...{ style: {} },
}));
const __VLS_136 = __VLS_135({
    justify: "end",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_135));
const { default: __VLS_139 } = __VLS_137.slots;
let __VLS_140;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_141 = __VLS_asFunctionalComponent1(__VLS_140, new __VLS_140({
    ...{ 'onClick': {} },
}));
const __VLS_142 = __VLS_141({
    ...{ 'onClick': {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_141));
let __VLS_145;
const __VLS_146 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.loadBuiltinTools),
};
const { default: __VLS_147 } = __VLS_143.slots;
(__VLS_ctx.t('common.refresh'));
// @ts-ignore
[t, t, loadBuiltinTools,];
var __VLS_143;
var __VLS_144;
// @ts-ignore
[];
var __VLS_137;
let __VLS_148;
/** @ts-ignore @type { | typeof __VLS_components.NSpin | typeof __VLS_components.NSpin} */
NSpin;
// @ts-ignore
const __VLS_149 = __VLS_asFunctionalComponent1(__VLS_148, new __VLS_148({
    show: (__VLS_ctx.builtinToolsLoading),
}));
const __VLS_150 = __VLS_149({
    show: (__VLS_ctx.builtinToolsLoading),
}, ...__VLS_functionalComponentArgsRest(__VLS_149));
const { default: __VLS_153 } = __VLS_151.slots;
if (__VLS_ctx.builtinTools.length) {
    let __VLS_154;
    /** @ts-ignore @type { | typeof __VLS_components.NGrid | typeof __VLS_components.NGrid} */
    NGrid;
    // @ts-ignore
    const __VLS_155 = __VLS_asFunctionalComponent1(__VLS_154, new __VLS_154({
        cols: (4),
        xGap: (16),
        yGap: (16),
        responsive: "screen",
        itemResponsive: true,
    }));
    const __VLS_156 = __VLS_155({
        cols: (4),
        xGap: (16),
        yGap: (16),
        responsive: "screen",
        itemResponsive: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_155));
    const { default: __VLS_159 } = __VLS_157.slots;
    for (const [tool] of __VLS_vFor((__VLS_ctx.builtinTools))) {
        let __VLS_160;
        /** @ts-ignore @type { | typeof __VLS_components.NGi | typeof __VLS_components.NGi} */
        NGi;
        // @ts-ignore
        const __VLS_161 = __VLS_asFunctionalComponent1(__VLS_160, new __VLS_160({
            key: (tool.type),
            span: (1),
        }));
        const __VLS_162 = __VLS_161({
            key: (tool.type),
            span: (1),
        }, ...__VLS_functionalComponentArgsRest(__VLS_161));
        const { default: __VLS_165 } = __VLS_163.slots;
        let __VLS_166;
        /** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
        NCard;
        // @ts-ignore
        const __VLS_167 = __VLS_asFunctionalComponent1(__VLS_166, new __VLS_166({
            size: "small",
            hoverable: true,
            ...{ style: {} },
        }));
        const __VLS_168 = __VLS_167({
            size: "small",
            hoverable: true,
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_167));
        const { default: __VLS_171 } = __VLS_169.slots;
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
            ...{ style: {} },
        });
        let __VLS_172;
        /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
        NText;
        // @ts-ignore
        const __VLS_173 = __VLS_asFunctionalComponent1(__VLS_172, new __VLS_172({
            strong: true,
            ...{ style: {} },
        }));
        const __VLS_174 = __VLS_173({
            strong: true,
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_173));
        const { default: __VLS_177 } = __VLS_175.slots;
        (tool.displayName);
        // @ts-ignore
        [builtinToolsLoading, builtinTools, builtinTools,];
        var __VLS_175;
        let __VLS_178;
        /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
        NTag;
        // @ts-ignore
        const __VLS_179 = __VLS_asFunctionalComponent1(__VLS_178, new __VLS_178({
            bordered: (false),
            size: "small",
            type: "info",
        }));
        const __VLS_180 = __VLS_179({
            bordered: (false),
            size: "small",
            type: "info",
        }, ...__VLS_functionalComponentArgsRest(__VLS_179));
        const { default: __VLS_183 } = __VLS_181.slots;
        // @ts-ignore
        [];
        var __VLS_181;
        let __VLS_184;
        /** @ts-ignore @type { | typeof __VLS_components.NDivider} */
        NDivider;
        // @ts-ignore
        const __VLS_185 = __VLS_asFunctionalComponent1(__VLS_184, new __VLS_184({
            ...{ style: {} },
        }));
        const __VLS_186 = __VLS_185({
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_185));
        let __VLS_189;
        /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
        NText;
        // @ts-ignore
        const __VLS_190 = __VLS_asFunctionalComponent1(__VLS_189, new __VLS_189({
            depth: "3",
            ...{ style: {} },
        }));
        const __VLS_191 = __VLS_190({
            depth: "3",
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_190));
        const { default: __VLS_194 } = __VLS_192.slots;
        (tool.description);
        // @ts-ignore
        [];
        var __VLS_192;
        // @ts-ignore
        [];
        var __VLS_169;
        // @ts-ignore
        [];
        var __VLS_163;
        // @ts-ignore
        [];
    }
    // @ts-ignore
    [];
    var __VLS_157;
}
else {
    let __VLS_195;
    /** @ts-ignore @type { | typeof __VLS_components.NEmpty} */
    NEmpty;
    // @ts-ignore
    const __VLS_196 = __VLS_asFunctionalComponent1(__VLS_195, new __VLS_195({
        description: (__VLS_ctx.t('tools.noData')),
        ...{ style: {} },
    }));
    const __VLS_197 = __VLS_196({
        description: (__VLS_ctx.t('tools.noData')),
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_196));
}
// @ts-ignore
[t,];
var __VLS_151;
// @ts-ignore
[];
var __VLS_131;
// @ts-ignore
[];
var __VLS_9;
let __VLS_200;
/** @ts-ignore @type { | typeof __VLS_components.NDrawer | typeof __VLS_components.NDrawer} */
NDrawer;
// @ts-ignore
const __VLS_201 = __VLS_asFunctionalComponent1(__VLS_200, new __VLS_200({
    show: (__VLS_ctx.drawerVisible),
    width: (480),
    placement: "right",
}));
const __VLS_202 = __VLS_201({
    show: (__VLS_ctx.drawerVisible),
    width: (480),
    placement: "right",
}, ...__VLS_functionalComponentArgsRest(__VLS_201));
const { default: __VLS_205 } = __VLS_203.slots;
let __VLS_206;
/** @ts-ignore @type { | typeof __VLS_components.NDrawerContent | typeof __VLS_components.NDrawerContent} */
NDrawerContent;
// @ts-ignore
const __VLS_207 = __VLS_asFunctionalComponent1(__VLS_206, new __VLS_206({
    title: (__VLS_ctx.isEdit ? __VLS_ctx.t('tools.editTitle') : __VLS_ctx.t('tools.addTitle')),
    closable: true,
}));
const __VLS_208 = __VLS_207({
    title: (__VLS_ctx.isEdit ? __VLS_ctx.t('tools.editTitle') : __VLS_ctx.t('tools.addTitle')),
    closable: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_207));
const { default: __VLS_211 } = __VLS_209.slots;
let __VLS_212;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_213 = __VLS_asFunctionalComponent1(__VLS_212, new __VLS_212({
    vertical: true,
    size: (16),
    ...{ style: {} },
}));
const __VLS_214 = __VLS_213({
    vertical: true,
    size: (16),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_213));
const { default: __VLS_217 } = __VLS_215.slots;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
let __VLS_218;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_219 = __VLS_asFunctionalComponent1(__VLS_218, new __VLS_218({
    strong: true,
    ...{ style: {} },
}));
const __VLS_220 = __VLS_219({
    strong: true,
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_219));
const { default: __VLS_223 } = __VLS_221.slots;
(__VLS_ctx.t('common.name'));
// @ts-ignore
[t, t, t, drawerVisible, isEdit,];
var __VLS_221;
let __VLS_224;
/** @ts-ignore @type { | typeof __VLS_components.NInput} */
NInput;
// @ts-ignore
const __VLS_225 = __VLS_asFunctionalComponent1(__VLS_224, new __VLS_224({
    value: (__VLS_ctx.formName),
    placeholder: (__VLS_ctx.t('common.name')),
}));
const __VLS_226 = __VLS_225({
    value: (__VLS_ctx.formName),
    placeholder: (__VLS_ctx.t('common.name')),
}, ...__VLS_functionalComponentArgsRest(__VLS_225));
if (__VLS_ctx.drawerMode === 'mcp') {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
    let __VLS_229;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_230 = __VLS_asFunctionalComponent1(__VLS_229, new __VLS_229({
        strong: true,
        ...{ style: {} },
    }));
    const __VLS_231 = __VLS_230({
        strong: true,
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_230));
    const { default: __VLS_234 } = __VLS_232.slots;
    (__VLS_ctx.t('tools.transport'));
    // @ts-ignore
    [t, t, formName, drawerMode,];
    var __VLS_232;
    let __VLS_235;
    /** @ts-ignore @type { | typeof __VLS_components.NSelect} */
    NSelect;
    // @ts-ignore
    const __VLS_236 = __VLS_asFunctionalComponent1(__VLS_235, new __VLS_235({
        value: (__VLS_ctx.mcpTransport),
        options: (__VLS_ctx.transportOptions),
    }));
    const __VLS_237 = __VLS_236({
        value: (__VLS_ctx.mcpTransport),
        options: (__VLS_ctx.transportOptions),
    }, ...__VLS_functionalComponentArgsRest(__VLS_236));
    if (__VLS_ctx.mcpTransport === 'streamable-http') {
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
        let __VLS_240;
        /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
        NText;
        // @ts-ignore
        const __VLS_241 = __VLS_asFunctionalComponent1(__VLS_240, new __VLS_240({
            strong: true,
            ...{ style: {} },
        }));
        const __VLS_242 = __VLS_241({
            strong: true,
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_241));
        const { default: __VLS_245 } = __VLS_243.slots;
        // @ts-ignore
        [mcpTransport, mcpTransport, transportOptions,];
        var __VLS_243;
        let __VLS_246;
        /** @ts-ignore @type { | typeof __VLS_components.NInput} */
        NInput;
        // @ts-ignore
        const __VLS_247 = __VLS_asFunctionalComponent1(__VLS_246, new __VLS_246({
            value: (__VLS_ctx.mcpUrl),
            placeholder: "https://example.com/mcp",
        }));
        const __VLS_248 = __VLS_247({
            value: (__VLS_ctx.mcpUrl),
            placeholder: "https://example.com/mcp",
        }, ...__VLS_functionalComponentArgsRest(__VLS_247));
    }
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
    let __VLS_251;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_252 = __VLS_asFunctionalComponent1(__VLS_251, new __VLS_251({
        strong: true,
        ...{ style: {} },
    }));
    const __VLS_253 = __VLS_252({
        strong: true,
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_252));
    const { default: __VLS_256 } = __VLS_254.slots;
    (__VLS_ctx.t('tools.authentication'));
    // @ts-ignore
    [t, mcpUrl,];
    var __VLS_254;
    let __VLS_257;
    /** @ts-ignore @type { | typeof __VLS_components.NSelect} */
    NSelect;
    // @ts-ignore
    const __VLS_258 = __VLS_asFunctionalComponent1(__VLS_257, new __VLS_257({
        value: (__VLS_ctx.mcpAuth),
        options: (__VLS_ctx.authOptions),
    }));
    const __VLS_259 = __VLS_258({
        value: (__VLS_ctx.mcpAuth),
        options: (__VLS_ctx.authOptions),
    }, ...__VLS_functionalComponentArgsRest(__VLS_258));
    if (__VLS_ctx.mcpAuth !== 'none') {
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
        let __VLS_262;
        /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
        NText;
        // @ts-ignore
        const __VLS_263 = __VLS_asFunctionalComponent1(__VLS_262, new __VLS_262({
            strong: true,
            ...{ style: {} },
        }));
        const __VLS_264 = __VLS_263({
            strong: true,
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_263));
        const { default: __VLS_267 } = __VLS_265.slots;
        // @ts-ignore
        [mcpAuth, mcpAuth, authOptions,];
        var __VLS_265;
        let __VLS_268;
        /** @ts-ignore @type { | typeof __VLS_components.NInput} */
        NInput;
        // @ts-ignore
        const __VLS_269 = __VLS_asFunctionalComponent1(__VLS_268, new __VLS_268({
            value: (__VLS_ctx.mcpAuthToken),
            type: "password",
            showPasswordOn: "click",
            placeholder: (__VLS_ctx.isEdit ? __VLS_ctx.t('model.placeholderNoChange') : 'Token'),
        }));
        const __VLS_270 = __VLS_269({
            value: (__VLS_ctx.mcpAuthToken),
            type: "password",
            showPasswordOn: "click",
            placeholder: (__VLS_ctx.isEdit ? __VLS_ctx.t('model.placeholderNoChange') : 'Token'),
        }, ...__VLS_functionalComponentArgsRest(__VLS_269));
    }
}
else {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
    let __VLS_273;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_274 = __VLS_asFunctionalComponent1(__VLS_273, new __VLS_273({
        strong: true,
        ...{ style: {} },
    }));
    const __VLS_275 = __VLS_274({
        strong: true,
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_274));
    const { default: __VLS_278 } = __VLS_276.slots;
    (__VLS_ctx.t('common.type'));
    // @ts-ignore
    [t, t, isEdit, mcpAuthToken,];
    var __VLS_276;
    let __VLS_279;
    /** @ts-ignore @type { | typeof __VLS_components.NSelect} */
    NSelect;
    // @ts-ignore
    const __VLS_280 = __VLS_asFunctionalComponent1(__VLS_279, new __VLS_279({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.formType),
        options: (__VLS_ctx.skillTypeOptions),
        disabled: (__VLS_ctx.isEdit),
    }));
    const __VLS_281 = __VLS_280({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.formType),
        options: (__VLS_ctx.skillTypeOptions),
        disabled: (__VLS_ctx.isEdit),
    }, ...__VLS_functionalComponentArgsRest(__VLS_280));
    let __VLS_284;
    const __VLS_285 = {
        ...{ 'update:value': {} },
        'onUpdate:value': (__VLS_ctx.onSkillTypeChange),
    };
    var __VLS_282;
    var __VLS_283;
    if (__VLS_ctx.currentSkillTypeMeta) {
        let __VLS_286;
        /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
        NText;
        // @ts-ignore
        const __VLS_287 = __VLS_asFunctionalComponent1(__VLS_286, new __VLS_286({
            depth: "3",
            ...{ style: {} },
        }));
        const __VLS_288 = __VLS_287({
            depth: "3",
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_287));
        const { default: __VLS_291 } = __VLS_289.slots;
        (__VLS_ctx.currentSkillTypeMeta.description);
        // @ts-ignore
        [isEdit, formType, skillTypeOptions, onSkillTypeChange, currentSkillTypeMeta, currentSkillTypeMeta,];
        var __VLS_289;
    }
    if (__VLS_ctx.currentSkillSchema.properties) {
        const __VLS_292 = JsonSchemaForm;
        // @ts-ignore
        const __VLS_293 = __VLS_asFunctionalComponent1(__VLS_292, new __VLS_292({
            modelValue: (__VLS_ctx.formProps),
            schema: (__VLS_ctx.currentSkillSchema),
            isEdit: (__VLS_ctx.isEdit),
        }));
        const __VLS_294 = __VLS_293({
            modelValue: (__VLS_ctx.formProps),
            schema: (__VLS_ctx.currentSkillSchema),
            isEdit: (__VLS_ctx.isEdit),
        }, ...__VLS_functionalComponentArgsRest(__VLS_293));
    }
}
// @ts-ignore
[isEdit, currentSkillSchema, currentSkillSchema, formProps,];
var __VLS_215;
{
    const { footer: __VLS_297 } = __VLS_209.slots;
    let __VLS_298;
    /** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
    NSpace;
    // @ts-ignore
    const __VLS_299 = __VLS_asFunctionalComponent1(__VLS_298, new __VLS_298({}));
    const __VLS_300 = __VLS_299({}, ...__VLS_functionalComponentArgsRest(__VLS_299));
    const { default: __VLS_303 } = __VLS_301.slots;
    let __VLS_304;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_305 = __VLS_asFunctionalComponent1(__VLS_304, new __VLS_304({
        ...{ 'onClick': {} },
    }));
    const __VLS_306 = __VLS_305({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_305));
    let __VLS_309;
    const __VLS_310 = {
        ...{ click: {} },
        onClick: (...[$event]) => {
            __VLS_ctx.drawerVisible = false;
            // @ts-ignore
            [drawerVisible,];
        },
    };
    const { default: __VLS_311 } = __VLS_307.slots;
    (__VLS_ctx.t('common.cancel'));
    // @ts-ignore
    [t,];
    var __VLS_307;
    var __VLS_308;
    let __VLS_312;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_313 = __VLS_asFunctionalComponent1(__VLS_312, new __VLS_312({
        ...{ 'onClick': {} },
        type: "primary",
        loading: (__VLS_ctx.saving),
    }));
    const __VLS_314 = __VLS_313({
        ...{ 'onClick': {} },
        type: "primary",
        loading: (__VLS_ctx.saving),
    }, ...__VLS_functionalComponentArgsRest(__VLS_313));
    let __VLS_317;
    const __VLS_318 = {
        ...{ click: {} },
        onClick: (__VLS_ctx.handleSave),
    };
    const { default: __VLS_319 } = __VLS_315.slots;
    (__VLS_ctx.t('common.save'));
    // @ts-ignore
    [t, saving, handleSave,];
    var __VLS_315;
    var __VLS_316;
    // @ts-ignore
    [];
    var __VLS_301;
    // @ts-ignore
    [];
}
// @ts-ignore
[];
var __VLS_209;
// @ts-ignore
[];
var __VLS_203;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
