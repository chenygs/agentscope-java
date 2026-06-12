import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NButton, NInput, NInputNumber, NSelect, NSpace, NH2, NTabs, NTabPane, NText, NCard, NTag, NDivider, NFormItem, NForm, NDynamicTags, NEmpty, NGrid, NGi, NSwitch, NUpload, useMessage, } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import * as agentApi from '@/api/agent';
import * as resourceApi from '@/api/resource';
import { useFactoriesStore } from '@/stores/factories';
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const message = useMessage();
const factories = useFactoriesStore();
// ── 路由参数 ──
const agentParamId = computed(() => {
    const id = route.params.id;
    return id ? Number(id) : null;
});
const isEdit = computed(() => !!agentParamId.value);
const saving = ref(false);
// ── 模型选项 ──
const modelOptions = ref([]);
async function loadModelOptions() {
    try {
        const resp = await resourceApi.listModels();
        modelOptions.value = (resp.data.data ?? []).map((m) => ({
            label: `${m.name} (${m.type})`,
            value: m.id,
        }));
    }
    catch { /* ignore */ }
}
// ── 可用工具列表（builtin-tools）──
const builtinTools = ref([]);
async function loadBuiltinTools() {
    try {
        const resp = await resourceApi.listBuiltinTools();
        builtinTools.value = resp.data.data ?? [];
    }
    catch { /* ignore */ }
}
// ── 可用技能仓库列表 ──
const allSkillRepos = ref([]);
async function loadSkillRepos() {
    try {
        const resp = await resourceApi.listSkillRepos();
        allSkillRepos.value = resp.data.data ?? [];
    }
    catch { /* ignore */ }
}
onMounted(async () => {
    await Promise.all([loadModelOptions(), loadBuiltinTools(), loadSkillRepos(), factories.loadAll()]);
    await loadAgent();
    loadWorkspaceSkills();
});
// ── 表单状态 ──
const formAgentId = ref('');
const formName = ref('');
const formDescription = ref('');
const formSysPrompt = ref('');
const formAgentType = ref('HARNESS');
const formModelId = ref(null);
const formMaxIters = ref(10);
const formToolSpecs = ref([]);
const formSkillRepos = ref([]);
const formMiddlewareSpecs = ref([]);
const formSubagentRefs = ref([]);
// ── 加载 Agent（编辑模式）──
async function loadAgent() {
    if (!agentParamId.value)
        return;
    try {
        const resp = await agentApi.getAgent(agentParamId.value);
        const a = resp.data.data;
        if (!a)
            return;
        formAgentId.value = a.agentId;
        formName.value = a.name ?? '';
        formDescription.value = a.description ?? '';
        formSysPrompt.value = a.sysPrompt ?? '';
        formAgentType.value = a.agentType;
        formModelId.value = a.defaultModelProviderId;
        formMaxIters.value = a.maxIters ?? 10;
        formToolSpecs.value = a.toolSpecs ?? [];
        formSkillRepos.value = a.skillRepositories ?? [];
        formMiddlewareSpecs.value = a.middlewareSpecs ?? [];
        formSubagentRefs.value = a.subagentRefs ?? [];
    }
    catch { /* interceptor */ }
}
// ── 工具选择 ──
const activeToolTypes = computed(() => new Set(formToolSpecs.value.map(s => s.type)));
function toggleTool(tool) {
    const idx = formToolSpecs.value.findIndex(s => s.type === tool.type);
    if (idx >= 0) {
        formToolSpecs.value.splice(idx, 1);
    }
    else {
        formToolSpecs.value.push({ type: tool.type, props: {} });
    }
}
// ── 技能仓库选择（从已创建的技能数据中选）──
const activeSkillRepoIds = computed(() => {
    const set = new Set();
    for (const spec of formSkillRepos.value) {
        const repo = allSkillRepos.value.find(r => r.type === spec.type && JSON.stringify(r.props) === JSON.stringify(spec.props));
        if (repo)
            set.add(repo.id);
    }
    return set;
});
function toggleSkillRepo(repo) {
    const idx = formSkillRepos.value.findIndex(s => s.type === repo.type && JSON.stringify(s.props) === JSON.stringify(repo.props));
    if (idx >= 0) {
        formSkillRepos.value.splice(idx, 1);
    }
    else {
        formSkillRepos.value.push({ type: repo.type, props: { ...repo.props } });
    }
}
// ── 新建技能仓库 ──
const showSkillForm = ref(false);
const newSkillName = ref('');
const newSkillType = ref(null);
const newSkillProps = ref({});
const creatingSkill = ref(false);
const skillTypeOptions = computed(() => factories.skillRepoTypes.map(s => ({ label: `${s.displayName} — ${s.description}`, value: s.type })));
function onNewSkillTypeChange() {
    newSkillProps.value = {};
}
async function createSkillRepo() {
    if (!newSkillName.value.trim() || !newSkillType.value) {
        message.warning(t('agent.requiredFields'));
        return;
    }
    creatingSkill.value = true;
    try {
        const props = {};
        for (const [k, v] of Object.entries(newSkillProps.value)) {
            if (v.trim())
                props[k] = v.trim();
        }
        const resp = await resourceApi.createSkillRepo({ name: newSkillName.value, type: newSkillType.value, props });
        message.success(t('common.saveSuccess'));
        // 重新加载并自动勾选
        await loadSkillRepos();
        const created = resp.data.data;
        if (created) {
            formSkillRepos.value.push({ type: created.type, props: { ...created.props } });
        }
        // 重置表单
        newSkillName.value = '';
        newSkillType.value = null;
        newSkillProps.value = {};
        showSkillForm.value = false;
    }
    catch { /* interceptor */ }
    finally {
        creatingSkill.value = false;
    }
}
// ── 上传技能压缩包 ──
const uploadingSkills = ref(false);
const uploadSkillName = ref('');
const uploadFile = ref(null);
// ── workspace 技能列表 ──
const workspaceSkills = ref([]);
async function loadWorkspaceSkills() {
    if (!agentParamId.value)
        return;
    try {
        const resp = await agentApi.listWorkspaceSkills(agentParamId.value);
        workspaceSkills.value = resp.data.data ?? [];
    }
    catch { /* ignore */ }
}
async function deleteWorkspaceSkill(name) {
    if (!agentParamId.value)
        return;
    try {
        await agentApi.deleteWorkspaceSkill(agentParamId.value, name);
        message.success(t('common.saveSuccess'));
        await loadWorkspaceSkills();
    }
    catch { /* interceptor */ }
}
async function handleSkillUpload() {
    if (!agentParamId.value) {
        message.warning(t('agent.saveBeforeUpload'));
        return;
    }
    if (!uploadSkillName.value.trim()) {
        message.warning(t('agent.skillNameRequired'));
        return;
    }
    if (!uploadFile.value) {
        message.warning(t('agent.selectZipFile'));
        return;
    }
    uploadingSkills.value = true;
    try {
        await agentApi.uploadSkill(agentParamId.value, uploadSkillName.value.trim(), uploadFile.value);
        message.success(t('agent.uploadSuccess', { name: uploadSkillName.value.trim() }));
        // Reset & reload
        uploadSkillName.value = '';
        uploadFile.value = null;
        await loadWorkspaceSkills();
    }
    catch { /* interceptor */ }
    finally {
        uploadingSkills.value = false;
    }
}
// ── 中间件选择 ──
const selectedMwType = ref(null);
const mwTypeOptions = computed(() => factories.middlewareTypes.map(m => ({ label: `${m.displayName} — ${m.description}`, value: m.type })));
function addMiddleware() {
    if (!selectedMwType.value)
        return;
    if (formMiddlewareSpecs.value.some(s => s.type === selectedMwType.value)) {
        message.warning(t('agent.alreadyAdded'));
        return;
    }
    formMiddlewareSpecs.value.push({ type: selectedMwType.value, props: {} });
    selectedMwType.value = null;
}
function removeMiddleware(idx) { formMiddlewareSpecs.value.splice(idx, 1); }
// ── Props JSON 辅助 ──
function propsToStr(props) {
    if (!props || Object.keys(props).length === 0)
        return '';
    return JSON.stringify(props, null, 2);
}
function updateProps(list, idx, val) {
    try {
        list[idx].props = val.trim() ? JSON.parse(val) : {};
    }
    catch { /* editing */ }
}
// ── 保存 ──
function buildReq() {
    return {
        agentId: formAgentId.value,
        name: formName.value,
        description: formDescription.value || undefined,
        sysPrompt: formSysPrompt.value || undefined,
        agentType: formAgentType.value,
        defaultModelProviderId: formModelId.value,
        maxIters: formMaxIters.value ?? undefined,
        toolSpecs: formToolSpecs.value.length > 0 ? formToolSpecs.value : undefined,
        skillRepositories: formSkillRepos.value.length > 0 ? formSkillRepos.value : undefined,
        middlewareSpecs: formMiddlewareSpecs.value.length > 0 ? formMiddlewareSpecs.value : undefined,
        subagentRefs: formSubagentRefs.value.length > 0 ? formSubagentRefs.value : undefined,
    };
}
async function handleSave() {
    if (!formAgentId.value || !formName.value || !formModelId.value) {
        message.warning(t('agent.requiredFields'));
        return;
    }
    saving.value = true;
    try {
        const req = buildReq();
        if (isEdit.value && agentParamId.value) {
            await agentApi.updateAgent(agentParamId.value, req);
            message.success(t('common.saveSuccess'));
        }
        else {
            const resp = await agentApi.createAgent(req);
            message.success(t('common.saveSuccess'));
            const newId = resp.data.data?.id;
            if (newId)
                router.replace({ name: 'AgentEdit', params: { id: newId } });
        }
    }
    catch { /* interceptor */ }
    finally {
        saving.value = false;
    }
}
function goBack() { router.push({ name: 'Agents' }); }
const agentTypeOptions = [
    { label: 'Harness', value: 'HARNESS' },
    { label: 'ReAct', value: 'REACT' },
];
const __VLS_ctx = {
    ...{},
    ...{},
};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    justify: "space-between",
    align: "center",
    ...{ style: {} },
}));
const __VLS_2 = __VLS_1({
    justify: "space-between",
    align: "center",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
const { default: __VLS_5 } = __VLS_3.slots;
let __VLS_6;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    align: "center",
    size: (12),
}));
const __VLS_8 = __VLS_7({
    align: "center",
    size: (12),
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
const { default: __VLS_11 } = __VLS_9.slots;
let __VLS_12;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent1(__VLS_12, new __VLS_12({
    ...{ 'onClick': {} },
    quaternary: true,
}));
const __VLS_14 = __VLS_13({
    ...{ 'onClick': {} },
    quaternary: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_13));
let __VLS_17;
const __VLS_18 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.goBack),
};
const { default: __VLS_19 } = __VLS_15.slots;
(__VLS_ctx.t('common.back'));
// @ts-ignore
[goBack, t,];
var __VLS_15;
var __VLS_16;
let __VLS_20;
/** @ts-ignore @type { | typeof __VLS_components.NH2 | typeof __VLS_components.NH2} */
NH2;
// @ts-ignore
const __VLS_21 = __VLS_asFunctionalComponent1(__VLS_20, new __VLS_20({
    ...{ style: {} },
}));
const __VLS_22 = __VLS_21({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_21));
const { default: __VLS_25 } = __VLS_23.slots;
(__VLS_ctx.isEdit ? __VLS_ctx.formName || __VLS_ctx.t('agent.edit') : __VLS_ctx.t('agent.create'));
// @ts-ignore
[t, t, isEdit, formName,];
var __VLS_23;
if (__VLS_ctx.isEdit) {
    let __VLS_26;
    /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
    NTag;
    // @ts-ignore
    const __VLS_27 = __VLS_asFunctionalComponent1(__VLS_26, new __VLS_26({
        size: "small",
        type: (__VLS_ctx.formAgentType === 'HARNESS' ? 'info' : 'success'),
    }));
    const __VLS_28 = __VLS_27({
        size: "small",
        type: (__VLS_ctx.formAgentType === 'HARNESS' ? 'info' : 'success'),
    }, ...__VLS_functionalComponentArgsRest(__VLS_27));
    const { default: __VLS_31 } = __VLS_29.slots;
    (__VLS_ctx.formAgentType);
    // @ts-ignore
    [isEdit, formAgentType, formAgentType,];
    var __VLS_29;
}
// @ts-ignore
[];
var __VLS_9;
let __VLS_32;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_33 = __VLS_asFunctionalComponent1(__VLS_32, new __VLS_32({}));
const __VLS_34 = __VLS_33({}, ...__VLS_functionalComponentArgsRest(__VLS_33));
const { default: __VLS_37 } = __VLS_35.slots;
let __VLS_38;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_39 = __VLS_asFunctionalComponent1(__VLS_38, new __VLS_38({
    ...{ 'onClick': {} },
}));
const __VLS_40 = __VLS_39({
    ...{ 'onClick': {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_39));
let __VLS_43;
const __VLS_44 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.goBack),
};
const { default: __VLS_45 } = __VLS_41.slots;
(__VLS_ctx.t('common.cancel'));
// @ts-ignore
[goBack, t,];
var __VLS_41;
var __VLS_42;
let __VLS_46;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_47 = __VLS_asFunctionalComponent1(__VLS_46, new __VLS_46({
    ...{ 'onClick': {} },
    type: "primary",
    loading: (__VLS_ctx.saving),
}));
const __VLS_48 = __VLS_47({
    ...{ 'onClick': {} },
    type: "primary",
    loading: (__VLS_ctx.saving),
}, ...__VLS_functionalComponentArgsRest(__VLS_47));
let __VLS_51;
const __VLS_52 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.handleSave),
};
const { default: __VLS_53 } = __VLS_49.slots;
(__VLS_ctx.t('common.save'));
// @ts-ignore
[t, saving, handleSave,];
var __VLS_49;
var __VLS_50;
// @ts-ignore
[];
var __VLS_35;
// @ts-ignore
[];
var __VLS_3;
let __VLS_54;
/** @ts-ignore @type { | typeof __VLS_components.NTabs | typeof __VLS_components.NTabs} */
NTabs;
// @ts-ignore
const __VLS_55 = __VLS_asFunctionalComponent1(__VLS_54, new __VLS_54({
    type: "line",
    animated: true,
}));
const __VLS_56 = __VLS_55({
    type: "line",
    animated: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_55));
const { default: __VLS_59 } = __VLS_57.slots;
let __VLS_60;
/** @ts-ignore @type { | typeof __VLS_components.NTabPane | typeof __VLS_components.NTabPane} */
NTabPane;
// @ts-ignore
const __VLS_61 = __VLS_asFunctionalComponent1(__VLS_60, new __VLS_60({
    name: "basic",
    tab: (__VLS_ctx.t('agent.tabBasic')),
}));
const __VLS_62 = __VLS_61({
    name: "basic",
    tab: (__VLS_ctx.t('agent.tabBasic')),
}, ...__VLS_functionalComponentArgsRest(__VLS_61));
const { default: __VLS_65 } = __VLS_63.slots;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ style: {} },
});
let __VLS_66;
/** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
NCard;
// @ts-ignore
const __VLS_67 = __VLS_asFunctionalComponent1(__VLS_66, new __VLS_66({
    size: "small",
    title: (__VLS_ctx.t('agent.agentInfo')),
    ...{ style: {} },
}));
const __VLS_68 = __VLS_67({
    size: "small",
    title: (__VLS_ctx.t('agent.agentInfo')),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_67));
const { default: __VLS_71 } = __VLS_69.slots;
let __VLS_72;
/** @ts-ignore @type { | typeof __VLS_components.NForm | typeof __VLS_components.NForm} */
NForm;
// @ts-ignore
const __VLS_73 = __VLS_asFunctionalComponent1(__VLS_72, new __VLS_72({
    labelPlacement: "left",
    labelWidth: "120",
    showFeedback: (false),
}));
const __VLS_74 = __VLS_73({
    labelPlacement: "left",
    labelWidth: "120",
    showFeedback: (false),
}, ...__VLS_functionalComponentArgsRest(__VLS_73));
const { default: __VLS_77 } = __VLS_75.slots;
let __VLS_78;
/** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
NFormItem;
// @ts-ignore
const __VLS_79 = __VLS_asFunctionalComponent1(__VLS_78, new __VLS_78({
    label: (__VLS_ctx.t('agent.agentId')),
}));
const __VLS_80 = __VLS_79({
    label: (__VLS_ctx.t('agent.agentId')),
}, ...__VLS_functionalComponentArgsRest(__VLS_79));
const { default: __VLS_83 } = __VLS_81.slots;
let __VLS_84;
/** @ts-ignore @type { | typeof __VLS_components.NInput} */
NInput;
// @ts-ignore
const __VLS_85 = __VLS_asFunctionalComponent1(__VLS_84, new __VLS_84({
    value: (__VLS_ctx.formAgentId),
    disabled: (__VLS_ctx.isEdit),
    placeholder: "my-agent",
}));
const __VLS_86 = __VLS_85({
    value: (__VLS_ctx.formAgentId),
    disabled: (__VLS_ctx.isEdit),
    placeholder: "my-agent",
}, ...__VLS_functionalComponentArgsRest(__VLS_85));
// @ts-ignore
[t, t, t, isEdit, formAgentId,];
var __VLS_81;
let __VLS_89;
/** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
NFormItem;
// @ts-ignore
const __VLS_90 = __VLS_asFunctionalComponent1(__VLS_89, new __VLS_89({
    label: (__VLS_ctx.t('common.name')),
}));
const __VLS_91 = __VLS_90({
    label: (__VLS_ctx.t('common.name')),
}, ...__VLS_functionalComponentArgsRest(__VLS_90));
const { default: __VLS_94 } = __VLS_92.slots;
let __VLS_95;
/** @ts-ignore @type { | typeof __VLS_components.NInput} */
NInput;
// @ts-ignore
const __VLS_96 = __VLS_asFunctionalComponent1(__VLS_95, new __VLS_95({
    value: (__VLS_ctx.formName),
}));
const __VLS_97 = __VLS_96({
    value: (__VLS_ctx.formName),
}, ...__VLS_functionalComponentArgsRest(__VLS_96));
// @ts-ignore
[t, formName,];
var __VLS_92;
let __VLS_100;
/** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
NFormItem;
// @ts-ignore
const __VLS_101 = __VLS_asFunctionalComponent1(__VLS_100, new __VLS_100({
    label: (__VLS_ctx.t('common.description')),
}));
const __VLS_102 = __VLS_101({
    label: (__VLS_ctx.t('common.description')),
}, ...__VLS_functionalComponentArgsRest(__VLS_101));
const { default: __VLS_105 } = __VLS_103.slots;
let __VLS_106;
/** @ts-ignore @type { | typeof __VLS_components.NInput} */
NInput;
// @ts-ignore
const __VLS_107 = __VLS_asFunctionalComponent1(__VLS_106, new __VLS_106({
    value: (__VLS_ctx.formDescription),
    type: "textarea",
    rows: (2),
}));
const __VLS_108 = __VLS_107({
    value: (__VLS_ctx.formDescription),
    type: "textarea",
    rows: (2),
}, ...__VLS_functionalComponentArgsRest(__VLS_107));
// @ts-ignore
[t, formDescription,];
var __VLS_103;
// @ts-ignore
[];
var __VLS_75;
// @ts-ignore
[];
var __VLS_69;
let __VLS_111;
/** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
NCard;
// @ts-ignore
const __VLS_112 = __VLS_asFunctionalComponent1(__VLS_111, new __VLS_111({
    size: "small",
    title: (__VLS_ctx.t('agent.runtimeConfig')),
    ...{ style: {} },
}));
const __VLS_113 = __VLS_112({
    size: "small",
    title: (__VLS_ctx.t('agent.runtimeConfig')),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_112));
const { default: __VLS_116 } = __VLS_114.slots;
let __VLS_117;
/** @ts-ignore @type { | typeof __VLS_components.NForm | typeof __VLS_components.NForm} */
NForm;
// @ts-ignore
const __VLS_118 = __VLS_asFunctionalComponent1(__VLS_117, new __VLS_117({
    labelPlacement: "left",
    labelWidth: "120",
    showFeedback: (false),
}));
const __VLS_119 = __VLS_118({
    labelPlacement: "left",
    labelWidth: "120",
    showFeedback: (false),
}, ...__VLS_functionalComponentArgsRest(__VLS_118));
const { default: __VLS_122 } = __VLS_120.slots;
let __VLS_123;
/** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
NFormItem;
// @ts-ignore
const __VLS_124 = __VLS_asFunctionalComponent1(__VLS_123, new __VLS_123({
    label: (__VLS_ctx.t('agent.agentType')),
}));
const __VLS_125 = __VLS_124({
    label: (__VLS_ctx.t('agent.agentType')),
}, ...__VLS_functionalComponentArgsRest(__VLS_124));
const { default: __VLS_128 } = __VLS_126.slots;
let __VLS_129;
/** @ts-ignore @type { | typeof __VLS_components.NSelect} */
NSelect;
// @ts-ignore
const __VLS_130 = __VLS_asFunctionalComponent1(__VLS_129, new __VLS_129({
    value: (__VLS_ctx.formAgentType),
    options: (__VLS_ctx.agentTypeOptions),
}));
const __VLS_131 = __VLS_130({
    value: (__VLS_ctx.formAgentType),
    options: (__VLS_ctx.agentTypeOptions),
}, ...__VLS_functionalComponentArgsRest(__VLS_130));
// @ts-ignore
[t, t, formAgentType, agentTypeOptions,];
var __VLS_126;
let __VLS_134;
/** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
NFormItem;
// @ts-ignore
const __VLS_135 = __VLS_asFunctionalComponent1(__VLS_134, new __VLS_134({
    label: (__VLS_ctx.t('agent.model')),
}));
const __VLS_136 = __VLS_135({
    label: (__VLS_ctx.t('agent.model')),
}, ...__VLS_functionalComponentArgsRest(__VLS_135));
const { default: __VLS_139 } = __VLS_137.slots;
let __VLS_140;
/** @ts-ignore @type { | typeof __VLS_components.NSelect} */
NSelect;
// @ts-ignore
const __VLS_141 = __VLS_asFunctionalComponent1(__VLS_140, new __VLS_140({
    value: (__VLS_ctx.formModelId),
    options: (__VLS_ctx.modelOptions),
    placeholder: (__VLS_ctx.t('agent.selectModel')),
    filterable: true,
}));
const __VLS_142 = __VLS_141({
    value: (__VLS_ctx.formModelId),
    options: (__VLS_ctx.modelOptions),
    placeholder: (__VLS_ctx.t('agent.selectModel')),
    filterable: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_141));
// @ts-ignore
[t, t, formModelId, modelOptions,];
var __VLS_137;
let __VLS_145;
/** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
NFormItem;
// @ts-ignore
const __VLS_146 = __VLS_asFunctionalComponent1(__VLS_145, new __VLS_145({
    label: (__VLS_ctx.t('agent.maxIters')),
}));
const __VLS_147 = __VLS_146({
    label: (__VLS_ctx.t('agent.maxIters')),
}, ...__VLS_functionalComponentArgsRest(__VLS_146));
const { default: __VLS_150 } = __VLS_148.slots;
let __VLS_151;
/** @ts-ignore @type { | typeof __VLS_components.NInputNumber} */
NInputNumber;
// @ts-ignore
const __VLS_152 = __VLS_asFunctionalComponent1(__VLS_151, new __VLS_151({
    value: (__VLS_ctx.formMaxIters),
    min: (1),
    max: (100),
    ...{ style: {} },
}));
const __VLS_153 = __VLS_152({
    value: (__VLS_ctx.formMaxIters),
    min: (1),
    max: (100),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_152));
// @ts-ignore
[t, formMaxIters,];
var __VLS_148;
// @ts-ignore
[];
var __VLS_120;
// @ts-ignore
[];
var __VLS_114;
let __VLS_156;
/** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
NCard;
// @ts-ignore
const __VLS_157 = __VLS_asFunctionalComponent1(__VLS_156, new __VLS_156({
    size: "small",
    title: (__VLS_ctx.t('agent.sysPrompt')),
}));
const __VLS_158 = __VLS_157({
    size: "small",
    title: (__VLS_ctx.t('agent.sysPrompt')),
}, ...__VLS_functionalComponentArgsRest(__VLS_157));
const { default: __VLS_161 } = __VLS_159.slots;
let __VLS_162;
/** @ts-ignore @type { | typeof __VLS_components.NInput} */
NInput;
// @ts-ignore
const __VLS_163 = __VLS_asFunctionalComponent1(__VLS_162, new __VLS_162({
    value: (__VLS_ctx.formSysPrompt),
    type: "textarea",
    rows: (10),
    placeholder: (__VLS_ctx.t('agent.sysPromptPlaceholder')),
}));
const __VLS_164 = __VLS_163({
    value: (__VLS_ctx.formSysPrompt),
    type: "textarea",
    rows: (10),
    placeholder: (__VLS_ctx.t('agent.sysPromptPlaceholder')),
}, ...__VLS_functionalComponentArgsRest(__VLS_163));
// @ts-ignore
[t, t, formSysPrompt,];
var __VLS_159;
// @ts-ignore
[];
var __VLS_63;
let __VLS_167;
/** @ts-ignore @type { | typeof __VLS_components.NTabPane | typeof __VLS_components.NTabPane} */
NTabPane;
// @ts-ignore
const __VLS_168 = __VLS_asFunctionalComponent1(__VLS_167, new __VLS_167({
    name: "tools",
    tab: (__VLS_ctx.t('agent.tabTools')),
}));
const __VLS_169 = __VLS_168({
    name: "tools",
    tab: (__VLS_ctx.t('agent.tabTools')),
}, ...__VLS_functionalComponentArgsRest(__VLS_168));
const { default: __VLS_172 } = __VLS_170.slots;
let __VLS_173;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_174 = __VLS_asFunctionalComponent1(__VLS_173, new __VLS_173({
    depth: "3",
    ...{ style: {} },
}));
const __VLS_175 = __VLS_174({
    depth: "3",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_174));
const { default: __VLS_178 } = __VLS_176.slots;
(__VLS_ctx.t('agent.toolsHint'));
// @ts-ignore
[t, t,];
var __VLS_176;
let __VLS_179;
/** @ts-ignore @type { | typeof __VLS_components.NGrid | typeof __VLS_components.NGrid} */
NGrid;
// @ts-ignore
const __VLS_180 = __VLS_asFunctionalComponent1(__VLS_179, new __VLS_179({
    cols: (4),
    xGap: (12),
    yGap: (12),
    responsive: "screen",
    itemResponsive: true,
}));
const __VLS_181 = __VLS_180({
    cols: (4),
    xGap: (12),
    yGap: (12),
    responsive: "screen",
    itemResponsive: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_180));
const { default: __VLS_184 } = __VLS_182.slots;
for (const [tool] of __VLS_vFor((__VLS_ctx.builtinTools))) {
    let __VLS_185;
    /** @ts-ignore @type { | typeof __VLS_components.NGi | typeof __VLS_components.NGi} */
    NGi;
    // @ts-ignore
    const __VLS_186 = __VLS_asFunctionalComponent1(__VLS_185, new __VLS_185({
        key: (tool.type),
        span: (1),
    }));
    const __VLS_187 = __VLS_186({
        key: (tool.type),
        span: (1),
    }, ...__VLS_functionalComponentArgsRest(__VLS_186));
    const { default: __VLS_190 } = __VLS_188.slots;
    let __VLS_191;
    /** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
    NCard;
    // @ts-ignore
    const __VLS_192 = __VLS_asFunctionalComponent1(__VLS_191, new __VLS_191({
        ...{ 'onClick': {} },
        size: "small",
        hoverable: true,
        ...{ class: ({ 'tool-card-active': __VLS_ctx.activeToolTypes.has(tool.type) }) },
        ...{ style: {} },
    }));
    const __VLS_193 = __VLS_192({
        ...{ 'onClick': {} },
        size: "small",
        hoverable: true,
        ...{ class: ({ 'tool-card-active': __VLS_ctx.activeToolTypes.has(tool.type) }) },
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_192));
    let __VLS_196;
    const __VLS_197 = {
        ...{ click: {} },
        onClick: (...[$event]) => {
            __VLS_ctx.toggleTool(tool);
            // @ts-ignore
            [builtinTools, activeToolTypes, toggleTool,];
        },
    };
    /** @type {__VLS_StyleScopedClasses['tool-card-active']} */ ;
    const { default: __VLS_198 } = __VLS_194.slots;
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ style: {} },
    });
    let __VLS_199;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_200 = __VLS_asFunctionalComponent1(__VLS_199, new __VLS_199({
        strong: true,
        ...{ style: {} },
    }));
    const __VLS_201 = __VLS_200({
        strong: true,
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_200));
    const { default: __VLS_204 } = __VLS_202.slots;
    (tool.displayName);
    // @ts-ignore
    [];
    var __VLS_202;
    let __VLS_205;
    /** @ts-ignore @type { | typeof __VLS_components.NSwitch} */
    NSwitch;
    // @ts-ignore
    const __VLS_206 = __VLS_asFunctionalComponent1(__VLS_205, new __VLS_205({
        value: (__VLS_ctx.activeToolTypes.has(tool.type)),
        size: "small",
    }));
    const __VLS_207 = __VLS_206({
        value: (__VLS_ctx.activeToolTypes.has(tool.type)),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_206));
    let __VLS_210;
    /** @ts-ignore @type { | typeof __VLS_components.NDivider} */
    NDivider;
    // @ts-ignore
    const __VLS_211 = __VLS_asFunctionalComponent1(__VLS_210, new __VLS_210({
        ...{ style: {} },
    }));
    const __VLS_212 = __VLS_211({
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_211));
    let __VLS_215;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_216 = __VLS_asFunctionalComponent1(__VLS_215, new __VLS_215({
        depth: "3",
        ...{ style: {} },
    }));
    const __VLS_217 = __VLS_216({
        depth: "3",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_216));
    const { default: __VLS_220 } = __VLS_218.slots;
    (tool.description);
    // @ts-ignore
    [activeToolTypes,];
    var __VLS_218;
    // @ts-ignore
    [];
    var __VLS_194;
    var __VLS_195;
    // @ts-ignore
    [];
    var __VLS_188;
    // @ts-ignore
    [];
}
// @ts-ignore
[];
var __VLS_182;
if (!__VLS_ctx.builtinTools.length) {
    let __VLS_221;
    /** @ts-ignore @type { | typeof __VLS_components.NEmpty} */
    NEmpty;
    // @ts-ignore
    const __VLS_222 = __VLS_asFunctionalComponent1(__VLS_221, new __VLS_221({
        description: (__VLS_ctx.t('agent.noTools')),
        ...{ style: {} },
    }));
    const __VLS_223 = __VLS_222({
        description: (__VLS_ctx.t('agent.noTools')),
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_222));
}
// @ts-ignore
[t, builtinTools,];
var __VLS_170;
let __VLS_226;
/** @ts-ignore @type { | typeof __VLS_components.NTabPane | typeof __VLS_components.NTabPane} */
NTabPane;
// @ts-ignore
const __VLS_227 = __VLS_asFunctionalComponent1(__VLS_226, new __VLS_226({
    name: "skills",
    tab: (__VLS_ctx.t('agent.tabSkills')),
}));
const __VLS_228 = __VLS_227({
    name: "skills",
    tab: (__VLS_ctx.t('agent.tabSkills')),
}, ...__VLS_functionalComponentArgsRest(__VLS_227));
const { default: __VLS_231 } = __VLS_229.slots;
let __VLS_232;
/** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
NCard;
// @ts-ignore
const __VLS_233 = __VLS_asFunctionalComponent1(__VLS_232, new __VLS_232({
    size: "small",
    ...{ style: {} },
    title: (__VLS_ctx.t('agent.uploadSkills')),
}));
const __VLS_234 = __VLS_233({
    size: "small",
    ...{ style: {} },
    title: (__VLS_ctx.t('agent.uploadSkills')),
}, ...__VLS_functionalComponentArgsRest(__VLS_233));
const { default: __VLS_237 } = __VLS_235.slots;
let __VLS_238;
/** @ts-ignore @type { | typeof __VLS_components.NForm | typeof __VLS_components.NForm} */
NForm;
// @ts-ignore
const __VLS_239 = __VLS_asFunctionalComponent1(__VLS_238, new __VLS_238({
    labelPlacement: "left",
    labelWidth: "80",
    showFeedback: (false),
}));
const __VLS_240 = __VLS_239({
    labelPlacement: "left",
    labelWidth: "80",
    showFeedback: (false),
}, ...__VLS_functionalComponentArgsRest(__VLS_239));
const { default: __VLS_243 } = __VLS_241.slots;
let __VLS_244;
/** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
NFormItem;
// @ts-ignore
const __VLS_245 = __VLS_asFunctionalComponent1(__VLS_244, new __VLS_244({
    label: (__VLS_ctx.t('agent.skillName')),
}));
const __VLS_246 = __VLS_245({
    label: (__VLS_ctx.t('agent.skillName')),
}, ...__VLS_functionalComponentArgsRest(__VLS_245));
const { default: __VLS_249 } = __VLS_247.slots;
let __VLS_250;
/** @ts-ignore @type { | typeof __VLS_components.NInput} */
NInput;
// @ts-ignore
const __VLS_251 = __VLS_asFunctionalComponent1(__VLS_250, new __VLS_250({
    value: (__VLS_ctx.uploadSkillName),
    placeholder: "my-skill",
}));
const __VLS_252 = __VLS_251({
    value: (__VLS_ctx.uploadSkillName),
    placeholder: "my-skill",
}, ...__VLS_functionalComponentArgsRest(__VLS_251));
// @ts-ignore
[t, t, t, uploadSkillName,];
var __VLS_247;
let __VLS_255;
/** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
NFormItem;
// @ts-ignore
const __VLS_256 = __VLS_asFunctionalComponent1(__VLS_255, new __VLS_255({
    label: (__VLS_ctx.t('agent.zipFile')),
}));
const __VLS_257 = __VLS_256({
    label: (__VLS_ctx.t('agent.zipFile')),
}, ...__VLS_functionalComponentArgsRest(__VLS_256));
const { default: __VLS_260 } = __VLS_258.slots;
let __VLS_261;
/** @ts-ignore @type { | typeof __VLS_components.NUpload | typeof __VLS_components.NUpload} */
NUpload;
// @ts-ignore
const __VLS_262 = __VLS_asFunctionalComponent1(__VLS_261, new __VLS_261({
    ...{ 'onUpdate:fileList': {} },
    multiple: (false),
    accept: ".zip",
    max: (1),
    defaultUpload: (false),
}));
const __VLS_263 = __VLS_262({
    ...{ 'onUpdate:fileList': {} },
    multiple: (false),
    accept: ".zip",
    max: (1),
    defaultUpload: (false),
}, ...__VLS_functionalComponentArgsRest(__VLS_262));
let __VLS_266;
const __VLS_267 = {
    ...{ 'update:fileList': {} },
    'onUpdate:fileList': ((list) => { __VLS_ctx.uploadFile = list.length ? list[0].file : null; }),
};
const { default: __VLS_268 } = __VLS_264.slots;
let __VLS_269;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_270 = __VLS_asFunctionalComponent1(__VLS_269, new __VLS_269({
    disabled: (__VLS_ctx.uploadingSkills),
}));
const __VLS_271 = __VLS_270({
    disabled: (__VLS_ctx.uploadingSkills),
}, ...__VLS_functionalComponentArgsRest(__VLS_270));
const { default: __VLS_274 } = __VLS_272.slots;
(__VLS_ctx.t('agent.selectZipFile'));
// @ts-ignore
[t, t, uploadFile, uploadingSkills,];
var __VLS_272;
// @ts-ignore
[];
var __VLS_264;
var __VLS_265;
// @ts-ignore
[];
var __VLS_258;
// @ts-ignore
[];
var __VLS_241;
let __VLS_275;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_276 = __VLS_asFunctionalComponent1(__VLS_275, new __VLS_275({
    justify: "end",
}));
const __VLS_277 = __VLS_276({
    justify: "end",
}, ...__VLS_functionalComponentArgsRest(__VLS_276));
const { default: __VLS_280 } = __VLS_278.slots;
let __VLS_281;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_282 = __VLS_asFunctionalComponent1(__VLS_281, new __VLS_281({
    ...{ 'onClick': {} },
    type: "primary",
    loading: (__VLS_ctx.uploadingSkills),
    disabled: (!__VLS_ctx.uploadSkillName.trim() || !__VLS_ctx.uploadFile || !__VLS_ctx.isEdit),
}));
const __VLS_283 = __VLS_282({
    ...{ 'onClick': {} },
    type: "primary",
    loading: (__VLS_ctx.uploadingSkills),
    disabled: (!__VLS_ctx.uploadSkillName.trim() || !__VLS_ctx.uploadFile || !__VLS_ctx.isEdit),
}, ...__VLS_functionalComponentArgsRest(__VLS_282));
let __VLS_286;
const __VLS_287 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.handleSkillUpload),
};
const { default: __VLS_288 } = __VLS_284.slots;
(__VLS_ctx.t('agent.uploadSkills'));
// @ts-ignore
[t, isEdit, uploadSkillName, uploadFile, uploadingSkills, handleSkillUpload,];
var __VLS_284;
var __VLS_285;
// @ts-ignore
[];
var __VLS_278;
let __VLS_289;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_290 = __VLS_asFunctionalComponent1(__VLS_289, new __VLS_289({
    depth: "3",
    ...{ style: {} },
}));
const __VLS_291 = __VLS_290({
    depth: "3",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_290));
const { default: __VLS_294 } = __VLS_292.slots;
(__VLS_ctx.t('agent.uploadFormatHint'));
// @ts-ignore
[t,];
var __VLS_292;
if (!__VLS_ctx.isEdit) {
    let __VLS_295;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_296 = __VLS_asFunctionalComponent1(__VLS_295, new __VLS_295({
        depth: "3",
        ...{ style: {} },
    }));
    const __VLS_297 = __VLS_296({
        depth: "3",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_296));
    const { default: __VLS_300 } = __VLS_298.slots;
    (__VLS_ctx.t('agent.saveBeforeUpload'));
    // @ts-ignore
    [t, isEdit,];
    var __VLS_298;
}
// @ts-ignore
[];
var __VLS_235;
if (__VLS_ctx.workspaceSkills.length) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ style: {} },
    });
    let __VLS_301;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_302 = __VLS_asFunctionalComponent1(__VLS_301, new __VLS_301({
        strong: true,
        ...{ style: {} },
    }));
    const __VLS_303 = __VLS_302({
        strong: true,
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_302));
    const { default: __VLS_306 } = __VLS_304.slots;
    (__VLS_ctx.t('agent.installedSkills'));
    // @ts-ignore
    [t, workspaceSkills,];
    var __VLS_304;
    let __VLS_307;
    /** @ts-ignore @type { | typeof __VLS_components.NGrid | typeof __VLS_components.NGrid} */
    NGrid;
    // @ts-ignore
    const __VLS_308 = __VLS_asFunctionalComponent1(__VLS_307, new __VLS_307({
        cols: (4),
        xGap: (12),
        yGap: (12),
        responsive: "screen",
        itemResponsive: true,
    }));
    const __VLS_309 = __VLS_308({
        cols: (4),
        xGap: (12),
        yGap: (12),
        responsive: "screen",
        itemResponsive: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_308));
    const { default: __VLS_312 } = __VLS_310.slots;
    for (const [skill] of __VLS_vFor((__VLS_ctx.workspaceSkills))) {
        let __VLS_313;
        /** @ts-ignore @type { | typeof __VLS_components.NGi | typeof __VLS_components.NGi} */
        NGi;
        // @ts-ignore
        const __VLS_314 = __VLS_asFunctionalComponent1(__VLS_313, new __VLS_313({
            key: (skill.name),
            span: (1),
        }));
        const __VLS_315 = __VLS_314({
            key: (skill.name),
            span: (1),
        }, ...__VLS_functionalComponentArgsRest(__VLS_314));
        const { default: __VLS_318 } = __VLS_316.slots;
        let __VLS_319;
        /** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
        NCard;
        // @ts-ignore
        const __VLS_320 = __VLS_asFunctionalComponent1(__VLS_319, new __VLS_319({
            ...{ 'onClose': {} },
            size: "small",
            title: (skill.name),
            closable: true,
        }));
        const __VLS_321 = __VLS_320({
            ...{ 'onClose': {} },
            size: "small",
            title: (skill.name),
            closable: true,
        }, ...__VLS_functionalComponentArgsRest(__VLS_320));
        let __VLS_324;
        const __VLS_325 = {
            ...{ close: {} },
            onClose: (...[$event]) => {
                if (!(__VLS_ctx.workspaceSkills.length))
                    return;
                __VLS_ctx.deleteWorkspaceSkill(skill.name);
                // @ts-ignore
                [workspaceSkills, deleteWorkspaceSkill,];
            },
        };
        const { default: __VLS_326 } = __VLS_322.slots;
        let __VLS_327;
        /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
        NText;
        // @ts-ignore
        const __VLS_328 = __VLS_asFunctionalComponent1(__VLS_327, new __VLS_327({
            depth: "3",
            ...{ style: {} },
        }));
        const __VLS_329 = __VLS_328({
            depth: "3",
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_328));
        const { default: __VLS_332 } = __VLS_330.slots;
        (skill.description || '-');
        // @ts-ignore
        [];
        var __VLS_330;
        let __VLS_333;
        /** @ts-ignore @type { | typeof __VLS_components.NDivider} */
        NDivider;
        // @ts-ignore
        const __VLS_334 = __VLS_asFunctionalComponent1(__VLS_333, new __VLS_333({
            ...{ style: {} },
        }));
        const __VLS_335 = __VLS_334({
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_334));
        let __VLS_338;
        /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
        NTag;
        // @ts-ignore
        const __VLS_339 = __VLS_asFunctionalComponent1(__VLS_338, new __VLS_338({
            size: "small",
            bordered: (false),
        }));
        const __VLS_340 = __VLS_339({
            size: "small",
            bordered: (false),
        }, ...__VLS_functionalComponentArgsRest(__VLS_339));
        const { default: __VLS_343 } = __VLS_341.slots;
        (skill.source);
        // @ts-ignore
        [];
        var __VLS_341;
        // @ts-ignore
        [];
        var __VLS_322;
        var __VLS_323;
        // @ts-ignore
        [];
        var __VLS_316;
        // @ts-ignore
        [];
    }
    // @ts-ignore
    [];
    var __VLS_310;
}
let __VLS_344;
/** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
NCard;
// @ts-ignore
const __VLS_345 = __VLS_asFunctionalComponent1(__VLS_344, new __VLS_344({
    size: "small",
    ...{ style: {} },
}));
const __VLS_346 = __VLS_345({
    size: "small",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_345));
const { default: __VLS_349 } = __VLS_347.slots;
if (!__VLS_ctx.showSkillForm) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
    let __VLS_350;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_351 = __VLS_asFunctionalComponent1(__VLS_350, new __VLS_350({
        ...{ 'onClick': {} },
        dashed: true,
        block: true,
    }));
    const __VLS_352 = __VLS_351({
        ...{ 'onClick': {} },
        dashed: true,
        block: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_351));
    let __VLS_355;
    const __VLS_356 = {
        ...{ click: {} },
        onClick: (...[$event]) => {
            if (!(!__VLS_ctx.showSkillForm))
                return;
            __VLS_ctx.showSkillForm = true;
            // @ts-ignore
            [showSkillForm, showSkillForm,];
        },
    };
    const { default: __VLS_357 } = __VLS_353.slots;
    (__VLS_ctx.t('agent.createSkillRepo'));
    // @ts-ignore
    [t,];
    var __VLS_353;
    var __VLS_354;
}
else {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
    let __VLS_358;
    /** @ts-ignore @type { | typeof __VLS_components.NForm | typeof __VLS_components.NForm} */
    NForm;
    // @ts-ignore
    const __VLS_359 = __VLS_asFunctionalComponent1(__VLS_358, new __VLS_358({
        labelPlacement: "left",
        labelWidth: "80",
        showFeedback: (false),
    }));
    const __VLS_360 = __VLS_359({
        labelPlacement: "left",
        labelWidth: "80",
        showFeedback: (false),
    }, ...__VLS_functionalComponentArgsRest(__VLS_359));
    const { default: __VLS_363 } = __VLS_361.slots;
    let __VLS_364;
    /** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
    NFormItem;
    // @ts-ignore
    const __VLS_365 = __VLS_asFunctionalComponent1(__VLS_364, new __VLS_364({
        label: (__VLS_ctx.t('common.name')),
    }));
    const __VLS_366 = __VLS_365({
        label: (__VLS_ctx.t('common.name')),
    }, ...__VLS_functionalComponentArgsRest(__VLS_365));
    const { default: __VLS_369 } = __VLS_367.slots;
    let __VLS_370;
    /** @ts-ignore @type { | typeof __VLS_components.NInput} */
    NInput;
    // @ts-ignore
    const __VLS_371 = __VLS_asFunctionalComponent1(__VLS_370, new __VLS_370({
        value: (__VLS_ctx.newSkillName),
        placeholder: "my-skills",
    }));
    const __VLS_372 = __VLS_371({
        value: (__VLS_ctx.newSkillName),
        placeholder: "my-skills",
    }, ...__VLS_functionalComponentArgsRest(__VLS_371));
    // @ts-ignore
    [t, newSkillName,];
    var __VLS_367;
    let __VLS_375;
    /** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
    NFormItem;
    // @ts-ignore
    const __VLS_376 = __VLS_asFunctionalComponent1(__VLS_375, new __VLS_375({
        label: (__VLS_ctx.t('common.type')),
    }));
    const __VLS_377 = __VLS_376({
        label: (__VLS_ctx.t('common.type')),
    }, ...__VLS_functionalComponentArgsRest(__VLS_376));
    const { default: __VLS_380 } = __VLS_378.slots;
    let __VLS_381;
    /** @ts-ignore @type { | typeof __VLS_components.NSelect} */
    NSelect;
    // @ts-ignore
    const __VLS_382 = __VLS_asFunctionalComponent1(__VLS_381, new __VLS_381({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.newSkillType),
        options: (__VLS_ctx.skillTypeOptions),
        filterable: true,
    }));
    const __VLS_383 = __VLS_382({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.newSkillType),
        options: (__VLS_ctx.skillTypeOptions),
        filterable: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_382));
    let __VLS_386;
    const __VLS_387 = {
        ...{ 'update:value': {} },
        'onUpdate:value': (__VLS_ctx.onNewSkillTypeChange),
    };
    var __VLS_384;
    var __VLS_385;
    // @ts-ignore
    [t, newSkillType, skillTypeOptions, onNewSkillTypeChange,];
    var __VLS_378;
    if (__VLS_ctx.newSkillType === 'local') {
        let __VLS_388;
        /** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
        NFormItem;
        // @ts-ignore
        const __VLS_389 = __VLS_asFunctionalComponent1(__VLS_388, new __VLS_388({
            label: "Path",
        }));
        const __VLS_390 = __VLS_389({
            label: "Path",
        }, ...__VLS_functionalComponentArgsRest(__VLS_389));
        const { default: __VLS_393 } = __VLS_391.slots;
        let __VLS_394;
        /** @ts-ignore @type { | typeof __VLS_components.NInput} */
        NInput;
        // @ts-ignore
        const __VLS_395 = __VLS_asFunctionalComponent1(__VLS_394, new __VLS_394({
            value: (__VLS_ctx.newSkillProps['path']),
            placeholder: "skills",
        }));
        const __VLS_396 = __VLS_395({
            value: (__VLS_ctx.newSkillProps['path']),
            placeholder: "skills",
        }, ...__VLS_functionalComponentArgsRest(__VLS_395));
        {
            const { feedback: __VLS_399 } = __VLS_391.slots;
            let __VLS_400;
            /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
            NText;
            // @ts-ignore
            const __VLS_401 = __VLS_asFunctionalComponent1(__VLS_400, new __VLS_400({
                depth: "3",
                ...{ style: {} },
            }));
            const __VLS_402 = __VLS_401({
                depth: "3",
                ...{ style: {} },
            }, ...__VLS_functionalComponentArgsRest(__VLS_401));
            const { default: __VLS_405 } = __VLS_403.slots;
            (__VLS_ctx.t('agent.localPathHint'));
            // @ts-ignore
            [t, newSkillType, newSkillProps,];
            var __VLS_403;
            // @ts-ignore
            [];
        }
        // @ts-ignore
        [];
        var __VLS_391;
    }
    if (__VLS_ctx.newSkillType === 'git') {
        let __VLS_406;
        /** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
        NFormItem;
        // @ts-ignore
        const __VLS_407 = __VLS_asFunctionalComponent1(__VLS_406, new __VLS_406({
            label: "Remote URL",
        }));
        const __VLS_408 = __VLS_407({
            label: "Remote URL",
        }, ...__VLS_functionalComponentArgsRest(__VLS_407));
        const { default: __VLS_411 } = __VLS_409.slots;
        let __VLS_412;
        /** @ts-ignore @type { | typeof __VLS_components.NInput} */
        NInput;
        // @ts-ignore
        const __VLS_413 = __VLS_asFunctionalComponent1(__VLS_412, new __VLS_412({
            value: (__VLS_ctx.newSkillProps['remoteUrl']),
            placeholder: "https://github.com/...",
        }));
        const __VLS_414 = __VLS_413({
            value: (__VLS_ctx.newSkillProps['remoteUrl']),
            placeholder: "https://github.com/...",
        }, ...__VLS_functionalComponentArgsRest(__VLS_413));
        // @ts-ignore
        [newSkillType, newSkillProps,];
        var __VLS_409;
        let __VLS_417;
        /** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
        NFormItem;
        // @ts-ignore
        const __VLS_418 = __VLS_asFunctionalComponent1(__VLS_417, new __VLS_417({
            label: "Branch",
        }));
        const __VLS_419 = __VLS_418({
            label: "Branch",
        }, ...__VLS_functionalComponentArgsRest(__VLS_418));
        const { default: __VLS_422 } = __VLS_420.slots;
        let __VLS_423;
        /** @ts-ignore @type { | typeof __VLS_components.NInput} */
        NInput;
        // @ts-ignore
        const __VLS_424 = __VLS_asFunctionalComponent1(__VLS_423, new __VLS_423({
            value: (__VLS_ctx.newSkillProps['branch']),
            placeholder: "main",
        }));
        const __VLS_425 = __VLS_424({
            value: (__VLS_ctx.newSkillProps['branch']),
            placeholder: "main",
        }, ...__VLS_functionalComponentArgsRest(__VLS_424));
        // @ts-ignore
        [newSkillProps,];
        var __VLS_420;
    }
    // @ts-ignore
    [];
    var __VLS_361;
    let __VLS_428;
    /** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
    NSpace;
    // @ts-ignore
    const __VLS_429 = __VLS_asFunctionalComponent1(__VLS_428, new __VLS_428({
        justify: "end",
    }));
    const __VLS_430 = __VLS_429({
        justify: "end",
    }, ...__VLS_functionalComponentArgsRest(__VLS_429));
    const { default: __VLS_433 } = __VLS_431.slots;
    let __VLS_434;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_435 = __VLS_asFunctionalComponent1(__VLS_434, new __VLS_434({
        ...{ 'onClick': {} },
    }));
    const __VLS_436 = __VLS_435({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_435));
    let __VLS_439;
    const __VLS_440 = {
        ...{ click: {} },
        onClick: (...[$event]) => {
            if (!!(!__VLS_ctx.showSkillForm))
                return;
            __VLS_ctx.showSkillForm = false;
            // @ts-ignore
            [showSkillForm,];
        },
    };
    const { default: __VLS_441 } = __VLS_437.slots;
    (__VLS_ctx.t('common.cancel'));
    // @ts-ignore
    [t,];
    var __VLS_437;
    var __VLS_438;
    let __VLS_442;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_443 = __VLS_asFunctionalComponent1(__VLS_442, new __VLS_442({
        ...{ 'onClick': {} },
        type: "primary",
        loading: (__VLS_ctx.creatingSkill),
    }));
    const __VLS_444 = __VLS_443({
        ...{ 'onClick': {} },
        type: "primary",
        loading: (__VLS_ctx.creatingSkill),
    }, ...__VLS_functionalComponentArgsRest(__VLS_443));
    let __VLS_447;
    const __VLS_448 = {
        ...{ click: {} },
        onClick: (__VLS_ctx.createSkillRepo),
    };
    const { default: __VLS_449 } = __VLS_445.slots;
    (__VLS_ctx.t('common.create'));
    // @ts-ignore
    [t, creatingSkill, createSkillRepo,];
    var __VLS_445;
    var __VLS_446;
    // @ts-ignore
    [];
    var __VLS_431;
}
// @ts-ignore
[];
var __VLS_347;
let __VLS_450;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_451 = __VLS_asFunctionalComponent1(__VLS_450, new __VLS_450({
    depth: "3",
    ...{ style: {} },
}));
const __VLS_452 = __VLS_451({
    depth: "3",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_451));
const { default: __VLS_455 } = __VLS_453.slots;
(__VLS_ctx.t('agent.skillsHint'));
// @ts-ignore
[t,];
var __VLS_453;
let __VLS_456;
/** @ts-ignore @type { | typeof __VLS_components.NGrid | typeof __VLS_components.NGrid} */
NGrid;
// @ts-ignore
const __VLS_457 = __VLS_asFunctionalComponent1(__VLS_456, new __VLS_456({
    cols: (4),
    xGap: (12),
    yGap: (12),
    responsive: "screen",
    itemResponsive: true,
}));
const __VLS_458 = __VLS_457({
    cols: (4),
    xGap: (12),
    yGap: (12),
    responsive: "screen",
    itemResponsive: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_457));
const { default: __VLS_461 } = __VLS_459.slots;
for (const [repo] of __VLS_vFor((__VLS_ctx.allSkillRepos))) {
    let __VLS_462;
    /** @ts-ignore @type { | typeof __VLS_components.NGi | typeof __VLS_components.NGi} */
    NGi;
    // @ts-ignore
    const __VLS_463 = __VLS_asFunctionalComponent1(__VLS_462, new __VLS_462({
        key: (repo.id),
        span: (1),
    }));
    const __VLS_464 = __VLS_463({
        key: (repo.id),
        span: (1),
    }, ...__VLS_functionalComponentArgsRest(__VLS_463));
    const { default: __VLS_467 } = __VLS_465.slots;
    let __VLS_468;
    /** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
    NCard;
    // @ts-ignore
    const __VLS_469 = __VLS_asFunctionalComponent1(__VLS_468, new __VLS_468({
        ...{ 'onClick': {} },
        size: "small",
        hoverable: true,
        ...{ class: ({ 'tool-card-active': __VLS_ctx.activeSkillRepoIds.has(repo.id) }) },
        ...{ style: {} },
    }));
    const __VLS_470 = __VLS_469({
        ...{ 'onClick': {} },
        size: "small",
        hoverable: true,
        ...{ class: ({ 'tool-card-active': __VLS_ctx.activeSkillRepoIds.has(repo.id) }) },
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_469));
    let __VLS_473;
    const __VLS_474 = {
        ...{ click: {} },
        onClick: (...[$event]) => {
            __VLS_ctx.toggleSkillRepo(repo);
            // @ts-ignore
            [allSkillRepos, activeSkillRepoIds, toggleSkillRepo,];
        },
    };
    /** @type {__VLS_StyleScopedClasses['tool-card-active']} */ ;
    const { default: __VLS_475 } = __VLS_471.slots;
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ style: {} },
    });
    let __VLS_476;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_477 = __VLS_asFunctionalComponent1(__VLS_476, new __VLS_476({
        strong: true,
        ...{ style: {} },
    }));
    const __VLS_478 = __VLS_477({
        strong: true,
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_477));
    const { default: __VLS_481 } = __VLS_479.slots;
    (repo.name);
    // @ts-ignore
    [];
    var __VLS_479;
    let __VLS_482;
    /** @ts-ignore @type { | typeof __VLS_components.NSwitch} */
    NSwitch;
    // @ts-ignore
    const __VLS_483 = __VLS_asFunctionalComponent1(__VLS_482, new __VLS_482({
        value: (__VLS_ctx.activeSkillRepoIds.has(repo.id)),
        size: "small",
    }));
    const __VLS_484 = __VLS_483({
        value: (__VLS_ctx.activeSkillRepoIds.has(repo.id)),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_483));
    let __VLS_487;
    /** @ts-ignore @type { | typeof __VLS_components.NDivider} */
    NDivider;
    // @ts-ignore
    const __VLS_488 = __VLS_asFunctionalComponent1(__VLS_487, new __VLS_487({
        ...{ style: {} },
    }));
    const __VLS_489 = __VLS_488({
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_488));
    let __VLS_492;
    /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
    NTag;
    // @ts-ignore
    const __VLS_493 = __VLS_asFunctionalComponent1(__VLS_492, new __VLS_492({
        size: "small",
        bordered: (false),
    }));
    const __VLS_494 = __VLS_493({
        size: "small",
        bordered: (false),
    }, ...__VLS_functionalComponentArgsRest(__VLS_493));
    const { default: __VLS_497 } = __VLS_495.slots;
    (repo.type);
    // @ts-ignore
    [activeSkillRepoIds,];
    var __VLS_495;
    // @ts-ignore
    [];
    var __VLS_471;
    var __VLS_472;
    // @ts-ignore
    [];
    var __VLS_465;
    // @ts-ignore
    [];
}
// @ts-ignore
[];
var __VLS_459;
if (!__VLS_ctx.allSkillRepos.length && !__VLS_ctx.showSkillForm) {
    let __VLS_498;
    /** @ts-ignore @type { | typeof __VLS_components.NEmpty} */
    NEmpty;
    // @ts-ignore
    const __VLS_499 = __VLS_asFunctionalComponent1(__VLS_498, new __VLS_498({
        description: (__VLS_ctx.t('agent.noSkillReposHint')),
        ...{ style: {} },
    }));
    const __VLS_500 = __VLS_499({
        description: (__VLS_ctx.t('agent.noSkillReposHint')),
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_499));
}
// @ts-ignore
[t, showSkillForm, allSkillRepos,];
var __VLS_229;
let __VLS_503;
/** @ts-ignore @type { | typeof __VLS_components.NTabPane | typeof __VLS_components.NTabPane} */
NTabPane;
// @ts-ignore
const __VLS_504 = __VLS_asFunctionalComponent1(__VLS_503, new __VLS_503({
    name: "middlewares",
    tab: (__VLS_ctx.t('agent.tabMiddleware')),
}));
const __VLS_505 = __VLS_504({
    name: "middlewares",
    tab: (__VLS_ctx.t('agent.tabMiddleware')),
}, ...__VLS_functionalComponentArgsRest(__VLS_504));
const { default: __VLS_508 } = __VLS_506.slots;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ style: {} },
});
let __VLS_509;
/** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
NCard;
// @ts-ignore
const __VLS_510 = __VLS_asFunctionalComponent1(__VLS_509, new __VLS_509({
    size: "small",
    title: (__VLS_ctx.t('agent.addMiddleware')),
    ...{ style: {} },
}));
const __VLS_511 = __VLS_510({
    size: "small",
    title: (__VLS_ctx.t('agent.addMiddleware')),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_510));
const { default: __VLS_514 } = __VLS_512.slots;
let __VLS_515;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_516 = __VLS_asFunctionalComponent1(__VLS_515, new __VLS_515({
    align: "end",
}));
const __VLS_517 = __VLS_516({
    align: "end",
}, ...__VLS_functionalComponentArgsRest(__VLS_516));
const { default: __VLS_520 } = __VLS_518.slots;
let __VLS_521;
/** @ts-ignore @type { | typeof __VLS_components.NSelect} */
NSelect;
// @ts-ignore
const __VLS_522 = __VLS_asFunctionalComponent1(__VLS_521, new __VLS_521({
    value: (__VLS_ctx.selectedMwType),
    options: (__VLS_ctx.mwTypeOptions),
    placeholder: (__VLS_ctx.t('agent.selectMwType')),
    ...{ style: {} },
    filterable: true,
}));
const __VLS_523 = __VLS_522({
    value: (__VLS_ctx.selectedMwType),
    options: (__VLS_ctx.mwTypeOptions),
    placeholder: (__VLS_ctx.t('agent.selectMwType')),
    ...{ style: {} },
    filterable: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_522));
let __VLS_526;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_527 = __VLS_asFunctionalComponent1(__VLS_526, new __VLS_526({
    ...{ 'onClick': {} },
    type: "primary",
    disabled: (!__VLS_ctx.selectedMwType),
}));
const __VLS_528 = __VLS_527({
    ...{ 'onClick': {} },
    type: "primary",
    disabled: (!__VLS_ctx.selectedMwType),
}, ...__VLS_functionalComponentArgsRest(__VLS_527));
let __VLS_531;
const __VLS_532 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.addMiddleware),
};
const { default: __VLS_533 } = __VLS_529.slots;
(__VLS_ctx.t('common.add'));
// @ts-ignore
[t, t, t, t, selectedMwType, selectedMwType, mwTypeOptions, addMiddleware,];
var __VLS_529;
var __VLS_530;
// @ts-ignore
[];
var __VLS_518;
// @ts-ignore
[];
var __VLS_512;
for (const [spec, idx] of __VLS_vFor((__VLS_ctx.formMiddlewareSpecs))) {
    let __VLS_534;
    /** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
    NCard;
    // @ts-ignore
    const __VLS_535 = __VLS_asFunctionalComponent1(__VLS_534, new __VLS_534({
        ...{ 'onClose': {} },
        key: (idx),
        size: "small",
        closable: true,
        ...{ style: {} },
    }));
    const __VLS_536 = __VLS_535({
        ...{ 'onClose': {} },
        key: (idx),
        size: "small",
        closable: true,
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_535));
    let __VLS_539;
    const __VLS_540 = {
        ...{ close: {} },
        onClose: (...[$event]) => {
            __VLS_ctx.removeMiddleware(idx);
            // @ts-ignore
            [formMiddlewareSpecs, removeMiddleware,];
        },
    };
    const { default: __VLS_541 } = __VLS_537.slots;
    let __VLS_542;
    /** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
    NSpace;
    // @ts-ignore
    const __VLS_543 = __VLS_asFunctionalComponent1(__VLS_542, new __VLS_542({
        align: "center",
        ...{ style: {} },
    }));
    const __VLS_544 = __VLS_543({
        align: "center",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_543));
    const { default: __VLS_547 } = __VLS_545.slots;
    let __VLS_548;
    /** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
    NTag;
    // @ts-ignore
    const __VLS_549 = __VLS_asFunctionalComponent1(__VLS_548, new __VLS_548({
        type: "warning",
        size: "small",
    }));
    const __VLS_550 = __VLS_549({
        type: "warning",
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_549));
    const { default: __VLS_553 } = __VLS_551.slots;
    (spec.type);
    // @ts-ignore
    [];
    var __VLS_551;
    let __VLS_554;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_555 = __VLS_asFunctionalComponent1(__VLS_554, new __VLS_554({
        depth: "3",
    }));
    const __VLS_556 = __VLS_555({
        depth: "3",
    }, ...__VLS_functionalComponentArgsRest(__VLS_555));
    const { default: __VLS_559 } = __VLS_557.slots;
    (__VLS_ctx.factories.middlewareTypes.find(m => m.type === spec.type)?.description);
    // @ts-ignore
    [factories,];
    var __VLS_557;
    // @ts-ignore
    [];
    var __VLS_545;
    let __VLS_560;
    /** @ts-ignore @type { | typeof __VLS_components.NInput} */
    NInput;
    // @ts-ignore
    const __VLS_561 = __VLS_asFunctionalComponent1(__VLS_560, new __VLS_560({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.propsToStr(spec.props)),
        type: "textarea",
        rows: (3),
        placeholder: "{ ... }",
    }));
    const __VLS_562 = __VLS_561({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.propsToStr(spec.props)),
        type: "textarea",
        rows: (3),
        placeholder: "{ ... }",
    }, ...__VLS_functionalComponentArgsRest(__VLS_561));
    let __VLS_565;
    const __VLS_566 = {
        ...{ 'update:value': {} },
        'onUpdate:value': ((v) => __VLS_ctx.updateProps(__VLS_ctx.formMiddlewareSpecs, idx, v)),
    };
    var __VLS_563;
    var __VLS_564;
    // @ts-ignore
    [formMiddlewareSpecs, propsToStr, updateProps,];
    var __VLS_537;
    var __VLS_538;
    // @ts-ignore
    [];
}
if (!__VLS_ctx.formMiddlewareSpecs.length) {
    let __VLS_567;
    /** @ts-ignore @type { | typeof __VLS_components.NEmpty} */
    NEmpty;
    // @ts-ignore
    const __VLS_568 = __VLS_asFunctionalComponent1(__VLS_567, new __VLS_567({
        description: (__VLS_ctx.t('agent.noMiddlewares')),
        ...{ style: {} },
    }));
    const __VLS_569 = __VLS_568({
        description: (__VLS_ctx.t('agent.noMiddlewares')),
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_568));
}
// @ts-ignore
[t, formMiddlewareSpecs,];
var __VLS_506;
let __VLS_572;
/** @ts-ignore @type { | typeof __VLS_components.NTabPane | typeof __VLS_components.NTabPane} */
NTabPane;
// @ts-ignore
const __VLS_573 = __VLS_asFunctionalComponent1(__VLS_572, new __VLS_572({
    name: "subagents",
    tab: (__VLS_ctx.t('agent.tabSubAgents')),
}));
const __VLS_574 = __VLS_573({
    name: "subagents",
    tab: (__VLS_ctx.t('agent.tabSubAgents')),
}, ...__VLS_functionalComponentArgsRest(__VLS_573));
const { default: __VLS_577 } = __VLS_575.slots;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ style: {} },
});
let __VLS_578;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_579 = __VLS_asFunctionalComponent1(__VLS_578, new __VLS_578({
    depth: "3",
    ...{ style: {} },
}));
const __VLS_580 = __VLS_579({
    depth: "3",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_579));
const { default: __VLS_583 } = __VLS_581.slots;
(__VLS_ctx.t('agent.subagentRefsHint'));
// @ts-ignore
[t, t,];
var __VLS_581;
let __VLS_584;
/** @ts-ignore @type { | typeof __VLS_components.NDynamicTags} */
NDynamicTags;
// @ts-ignore
const __VLS_585 = __VLS_asFunctionalComponent1(__VLS_584, new __VLS_584({
    value: (__VLS_ctx.formSubagentRefs),
}));
const __VLS_586 = __VLS_585({
    value: (__VLS_ctx.formSubagentRefs),
}, ...__VLS_functionalComponentArgsRest(__VLS_585));
if (!__VLS_ctx.formSubagentRefs.length) {
    let __VLS_589;
    /** @ts-ignore @type { | typeof __VLS_components.NEmpty} */
    NEmpty;
    // @ts-ignore
    const __VLS_590 = __VLS_asFunctionalComponent1(__VLS_589, new __VLS_589({
        description: (__VLS_ctx.t('agent.noSubagents')),
        ...{ style: {} },
    }));
    const __VLS_591 = __VLS_590({
        description: (__VLS_ctx.t('agent.noSubagents')),
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_590));
}
// @ts-ignore
[t, formSubagentRefs, formSubagentRefs,];
var __VLS_575;
// @ts-ignore
[];
var __VLS_57;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
