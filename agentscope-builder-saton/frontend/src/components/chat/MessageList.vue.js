import { ref, watch, nextTick } from 'vue';
import { NScrollbar, NSpin, NEmpty, NText } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import MessageBubble from './MessageBubble.vue';
import ToolCallCard from './ToolCallCard.vue';
const props = defineProps();
const { t } = useI18n();
const scrollbarRef = ref(null);
// Auto-scroll to bottom when messages or streaming text changes
watch(() => [props.messages.length, props.streamingText, props.streamingToolCalls.length], async () => {
    await nextTick();
    scrollbarRef.value?.scrollTo({ top: 999999, behavior: 'smooth' });
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
/** @type {__VLS_StyleScopedClasses['message-row']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "message-list-wrapper" },
});
/** @type {__VLS_StyleScopedClasses['message-list-wrapper']} */ ;
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NScrollbar | typeof __VLS_components.NScrollbar} */
NScrollbar;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    ref: "scrollbarRef",
    ...{ class: "message-list-scrollbar" },
}));
const __VLS_2 = __VLS_1({
    ref: "scrollbarRef",
    ...{ class: "message-list-scrollbar" },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
var __VLS_5;
/** @type {__VLS_StyleScopedClasses['message-list-scrollbar']} */ ;
const { default: __VLS_7 } = __VLS_3.slots;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "message-list-inner" },
});
/** @type {__VLS_StyleScopedClasses['message-list-inner']} */ ;
if (__VLS_ctx.messages.length === 0 && !__VLS_ctx.isStreaming) {
    let __VLS_8;
    /** @ts-ignore @type { | typeof __VLS_components.NEmpty} */
    NEmpty;
    // @ts-ignore
    const __VLS_9 = __VLS_asFunctionalComponent1(__VLS_8, new __VLS_8({
        description: "Send a message to start the conversation",
        ...{ style: {} },
    }));
    const __VLS_10 = __VLS_9({
        description: "Send a message to start the conversation",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_9));
}
for (const [msg] of __VLS_vFor((__VLS_ctx.messages))) {
    const __VLS_13 = MessageBubble;
    // @ts-ignore
    const __VLS_14 = __VLS_asFunctionalComponent1(__VLS_13, new __VLS_13({
        key: (msg.id),
        message: (msg),
    }));
    const __VLS_15 = __VLS_14({
        key: (msg.id),
        message: (msg),
    }, ...__VLS_functionalComponentArgsRest(__VLS_14));
    // @ts-ignore
    [messages, messages, isStreaming,];
}
if (__VLS_ctx.isStreaming) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "message-row assistant" },
    });
    /** @type {__VLS_StyleScopedClasses['message-row']} */ ;
    /** @type {__VLS_StyleScopedClasses['assistant']} */ ;
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "avatar" },
    });
    /** @type {__VLS_StyleScopedClasses['avatar']} */ ;
    __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
        ...{ class: "avatar-icon assistant-avatar" },
    });
    /** @type {__VLS_StyleScopedClasses['avatar-icon']} */ ;
    /** @type {__VLS_StyleScopedClasses['assistant-avatar']} */ ;
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "bubble assistant" },
    });
    /** @type {__VLS_StyleScopedClasses['bubble']} */ ;
    /** @type {__VLS_StyleScopedClasses['assistant']} */ ;
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "bubble-header" },
    });
    /** @type {__VLS_StyleScopedClasses['bubble-header']} */ ;
    let __VLS_18;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_19 = __VLS_asFunctionalComponent1(__VLS_18, new __VLS_18({
        strong: true,
        ...{ style: {} },
    }));
    const __VLS_20 = __VLS_19({
        strong: true,
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_19));
    const { default: __VLS_23 } = __VLS_21.slots;
    // @ts-ignore
    [isStreaming,];
    var __VLS_21;
    let __VLS_24;
    /** @ts-ignore @type { | typeof __VLS_components.NSpin} */
    NSpin;
    // @ts-ignore
    const __VLS_25 = __VLS_asFunctionalComponent1(__VLS_24, new __VLS_24({
        size: (12),
        ...{ style: {} },
    }));
    const __VLS_26 = __VLS_25({
        size: (12),
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_25));
    if (__VLS_ctx.streamingToolCalls.length) {
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
            ...{ class: "tool-calls" },
        });
        /** @type {__VLS_StyleScopedClasses['tool-calls']} */ ;
        for (const [tc] of __VLS_vFor((__VLS_ctx.streamingToolCalls))) {
            const __VLS_29 = ToolCallCard;
            // @ts-ignore
            const __VLS_30 = __VLS_asFunctionalComponent1(__VLS_29, new __VLS_29({
                key: (tc.callId),
                toolCall: (tc),
            }));
            const __VLS_31 = __VLS_30({
                key: (tc.callId),
                toolCall: (tc),
            }, ...__VLS_functionalComponentArgsRest(__VLS_30));
            // @ts-ignore
            [streamingToolCalls, streamingToolCalls,];
        }
    }
    if (__VLS_ctx.streamingText) {
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
            ...{ class: "bubble-text" },
        });
        /** @type {__VLS_StyleScopedClasses['bubble-text']} */ ;
        __VLS_asFunctionalElement1(__VLS_intrinsics.pre, __VLS_intrinsics.pre)({
            ...{ class: "message-pre" },
        });
        /** @type {__VLS_StyleScopedClasses['message-pre']} */ ;
        (__VLS_ctx.streamingText);
    }
    if (!__VLS_ctx.streamingText && !__VLS_ctx.streamingToolCalls.length) {
        __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
            ...{ class: "bubble-text" },
        });
        /** @type {__VLS_StyleScopedClasses['bubble-text']} */ ;
        let __VLS_34;
        /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
        NText;
        // @ts-ignore
        const __VLS_35 = __VLS_asFunctionalComponent1(__VLS_34, new __VLS_34({
            depth: "3",
        }));
        const __VLS_36 = __VLS_35({
            depth: "3",
        }, ...__VLS_functionalComponentArgsRest(__VLS_35));
        const { default: __VLS_39 } = __VLS_37.slots;
        // @ts-ignore
        [streamingToolCalls, streamingText, streamingText, streamingText,];
        var __VLS_37;
    }
}
// @ts-ignore
[];
var __VLS_3;
// @ts-ignore
var __VLS_6 = __VLS_5;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({
    __typeProps: {},
});
export default {};
