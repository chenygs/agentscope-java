import { ref, onMounted, h } from 'vue';
import { useRouter } from 'vue-router';
import { NDataTable, NButton, NSpace, NH2, NPopconfirm, NTag, useDialog, } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import * as agentApi from '@/api/agent';
const { t } = useI18n();
const dialog = useDialog();
const router = useRouter();
// ── 列表数据 ──
const agents = ref([]);
const loading = ref(false);
async function fetchAgents() {
    loading.value = true;
    try {
        const resp = await agentApi.listAgents();
        agents.value = resp.data.data ?? [];
    }
    catch { /* 拦截器已处理 */ }
    finally {
        loading.value = false;
    }
}
onMounted(() => fetchAgents());
async function handleDelete(agent) {
    try {
        await agentApi.deleteAgent(agent.id);
        await fetchAgents();
    }
    catch { /* 拦截器已处理 */ }
}
function openEdit(agent) {
    router.push({ name: 'AgentEdit', params: { id: agent.id } });
}
function openChat(agent) {
    router.push({ name: 'AgentChat', params: { id: agent.id } });
}
function openCreate() {
    router.push({ name: 'AgentCreate' });
}
// ── 表格列 ──
const columns = [
    {
        title: 'Agent ID',
        key: 'agentId',
        width: 160,
        ellipsis: { tooltip: true },
    },
    {
        title: t('common.name'),
        key: 'name',
        width: 160,
        ellipsis: { tooltip: true },
    },
    {
        title: t('agent.agentType'),
        key: 'agentType',
        width: 110,
        render(row) {
            return h(NTag, { size: 'small', type: row.agentType === 'HARNESS' ? 'info' : 'success' }, { default: () => row.agentType });
        },
    },
    {
        title: t('common.description'),
        key: 'description',
        ellipsis: { tooltip: true },
    },
    {
        title: t('agent.maxIters'),
        key: 'maxIters',
        width: 90,
        align: 'center',
    },
    {
        title: t('agent.tools'),
        key: 'toolCount',
        width: 80,
        align: 'center',
        render(row) {
            return h('span', {}, String(row.toolSpecs?.length ?? 0));
        },
    },
    {
        title: t('common.action'),
        key: 'actions',
        width: 200,
        render(row) {
            return h(NSpace, { size: 8 }, {
                default: () => [
                    h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openChat(row) }, { default: () => t('agent.chat') }),
                    h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openEdit(row) }, { default: () => t('common.edit') }),
                    h(NPopconfirm, { onPositiveClick: () => handleDelete(row) }, {
                        trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => t('common.delete') }),
                        default: () => t('agent.deleteConfirm'),
                    }),
                ],
            });
        },
    },
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
/** @ts-ignore @type { | typeof __VLS_components.NH2 | typeof __VLS_components.NH2} */
NH2;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    ...{ style: {} },
}));
const __VLS_8 = __VLS_7({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
const { default: __VLS_11 } = __VLS_9.slots;
(__VLS_ctx.t('agent.list'));
// @ts-ignore
[t,];
var __VLS_9;
let __VLS_12;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent1(__VLS_12, new __VLS_12({}));
const __VLS_14 = __VLS_13({}, ...__VLS_functionalComponentArgsRest(__VLS_13));
const { default: __VLS_17 } = __VLS_15.slots;
let __VLS_18;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_19 = __VLS_asFunctionalComponent1(__VLS_18, new __VLS_18({
    ...{ 'onClick': {} },
}));
const __VLS_20 = __VLS_19({
    ...{ 'onClick': {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_19));
let __VLS_23;
const __VLS_24 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.fetchAgents),
};
const { default: __VLS_25 } = __VLS_21.slots;
(__VLS_ctx.t('common.refresh'));
// @ts-ignore
[t, fetchAgents,];
var __VLS_21;
var __VLS_22;
let __VLS_26;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_27 = __VLS_asFunctionalComponent1(__VLS_26, new __VLS_26({
    ...{ 'onClick': {} },
    type: "primary",
}));
const __VLS_28 = __VLS_27({
    ...{ 'onClick': {} },
    type: "primary",
}, ...__VLS_functionalComponentArgsRest(__VLS_27));
let __VLS_31;
const __VLS_32 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.openCreate),
};
const { default: __VLS_33 } = __VLS_29.slots;
(__VLS_ctx.t('agent.create'));
// @ts-ignore
[t, openCreate,];
var __VLS_29;
var __VLS_30;
// @ts-ignore
[];
var __VLS_15;
// @ts-ignore
[];
var __VLS_3;
let __VLS_34;
/** @ts-ignore @type { | typeof __VLS_components.NDataTable} */
NDataTable;
// @ts-ignore
const __VLS_35 = __VLS_asFunctionalComponent1(__VLS_34, new __VLS_34({
    columns: (__VLS_ctx.columns),
    data: (__VLS_ctx.agents),
    loading: (__VLS_ctx.loading),
    rowKey: ((row) => row.id),
    striped: true,
}));
const __VLS_36 = __VLS_35({
    columns: (__VLS_ctx.columns),
    data: (__VLS_ctx.agents),
    loading: (__VLS_ctx.loading),
    rowKey: ((row) => row.id),
    striped: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_35));
// @ts-ignore
[columns, agents, loading,];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
