import { computed, watch } from 'vue';
import { NFormItem, NInput, NInputNumber, NSelect, NSwitch, NDynamicTags, NText, } from 'naive-ui';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();
const props = withDefaults(defineProps(), {
    isEdit: false,
});
const emit = defineEmits();
// ── 字段列表（按 properties 顺序） ──
const fields = computed(() => {
    if (!props.schema?.properties)
        return [];
    const required = new Set(props.schema.required ?? []);
    return Object.entries(props.schema.properties).map(([key, prop]) => ({
        key,
        prop,
        required: required.has(key),
    }));
});
// ── 初始化默认值 ──
watch(() => props.schema, (schema) => {
    if (!schema?.properties)
        return;
    const current = { ...props.modelValue };
    let changed = false;
    for (const [key, prop] of Object.entries(schema.properties)) {
        if (current[key] === undefined && prop.default !== undefined) {
            current[key] = prop.default;
            changed = true;
        }
    }
    if (changed)
        emit('update:modelValue', current);
}, { immediate: true });
function updateField(key, value) {
    emit('update:modelValue', { ...props.modelValue, [key]: value });
}
function getFieldValue(key) {
    return props.modelValue[key];
}
function getPlaceholder(prop) {
    if (prop.secret && props.isEdit) {
        return t('model.placeholderNoChange');
    }
    return prop.description ?? '';
}
const __VLS_defaults = {
    isEdit: false,
};
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
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "json-schema-form" },
});
/** @type {__VLS_StyleScopedClasses['json-schema-form']} */ ;
for (const [field] of __VLS_vFor((__VLS_ctx.fields))) {
    __VLS_asFunctionalElement(__VLS_intrinsics.template)({
        key: (field.key),
    });
    let __VLS_0;
    /** @ts-ignore @type { | typeof __VLS_components.NFormItem | typeof __VLS_components.NFormItem} */
    NFormItem;
    // @ts-ignore
    const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
        label: (field.prop.title ?? field.key),
        required: (field.required),
        path: (field.key),
    }));
    const __VLS_2 = __VLS_1({
        label: (field.prop.title ?? field.key),
        required: (field.required),
        path: (field.key),
    }, ...__VLS_functionalComponentArgsRest(__VLS_1));
    const { default: __VLS_5 } = __VLS_3.slots;
    if (field.prop.type === 'string' && field.prop.enum) {
        let __VLS_6;
        /** @ts-ignore @type { | typeof __VLS_components.NSelect} */
        NSelect;
        // @ts-ignore
        const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
            ...{ 'onUpdate:value': {} },
            value: (__VLS_ctx.getFieldValue(field.key) ?? ''),
            options: (field.prop.enum.map(e => ({ label: e, value: e }))),
            clearable: true,
        }));
        const __VLS_8 = __VLS_7({
            ...{ 'onUpdate:value': {} },
            value: (__VLS_ctx.getFieldValue(field.key) ?? ''),
            options: (field.prop.enum.map(e => ({ label: e, value: e }))),
            clearable: true,
        }, ...__VLS_functionalComponentArgsRest(__VLS_7));
        let __VLS_11;
        const __VLS_12 = {
            ...{ 'update:value': {} },
            'onUpdate:value': ((v) => __VLS_ctx.updateField(field.key, v)),
        };
        var __VLS_9;
        var __VLS_10;
    }
    else if (field.prop.type === 'string' && field.prop.secret) {
        let __VLS_13;
        /** @ts-ignore @type { | typeof __VLS_components.NInput} */
        NInput;
        // @ts-ignore
        const __VLS_14 = __VLS_asFunctionalComponent1(__VLS_13, new __VLS_13({
            ...{ 'onUpdate:value': {} },
            type: "password",
            showPasswordOn: "click",
            value: (__VLS_ctx.getFieldValue(field.key) ?? ''),
            placeholder: (__VLS_ctx.getPlaceholder(field.prop)),
        }));
        const __VLS_15 = __VLS_14({
            ...{ 'onUpdate:value': {} },
            type: "password",
            showPasswordOn: "click",
            value: (__VLS_ctx.getFieldValue(field.key) ?? ''),
            placeholder: (__VLS_ctx.getPlaceholder(field.prop)),
        }, ...__VLS_functionalComponentArgsRest(__VLS_14));
        let __VLS_18;
        const __VLS_19 = {
            ...{ 'update:value': {} },
            'onUpdate:value': ((v) => __VLS_ctx.updateField(field.key, v)),
        };
        var __VLS_16;
        var __VLS_17;
    }
    else if (field.prop.type === 'string' && field.prop.format === 'textarea') {
        let __VLS_20;
        /** @ts-ignore @type { | typeof __VLS_components.NInput} */
        NInput;
        // @ts-ignore
        const __VLS_21 = __VLS_asFunctionalComponent1(__VLS_20, new __VLS_20({
            ...{ 'onUpdate:value': {} },
            type: "textarea",
            rows: (3),
            value: (__VLS_ctx.getFieldValue(field.key) ?? ''),
            placeholder: (field.prop.description),
        }));
        const __VLS_22 = __VLS_21({
            ...{ 'onUpdate:value': {} },
            type: "textarea",
            rows: (3),
            value: (__VLS_ctx.getFieldValue(field.key) ?? ''),
            placeholder: (field.prop.description),
        }, ...__VLS_functionalComponentArgsRest(__VLS_21));
        let __VLS_25;
        const __VLS_26 = {
            ...{ 'update:value': {} },
            'onUpdate:value': ((v) => __VLS_ctx.updateField(field.key, v)),
        };
        var __VLS_23;
        var __VLS_24;
    }
    else if (field.prop.type === 'string') {
        let __VLS_27;
        /** @ts-ignore @type { | typeof __VLS_components.NInput} */
        NInput;
        // @ts-ignore
        const __VLS_28 = __VLS_asFunctionalComponent1(__VLS_27, new __VLS_27({
            ...{ 'onUpdate:value': {} },
            value: (__VLS_ctx.getFieldValue(field.key) ?? ''),
            placeholder: (field.prop.description),
        }));
        const __VLS_29 = __VLS_28({
            ...{ 'onUpdate:value': {} },
            value: (__VLS_ctx.getFieldValue(field.key) ?? ''),
            placeholder: (field.prop.description),
        }, ...__VLS_functionalComponentArgsRest(__VLS_28));
        let __VLS_32;
        const __VLS_33 = {
            ...{ 'update:value': {} },
            'onUpdate:value': ((v) => __VLS_ctx.updateField(field.key, v)),
        };
        var __VLS_30;
        var __VLS_31;
    }
    else if (field.prop.type === 'integer' || field.prop.type === 'number') {
        let __VLS_34;
        /** @ts-ignore @type { | typeof __VLS_components.NInputNumber} */
        NInputNumber;
        // @ts-ignore
        const __VLS_35 = __VLS_asFunctionalComponent1(__VLS_34, new __VLS_34({
            ...{ 'onUpdate:value': {} },
            value: (__VLS_ctx.getFieldValue(field.key) ?? undefined),
            placeholder: (field.prop.description),
            ...{ style: {} },
        }));
        const __VLS_36 = __VLS_35({
            ...{ 'onUpdate:value': {} },
            value: (__VLS_ctx.getFieldValue(field.key) ?? undefined),
            placeholder: (field.prop.description),
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_35));
        let __VLS_39;
        const __VLS_40 = {
            ...{ 'update:value': {} },
            'onUpdate:value': ((v) => __VLS_ctx.updateField(field.key, v)),
        };
        var __VLS_37;
        var __VLS_38;
    }
    else if (field.prop.type === 'boolean') {
        let __VLS_41;
        /** @ts-ignore @type { | typeof __VLS_components.NSwitch} */
        NSwitch;
        // @ts-ignore
        const __VLS_42 = __VLS_asFunctionalComponent1(__VLS_41, new __VLS_41({
            ...{ 'onUpdate:value': {} },
            value: (!!__VLS_ctx.getFieldValue(field.key)),
        }));
        const __VLS_43 = __VLS_42({
            ...{ 'onUpdate:value': {} },
            value: (!!__VLS_ctx.getFieldValue(field.key)),
        }, ...__VLS_functionalComponentArgsRest(__VLS_42));
        let __VLS_46;
        const __VLS_47 = {
            ...{ 'update:value': {} },
            'onUpdate:value': ((v) => __VLS_ctx.updateField(field.key, v)),
        };
        var __VLS_44;
        var __VLS_45;
    }
    else if (field.prop.type === 'array' && field.prop.items?.type === 'string') {
        let __VLS_48;
        /** @ts-ignore @type { | typeof __VLS_components.NDynamicTags} */
        NDynamicTags;
        // @ts-ignore
        const __VLS_49 = __VLS_asFunctionalComponent1(__VLS_48, new __VLS_48({
            ...{ 'onUpdate:value': {} },
            value: ((__VLS_ctx.getFieldValue(field.key) ?? [])),
        }));
        const __VLS_50 = __VLS_49({
            ...{ 'onUpdate:value': {} },
            value: ((__VLS_ctx.getFieldValue(field.key) ?? [])),
        }, ...__VLS_functionalComponentArgsRest(__VLS_49));
        let __VLS_53;
        const __VLS_54 = {
            ...{ 'update:value': {} },
            'onUpdate:value': ((v) => __VLS_ctx.updateField(field.key, v)),
        };
        var __VLS_51;
        var __VLS_52;
    }
    else {
        let __VLS_55;
        /** @ts-ignore @type { | typeof __VLS_components.NInput} */
        NInput;
        // @ts-ignore
        const __VLS_56 = __VLS_asFunctionalComponent1(__VLS_55, new __VLS_55({
            ...{ 'onUpdate:value': {} },
            value: (String(__VLS_ctx.getFieldValue(field.key) ?? '')),
            placeholder: (field.prop.description),
        }));
        const __VLS_57 = __VLS_56({
            ...{ 'onUpdate:value': {} },
            value: (String(__VLS_ctx.getFieldValue(field.key) ?? '')),
            placeholder: (field.prop.description),
        }, ...__VLS_functionalComponentArgsRest(__VLS_56));
        let __VLS_60;
        const __VLS_61 = {
            ...{ 'update:value': {} },
            'onUpdate:value': ((v) => __VLS_ctx.updateField(field.key, v)),
        };
        var __VLS_58;
        var __VLS_59;
    }
    // @ts-ignore
    [fields, getFieldValue, getFieldValue, getFieldValue, getFieldValue, getFieldValue, getFieldValue, getFieldValue, getFieldValue, updateField, updateField, updateField, updateField, updateField, updateField, updateField, updateField, getPlaceholder,];
    var __VLS_3;
    if (field.prop.description && !field.prop.secret) {
        let __VLS_62;
        /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
        NText;
        // @ts-ignore
        const __VLS_63 = __VLS_asFunctionalComponent1(__VLS_62, new __VLS_62({
            depth: "3",
            ...{ style: {} },
        }));
        const __VLS_64 = __VLS_63({
            depth: "3",
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_63));
        const { default: __VLS_67 } = __VLS_65.slots;
        (field.prop.description);
        // @ts-ignore
        [];
        var __VLS_65;
    }
    // @ts-ignore
    [];
}
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({
    __typeEmits: {},
    __typeProps: {},
    props: {},
});
export default {};
