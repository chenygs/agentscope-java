import { ref, computed, onMounted, h } from 'vue';
import { NDataTable, NButton, NDrawer, NDrawerContent, NInput, NSelect, NSpace, NH2, NText, NTag, useDialog, } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import * as resourceApi from '@/api/resource';
import { useFactoriesStore } from '@/stores/factories';
import JsonSchemaForm from '@/components/JsonSchemaForm.vue';
const { t } = useI18n();
const dialog = useDialog();
const factories = useFactoriesStore();
// ── 列表数据 ──
const models = ref([]);
const loading = ref(false);
async function fetchModels() {
    loading.value = true;
    try {
        const resp = await resourceApi.listModels();
        models.value = resp.data.data ?? [];
    }
    catch { /* 拦截器已处理 */ }
    finally {
        loading.value = false;
    }
}
onMounted(() => fetchModels());
// ── Drawer 状态 ──
const drawerVisible = ref(false);
const editingModel = ref(null);
const isEdit = computed(() => !!editingModel.value);
// ── 表单 ──
const formName = ref('');
const formType = ref(null);
const formProps = ref({});
const saving = ref(false);
// ── 当前选中类型的 schema ──
const currentTypeMeta = computed(() => {
    if (!formType.value)
        return undefined;
    return factories.getModelType(formType.value);
});
const currentSchema = computed(() => {
    return currentTypeMeta.value?.schema ?? { type: 'object', properties: {} };
});
// ── 类型选择下拉选项 ──
const typeOptions = computed(() => factories.modelTypes.map((m) => ({
    label: `${m.displayName} (${m.type})`,
    value: m.type,
})));
// ── 打开新建 Drawer ──
function openCreate() {
    editingModel.value = null;
    formName.value = '';
    formType.value = factories.modelTypes[0]?.type ?? null;
    formProps.value = {};
    drawerVisible.value = true;
}
// ── 打开编辑 Drawer ──
function openEdit(row) {
    editingModel.value = row;
    formName.value = row.name;
    formType.value = row.type;
    // 深拷贝 props，避免直接修改 store 数据
    formProps.value = JSON.parse(JSON.stringify(row.props ?? {}));
    drawerVisible.value = true;
}
// ── 切换类型时重置 props ──
function onTypeChange() {
    formProps.value = {};
}
// ── 保存 ──
async function handleSave() {
    // 校验基础字段
    if (!formName.value.trim()) {
        window.$message?.error(`${t('model.name')} ${t('common.required')}`);
        return;
    }
    if (!formType.value) {
        window.$message?.error(`${t('model.modelType')} ${t('common.required')}`);
        return;
    }
    // 校验 schema 中的 required 字段
    const requiredFields = currentSchema.value.required ?? [];
    const schemaProps = currentSchema.value.properties ?? {};
    for (const field of requiredFields) {
        const val = formProps.value[field];
        if (val === undefined || val === null || val === '') {
            const label = (schemaProps[field]?.title ?? field);
            window.$message?.error(`${label} ${t('common.required')}`);
            return;
        }
    }
    saving.value = true;
    const req = {
        name: formName.value,
        type: formType.value,
        props: formProps.value,
    };
    try {
        if (isEdit.value && editingModel.value) {
            await resourceApi.updateModel(editingModel.value.id, req);
            window.$message?.success(t('model.saveSuccess'));
        }
        else {
            await resourceApi.createModel(req);
            window.$message?.success(t('model.saveSuccess'));
        }
        drawerVisible.value = false;
        await fetchModels();
    }
    catch { /* 拦截器已处理 */ }
    finally {
        saving.value = false;
    }
}
// ── 删除 ──
function handleDelete(row) {
    dialog.warning({
        title: t('model.delete'),
        content: t('model.deleteConfirm'),
        positiveText: t('common.confirm'),
        negativeText: t('common.cancel'),
        onPositiveClick: async () => {
            try {
                await resourceApi.deleteModel(row.id);
                window.$message?.success(t('model.deleteSuccess'));
                await fetchModels();
            }
            catch { /* 拦截器已处理 */ }
        },
    });
}
// ── 表格列 ──
function formatDate(ts) {
    if (!ts)
        return '-';
    return new Date(ts).toLocaleDateString();
}
const columns = [
    { title: t('model.name'), key: 'name', ellipsis: { tooltip: true } },
    {
        title: t('model.type'),
        key: 'type',
        width: 140,
        render(row) {
            const meta = factories.getModelType(row.type);
            return h(NTag, { size: 'small', bordered: false }, () => meta?.displayName ?? row.type);
        },
    },
    {
        title: t('model.endpoint'),
        key: 'endpoint',
        ellipsis: { tooltip: true },
        render(row) {
            const baseUrl = row.props?.baseUrl ?? row.props?.apiKey ?? '-';
            return h(NText, { depth: 3 }, () => baseUrl);
        },
    },
    {
        title: t('model.updatedAt'),
        key: 'updatedAt',
        width: 120,
        render(row) {
            return formatDate(row.updatedAt);
        },
    },
    {
        title: t('common.action'),
        key: 'actions',
        width: 100,
        render(row) {
            return h(NSpace, { size: 4 }, () => [
                h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => openEdit(row) }, () => '✎'),
                h(NButton, { text: true, type: 'error', size: 'small', onClick: () => handleDelete(row) }, () => '✕'),
            ]);
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
(__VLS_ctx.t('model.title'));
// @ts-ignore
[t,];
var __VLS_3;
let __VLS_6;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    ...{ 'onClick': {} },
    type: "primary",
}));
const __VLS_8 = __VLS_7({
    ...{ 'onClick': {} },
    type: "primary",
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
let __VLS_11;
const __VLS_12 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.openCreate),
};
const { default: __VLS_13 } = __VLS_9.slots;
(__VLS_ctx.t('model.add'));
// @ts-ignore
[t, openCreate,];
var __VLS_9;
var __VLS_10;
let __VLS_14;
/** @ts-ignore @type { | typeof __VLS_components.NDataTable} */
NDataTable;
// @ts-ignore
const __VLS_15 = __VLS_asFunctionalComponent1(__VLS_14, new __VLS_14({
    columns: (__VLS_ctx.columns),
    data: (__VLS_ctx.models),
    loading: (__VLS_ctx.loading),
    bordered: (false),
    singleLine: (false),
    striped: true,
    locale: ({ empty: __VLS_ctx.t('model.noData') }),
}));
const __VLS_16 = __VLS_15({
    columns: (__VLS_ctx.columns),
    data: (__VLS_ctx.models),
    loading: (__VLS_ctx.loading),
    bordered: (false),
    singleLine: (false),
    striped: true,
    locale: ({ empty: __VLS_ctx.t('model.noData') }),
}, ...__VLS_functionalComponentArgsRest(__VLS_15));
let __VLS_19;
/** @ts-ignore @type { | typeof __VLS_components.NDrawer | typeof __VLS_components.NDrawer} */
NDrawer;
// @ts-ignore
const __VLS_20 = __VLS_asFunctionalComponent1(__VLS_19, new __VLS_19({
    show: (__VLS_ctx.drawerVisible),
    width: (480),
    placement: "right",
}));
const __VLS_21 = __VLS_20({
    show: (__VLS_ctx.drawerVisible),
    width: (480),
    placement: "right",
}, ...__VLS_functionalComponentArgsRest(__VLS_20));
const { default: __VLS_24 } = __VLS_22.slots;
let __VLS_25;
/** @ts-ignore @type { | typeof __VLS_components.NDrawerContent | typeof __VLS_components.NDrawerContent} */
NDrawerContent;
// @ts-ignore
const __VLS_26 = __VLS_asFunctionalComponent1(__VLS_25, new __VLS_25({
    title: (__VLS_ctx.isEdit ? __VLS_ctx.t('model.editTitle') : __VLS_ctx.t('model.addTitle')),
    closable: true,
}));
const __VLS_27 = __VLS_26({
    title: (__VLS_ctx.isEdit ? __VLS_ctx.t('model.editTitle') : __VLS_ctx.t('model.addTitle')),
    closable: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_26));
const { default: __VLS_30 } = __VLS_28.slots;
let __VLS_31;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_32 = __VLS_asFunctionalComponent1(__VLS_31, new __VLS_31({
    vertical: true,
    size: (16),
    ...{ style: {} },
}));
const __VLS_33 = __VLS_32({
    vertical: true,
    size: (16),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_32));
const { default: __VLS_36 } = __VLS_34.slots;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
let __VLS_37;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_38 = __VLS_asFunctionalComponent1(__VLS_37, new __VLS_37({
    strong: true,
    ...{ style: {} },
}));
const __VLS_39 = __VLS_38({
    strong: true,
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_38));
const { default: __VLS_42 } = __VLS_40.slots;
(__VLS_ctx.t('model.name'));
// @ts-ignore
[t, t, t, t, columns, models, loading, drawerVisible, isEdit,];
var __VLS_40;
let __VLS_43;
/** @ts-ignore @type { | typeof __VLS_components.NInput} */
NInput;
// @ts-ignore
const __VLS_44 = __VLS_asFunctionalComponent1(__VLS_43, new __VLS_43({
    value: (__VLS_ctx.formName),
    placeholder: (__VLS_ctx.t('model.name')),
}));
const __VLS_45 = __VLS_44({
    value: (__VLS_ctx.formName),
    placeholder: (__VLS_ctx.t('model.name')),
}, ...__VLS_functionalComponentArgsRest(__VLS_44));
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({});
let __VLS_48;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_49 = __VLS_asFunctionalComponent1(__VLS_48, new __VLS_48({
    strong: true,
    ...{ style: {} },
}));
const __VLS_50 = __VLS_49({
    strong: true,
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_49));
const { default: __VLS_53 } = __VLS_51.slots;
(__VLS_ctx.t('model.modelType'));
// @ts-ignore
[t, t, formName,];
var __VLS_51;
let __VLS_54;
/** @ts-ignore @type { | typeof __VLS_components.NSelect} */
NSelect;
// @ts-ignore
const __VLS_55 = __VLS_asFunctionalComponent1(__VLS_54, new __VLS_54({
    ...{ 'onUpdate:value': {} },
    value: (__VLS_ctx.formType),
    options: (__VLS_ctx.typeOptions),
    disabled: (__VLS_ctx.isEdit),
}));
const __VLS_56 = __VLS_55({
    ...{ 'onUpdate:value': {} },
    value: (__VLS_ctx.formType),
    options: (__VLS_ctx.typeOptions),
    disabled: (__VLS_ctx.isEdit),
}, ...__VLS_functionalComponentArgsRest(__VLS_55));
let __VLS_59;
const __VLS_60 = {
    ...{ 'update:value': {} },
    'onUpdate:value': (__VLS_ctx.onTypeChange),
};
var __VLS_57;
var __VLS_58;
if (__VLS_ctx.currentTypeMeta) {
    let __VLS_61;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_62 = __VLS_asFunctionalComponent1(__VLS_61, new __VLS_61({
        depth: "3",
        ...{ style: {} },
    }));
    const __VLS_63 = __VLS_62({
        depth: "3",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_62));
    const { default: __VLS_66 } = __VLS_64.slots;
    (__VLS_ctx.currentTypeMeta.description);
    // @ts-ignore
    [isEdit, formType, typeOptions, onTypeChange, currentTypeMeta, currentTypeMeta,];
    var __VLS_64;
}
if (__VLS_ctx.currentSchema.properties) {
    const __VLS_67 = JsonSchemaForm;
    // @ts-ignore
    const __VLS_68 = __VLS_asFunctionalComponent1(__VLS_67, new __VLS_67({
        modelValue: (__VLS_ctx.formProps),
        schema: (__VLS_ctx.currentSchema),
        isEdit: (__VLS_ctx.isEdit),
    }));
    const __VLS_69 = __VLS_68({
        modelValue: (__VLS_ctx.formProps),
        schema: (__VLS_ctx.currentSchema),
        isEdit: (__VLS_ctx.isEdit),
    }, ...__VLS_functionalComponentArgsRest(__VLS_68));
}
// @ts-ignore
[isEdit, currentSchema, currentSchema, formProps,];
var __VLS_34;
{
    const { footer: __VLS_72 } = __VLS_28.slots;
    let __VLS_73;
    /** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
    NSpace;
    // @ts-ignore
    const __VLS_74 = __VLS_asFunctionalComponent1(__VLS_73, new __VLS_73({}));
    const __VLS_75 = __VLS_74({}, ...__VLS_functionalComponentArgsRest(__VLS_74));
    const { default: __VLS_78 } = __VLS_76.slots;
    let __VLS_79;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_80 = __VLS_asFunctionalComponent1(__VLS_79, new __VLS_79({
        ...{ 'onClick': {} },
    }));
    const __VLS_81 = __VLS_80({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_80));
    let __VLS_84;
    const __VLS_85 = {
        ...{ click: {} },
        onClick: (...[$event]) => {
            __VLS_ctx.drawerVisible = false;
            // @ts-ignore
            [drawerVisible,];
        },
    };
    const { default: __VLS_86 } = __VLS_82.slots;
    (__VLS_ctx.t('common.cancel'));
    // @ts-ignore
    [t,];
    var __VLS_82;
    var __VLS_83;
    let __VLS_87;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_88 = __VLS_asFunctionalComponent1(__VLS_87, new __VLS_87({
        ...{ 'onClick': {} },
        type: "primary",
        loading: (__VLS_ctx.saving),
    }));
    const __VLS_89 = __VLS_88({
        ...{ 'onClick': {} },
        type: "primary",
        loading: (__VLS_ctx.saving),
    }, ...__VLS_functionalComponentArgsRest(__VLS_88));
    let __VLS_92;
    const __VLS_93 = {
        ...{ click: {} },
        onClick: (__VLS_ctx.handleSave),
    };
    const { default: __VLS_94 } = __VLS_90.slots;
    (__VLS_ctx.t('common.save'));
    // @ts-ignore
    [t, saving, handleSave,];
    var __VLS_90;
    var __VLS_91;
    // @ts-ignore
    [];
    var __VLS_76;
    // @ts-ignore
    [];
}
// @ts-ignore
[];
var __VLS_28;
// @ts-ignore
[];
var __VLS_22;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
