import { NText, NTag } from 'naive-ui';
import ToolCallCard from './ToolCallCard.vue';
const __VLS_props = defineProps();
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
/** @type {__VLS_StyleScopedClasses['message-row']} */ ;
/** @type {__VLS_StyleScopedClasses['user']} */ ;
/** @type {__VLS_StyleScopedClasses['bubble']} */ ;
/** @type {__VLS_StyleScopedClasses['message-row']} */ ;
/** @type {__VLS_StyleScopedClasses['user']} */ ;
/** @type {__VLS_StyleScopedClasses['bubble-text']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "message-row" },
    ...{ class: (__VLS_ctx.message.role) },
});
/** @type {__VLS_StyleScopedClasses['message-row']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "avatar" },
});
/** @type {__VLS_StyleScopedClasses['avatar']} */ ;
if (__VLS_ctx.message.role === 'user') {
    __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
        ...{ class: "avatar-icon user-avatar" },
    });
    /** @type {__VLS_StyleScopedClasses['avatar-icon']} */ ;
    /** @type {__VLS_StyleScopedClasses['user-avatar']} */ ;
}
else {
    __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
        ...{ class: "avatar-icon assistant-avatar" },
    });
    /** @type {__VLS_StyleScopedClasses['avatar-icon']} */ ;
    /** @type {__VLS_StyleScopedClasses['assistant-avatar']} */ ;
}
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "bubble" },
    ...{ class: (__VLS_ctx.message.role) },
});
/** @type {__VLS_StyleScopedClasses['bubble']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "bubble-header" },
});
/** @type {__VLS_StyleScopedClasses['bubble-header']} */ ;
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NTag | typeof __VLS_components.NTag} */
NTag;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    size: "small",
    type: (__VLS_ctx.message.role === 'user' ? 'info' : 'success'),
    round: true,
}));
const __VLS_2 = __VLS_1({
    size: "small",
    type: (__VLS_ctx.message.role === 'user' ? 'info' : 'success'),
    round: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
const { default: __VLS_5 } = __VLS_3.slots;
(__VLS_ctx.message.role === 'user' ? 'You' : 'Assistant');
// @ts-ignore
[message, message, message, message, message,];
var __VLS_3;
let __VLS_6;
/** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
NText;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    depth: "3",
    ...{ style: {} },
}));
const __VLS_8 = __VLS_7({
    depth: "3",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
const { default: __VLS_11 } = __VLS_9.slots;
(new Date(__VLS_ctx.message.timestamp).toLocaleTimeString());
// @ts-ignore
[message,];
var __VLS_9;
if (__VLS_ctx.message.text) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "bubble-text" },
    });
    /** @type {__VLS_StyleScopedClasses['bubble-text']} */ ;
    __VLS_asFunctionalElement1(__VLS_intrinsics.pre, __VLS_intrinsics.pre)({
        ...{ class: "message-pre" },
    });
    /** @type {__VLS_StyleScopedClasses['message-pre']} */ ;
    (__VLS_ctx.message.text);
}
if (__VLS_ctx.message.toolCalls?.length) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "tool-calls" },
    });
    /** @type {__VLS_StyleScopedClasses['tool-calls']} */ ;
    for (const [tc] of __VLS_vFor((__VLS_ctx.message.toolCalls))) {
        const __VLS_12 = ToolCallCard;
        // @ts-ignore
        const __VLS_13 = __VLS_asFunctionalComponent1(__VLS_12, new __VLS_12({
            key: (tc.callId),
            toolCall: (tc),
        }));
        const __VLS_14 = __VLS_13({
            key: (tc.callId),
            toolCall: (tc),
        }, ...__VLS_functionalComponentArgsRest(__VLS_13));
        // @ts-ignore
        [message, message, message, message,];
    }
}
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({
    __typeProps: {},
});
export default {};
