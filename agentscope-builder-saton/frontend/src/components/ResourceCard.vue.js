import { NCard, NButton, NSpace, NText, NTag, NPopconfirm, NDivider } from 'naive-ui';
import { useI18n } from 'vue-i18n';
const __VLS_props = defineProps();
const emit = defineEmits();
const { t } = useI18n();
/** 将 props 对象转成简短摘要行，最多显示 3 个 */
function summaryEntries(props) {
    if (!props)
        return [];
    return Object.entries(props)
        .filter(([, v]) => v !== undefined && v !== null && v !== '')
        .slice(0, 3)
        .map(([key, value]) => ({
        key,
        value: typeof value === 'string' ? value : JSON.stringify(value),
    }));
}
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
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NCard | typeof __VLS_components.NCard} */
NCard;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    size: "small",
    hoverable: true,
    ...{ style: {} },
}));
const __VLS_2 = __VLS_1({
    size: "small",
    hoverable: true,
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
var __VLS_5;
const { default: __VLS_6 } = __VLS_3.slots;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ style: {} },
});
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ style: {} },
});
let __VLS_7;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_8 = __VLS_asFunctionalComponent1(__VLS_7, new __VLS_7({
    strong: true,
    ...{ style: {} },
}));
const __VLS_9 = __VLS_8({
    strong: true,
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_8));
const { default: __VLS_12 } = __VLS_10.slots;
(__VLS_ctx.title);
// @ts-ignore
[title,];
var __VLS_10;
let __VLS_13;
/** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
NTag;
// @ts-ignore
const __VLS_14 = __VLS_asFunctionalComponent1(__VLS_13, new __VLS_13({
    bordered: (false),
    size: "small",
    type: "info",
}));
const __VLS_15 = __VLS_14({
    bordered: (false),
    size: "small",
    type: "info",
}, ...__VLS_functionalComponentArgsRest(__VLS_14));
const { default: __VLS_18 } = __VLS_16.slots;
(__VLS_ctx.type);
// @ts-ignore
[type,];
var __VLS_16;
if (__VLS_ctx.summaryEntries(__VLS_ctx.propsSummary).length) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ style: {} },
    });
    for (const [entry] of __VLS_vFor((__VLS_ctx.summaryEntries(__VLS_ctx.propsSummary)))) {
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
            key: (entry.key),
            ...{ style: {} },
        });
        let __VLS_19;
        /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
        NText;
        // @ts-ignore
        const __VLS_20 = __VLS_asFunctionalComponent1(__VLS_19, new __VLS_19({
            depth: "3",
            ...{ style: {} },
        }));
        const __VLS_21 = __VLS_20({
            depth: "3",
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_20));
        const { default: __VLS_24 } = __VLS_22.slots;
        (entry.key);
        // @ts-ignore
        [summaryEntries, summaryEntries, propsSummary, propsSummary,];
        var __VLS_22;
        let __VLS_25;
        /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
        NText;
        // @ts-ignore
        const __VLS_26 = __VLS_asFunctionalComponent1(__VLS_25, new __VLS_25({
            ...{ style: {} },
        }));
        const __VLS_27 = __VLS_26({
            ...{ style: {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_26));
        const { default: __VLS_30 } = __VLS_28.slots;
        (entry.value);
        // @ts-ignore
        [];
        var __VLS_28;
        // @ts-ignore
        [];
    }
}
let __VLS_31;
/** @ts-ignore @type { | typeof __VLS_components.NDivider} */
NDivider;
// @ts-ignore
const __VLS_32 = __VLS_asFunctionalComponent1(__VLS_31, new __VLS_31({
    ...{ style: {} },
}));
const __VLS_33 = __VLS_32({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_32));
let __VLS_36;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_37 = __VLS_asFunctionalComponent1(__VLS_36, new __VLS_36({
    size: (4),
    justify: "end",
}));
const __VLS_38 = __VLS_37({
    size: (4),
    justify: "end",
}, ...__VLS_functionalComponentArgsRest(__VLS_37));
const { default: __VLS_41 } = __VLS_39.slots;
let __VLS_42;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_43 = __VLS_asFunctionalComponent1(__VLS_42, new __VLS_42({
    ...{ 'onClick': {} },
    text: true,
    type: "primary",
    size: "small",
}));
const __VLS_44 = __VLS_43({
    ...{ 'onClick': {} },
    text: true,
    type: "primary",
    size: "small",
}, ...__VLS_functionalComponentArgsRest(__VLS_43));
let __VLS_47;
const __VLS_48 = {
    ...{ click: {} },
    onClick: (...[$event]) => {
        __VLS_ctx.emit('edit');
        // @ts-ignore
        [emit,];
    },
};
const { default: __VLS_49 } = __VLS_45.slots;
(__VLS_ctx.t('common.edit'));
// @ts-ignore
[t,];
var __VLS_45;
var __VLS_46;
let __VLS_50;
/** @ts-ignore @type { | typeof __VLS_components.NPopconfirm | typeof __VLS_components.NPopconfirm} */
NPopconfirm;
// @ts-ignore
const __VLS_51 = __VLS_asFunctionalComponent1(__VLS_50, new __VLS_50({
    ...{ 'onPositiveClick': {} },
}));
const __VLS_52 = __VLS_51({
    ...{ 'onPositiveClick': {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_51));
let __VLS_55;
const __VLS_56 = {
    ...{ positiveClick: {} },
    onPositiveClick: (...[$event]) => {
        __VLS_ctx.emit('delete');
        // @ts-ignore
        [emit,];
    },
};
const { default: __VLS_57 } = __VLS_53.slots;
{
    const { trigger: __VLS_58 } = __VLS_53.slots;
    let __VLS_59;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_60 = __VLS_asFunctionalComponent1(__VLS_59, new __VLS_59({
        text: true,
        type: "error",
        size: "small",
    }));
    const __VLS_61 = __VLS_60({
        text: true,
        type: "error",
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_60));
    const { default: __VLS_64 } = __VLS_62.slots;
    (__VLS_ctx.t('common.delete'));
    // @ts-ignore
    [t,];
    var __VLS_62;
    // @ts-ignore
    [];
}
(__VLS_ctx.t('tools.deleteConfirm'));
// @ts-ignore
[t,];
var __VLS_53;
var __VLS_54;
// @ts-ignore
[];
var __VLS_39;
// @ts-ignore
[];
var __VLS_3;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({
    __typeEmits: {},
    __typeProps: {},
});
export default {};
