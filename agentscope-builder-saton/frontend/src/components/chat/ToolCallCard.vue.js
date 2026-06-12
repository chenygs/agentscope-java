import { computed } from 'vue';
import { NCard, NTag, NText, NCollapse, NCollapseItem, NCode } from 'naive-ui';
import { useI18n } from 'vue-i18n';
const props = defineProps();
const { t } = useI18n();
const statusType = computed(() => {
    switch (props.toolCall.status) {
        case 'done': return 'success';
        case 'error': return 'error';
        case 'running': return 'warning';
        default: return 'default';
    }
});
const statusLabel = computed(() => {
    switch (props.toolCall.status) {
        case 'done': return t('chat.toolResult');
        case 'error': return t('chat.toolError');
        case 'running': return t('chat.toolCall');
        default: return props.toolCall.status;
    }
});
const __VLS_ctx = {
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
    ...{ class: "tool-call-card" },
    bordered: (true),
}));
const __VLS_2 = __VLS_1({
    size: "small",
    ...{ class: "tool-call-card" },
    bordered: (true),
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
var __VLS_5;
/** @type {__VLS_StyleScopedClasses['tool-call-card']} */ ;
const { default: __VLS_6 } = __VLS_3.slots;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "tool-call-header" },
});
/** @type {__VLS_StyleScopedClasses['tool-call-header']} */ ;
let __VLS_7;
/** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
NTag;
// @ts-ignore
const __VLS_8 = __VLS_asFunctionalComponent1(__VLS_7, new __VLS_7({
    size: "small",
    type: (__VLS_ctx.statusType),
    round: true,
}));
const __VLS_9 = __VLS_8({
    size: "small",
    type: (__VLS_ctx.statusType),
    round: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_8));
const { default: __VLS_12 } = __VLS_10.slots;
(__VLS_ctx.statusLabel);
// @ts-ignore
[statusType, statusLabel,];
var __VLS_10;
let __VLS_13;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_14 = __VLS_asFunctionalComponent1(__VLS_13, new __VLS_13({
    strong: true,
    ...{ style: {} },
}));
const __VLS_15 = __VLS_14({
    strong: true,
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_14));
const { default: __VLS_18 } = __VLS_16.slots;
(__VLS_ctx.toolCall.toolName);
// @ts-ignore
[toolCall,];
var __VLS_16;
if (__VLS_ctx.toolCall.args || __VLS_ctx.toolCall.result) {
    let __VLS_19;
    /** @ts-ignore @type { | typeof __VLS_components.NCollapse | typeof __VLS_components.NCollapse} */
    NCollapse;
    // @ts-ignore
    const __VLS_20 = __VLS_asFunctionalComponent1(__VLS_19, new __VLS_19({
        defaultExpandedNames: ([]),
        arrowPlacement: "left",
    }));
    const __VLS_21 = __VLS_20({
        defaultExpandedNames: ([]),
        arrowPlacement: "left",
    }, ...__VLS_functionalComponentArgsRest(__VLS_20));
    const { default: __VLS_24 } = __VLS_22.slots;
    if (__VLS_ctx.toolCall.args) {
        let __VLS_25;
        /** @ts-ignore @type { | typeof __VLS_components.NCollapseItem | typeof __VLS_components.NCollapseItem} */
        NCollapseItem;
        // @ts-ignore
        const __VLS_26 = __VLS_asFunctionalComponent1(__VLS_25, new __VLS_25({
            title: (__VLS_ctx.t('agent.propsJson')),
            name: "args",
        }));
        const __VLS_27 = __VLS_26({
            title: (__VLS_ctx.t('agent.propsJson')),
            name: "args",
        }, ...__VLS_functionalComponentArgsRest(__VLS_26));
        const { default: __VLS_30 } = __VLS_28.slots;
        let __VLS_31;
        /** @ts-ignore @type { | typeof __VLS_components.NCode} */
        NCode;
        // @ts-ignore
        const __VLS_32 = __VLS_asFunctionalComponent1(__VLS_31, new __VLS_31({
            code: (__VLS_ctx.toolCall.args),
            language: "json",
            wordWrap: true,
        }));
        const __VLS_33 = __VLS_32({
            code: (__VLS_ctx.toolCall.args),
            language: "json",
            wordWrap: true,
        }, ...__VLS_functionalComponentArgsRest(__VLS_32));
        // @ts-ignore
        [toolCall, toolCall, toolCall, toolCall, t,];
        var __VLS_28;
    }
    if (__VLS_ctx.toolCall.result) {
        let __VLS_36;
        /** @ts-ignore @type { | typeof __VLS_components.NCollapseItem | typeof __VLS_components.NCollapseItem} */
        NCollapseItem;
        // @ts-ignore
        const __VLS_37 = __VLS_asFunctionalComponent1(__VLS_36, new __VLS_36({
            title: (__VLS_ctx.t('chat.toolResult')),
            name: "result",
        }));
        const __VLS_38 = __VLS_37({
            title: (__VLS_ctx.t('chat.toolResult')),
            name: "result",
        }, ...__VLS_functionalComponentArgsRest(__VLS_37));
        const { default: __VLS_41 } = __VLS_39.slots;
        let __VLS_42;
        /** @ts-ignore @type { | typeof __VLS_components.NCode} */
        NCode;
        // @ts-ignore
        const __VLS_43 = __VLS_asFunctionalComponent1(__VLS_42, new __VLS_42({
            code: (__VLS_ctx.toolCall.result),
            language: "text",
            wordWrap: true,
        }));
        const __VLS_44 = __VLS_43({
            code: (__VLS_ctx.toolCall.result),
            language: "text",
            wordWrap: true,
        }, ...__VLS_functionalComponentArgsRest(__VLS_43));
        // @ts-ignore
        [toolCall, toolCall, t,];
        var __VLS_39;
    }
    // @ts-ignore
    [];
    var __VLS_22;
}
// @ts-ignore
[];
var __VLS_3;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({
    __typeProps: {},
});
export default {};
