import { ref } from 'vue';
import { NInput, NButton } from 'naive-ui';
import { useI18n } from 'vue-i18n';
const props = defineProps();
const emit = defineEmits();
const { t } = useI18n();
const inputText = ref('');
function handleSend() {
    const text = inputText.value.trim();
    if (!text || props.disabled)
        return;
    emit('send', text);
    inputText.value = '';
}
function handleKeydown(e) {
    // Enter without Shift = send
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
    }
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
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "composer" },
});
/** @type {__VLS_StyleScopedClasses['composer']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "composer-inner" },
});
/** @type {__VLS_StyleScopedClasses['composer-inner']} */ ;
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NInput} */
NInput;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    ...{ 'onKeydown': {} },
    value: (__VLS_ctx.inputText),
    type: "textarea",
    placeholder: (__VLS_ctx.t('chat.placeholder')),
    autosize: ({ minRows: 3, maxRows: 8 }),
    disabled: (__VLS_ctx.disabled),
    ...{ class: "composer-input" },
}));
const __VLS_2 = __VLS_1({
    ...{ 'onKeydown': {} },
    value: (__VLS_ctx.inputText),
    type: "textarea",
    placeholder: (__VLS_ctx.t('chat.placeholder')),
    autosize: ({ minRows: 3, maxRows: 8 }),
    disabled: (__VLS_ctx.disabled),
    ...{ class: "composer-input" },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
let __VLS_5;
const __VLS_6 = {
    ...{ keydown: {} },
    onKeydown: (__VLS_ctx.handleKeydown),
};
/** @type {__VLS_StyleScopedClasses['composer-input']} */ ;
var __VLS_3;
var __VLS_4;
if (__VLS_ctx.isStreaming) {
    let __VLS_7;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_8 = __VLS_asFunctionalComponent1(__VLS_7, new __VLS_7({
        ...{ 'onClick': {} },
        type: "error",
        disabled: (__VLS_ctx.disabled),
        ...{ class: "composer-btn" },
    }));
    const __VLS_9 = __VLS_8({
        ...{ 'onClick': {} },
        type: "error",
        disabled: (__VLS_ctx.disabled),
        ...{ class: "composer-btn" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_8));
    let __VLS_12;
    const __VLS_13 = {
        ...{ click: {} },
        onClick: (...[$event]) => {
            if (!(__VLS_ctx.isStreaming))
                return;
            __VLS_ctx.emit('abort');
            // @ts-ignore
            [inputText, t, disabled, disabled, handleKeydown, isStreaming, emit,];
        },
    };
    /** @type {__VLS_StyleScopedClasses['composer-btn']} */ ;
    const { default: __VLS_14 } = __VLS_10.slots;
    (__VLS_ctx.t('chat.stop'));
    // @ts-ignore
    [t,];
    var __VLS_10;
    var __VLS_11;
}
else {
    let __VLS_15;
    /** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
    NButton;
    // @ts-ignore
    const __VLS_16 = __VLS_asFunctionalComponent1(__VLS_15, new __VLS_15({
        ...{ 'onClick': {} },
        type: "primary",
        disabled: (__VLS_ctx.disabled || !__VLS_ctx.inputText.trim()),
        ...{ class: "composer-btn" },
    }));
    const __VLS_17 = __VLS_16({
        ...{ 'onClick': {} },
        type: "primary",
        disabled: (__VLS_ctx.disabled || !__VLS_ctx.inputText.trim()),
        ...{ class: "composer-btn" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_16));
    let __VLS_20;
    const __VLS_21 = {
        ...{ click: {} },
        onClick: (__VLS_ctx.handleSend),
    };
    /** @type {__VLS_StyleScopedClasses['composer-btn']} */ ;
    const { default: __VLS_22 } = __VLS_18.slots;
    (__VLS_ctx.t('chat.send'));
    // @ts-ignore
    [inputText, t, disabled, handleSend,];
    var __VLS_18;
    var __VLS_19;
}
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({
    __typeEmits: {},
    __typeProps: {},
});
export default {};
