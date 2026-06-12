import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NButton, NSpace, NH3, NText, NSelect, NDivider } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import { useChat } from '@/composables/useChat';
import * as agentApi from '@/api/agent';
import * as modelApi from '@/api/resource';
import MessageList from '@/components/chat/MessageList.vue';
import Composer from '@/components/chat/Composer.vue';
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const agentId = computed(() => Number(route.params.id));
const agent = ref(null);
const models = ref([]);
const { messages, isStreaming, streamingText, streamingToolCalls, overrideModelId, send, abort, clearMessages, } = useChat(agentId.value);
// Load agent info + available models
onMounted(async () => {
    try {
        const [agentResp, modelResp] = await Promise.all([
            agentApi.getAgent(agentId.value),
            modelApi.listModels(),
        ]);
        agent.value = agentResp.data.data;
        models.value = modelResp.data.data ?? [];
    }
    catch {
        // handled by interceptor
    }
});
const modelOptions = computed(() => models.value.map(m => ({ label: m.name, value: m.id })));
function goBack() {
    router.push({ name: 'Agents' });
}
const __VLS_ctx = {
    ...{},
    ...{},
};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "chat-page" },
});
/** @type {__VLS_StyleScopedClasses['chat-page']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "chat-header" },
});
/** @type {__VLS_StyleScopedClasses['chat-header']} */ ;
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    align: "center",
    wrap: (false),
}));
const __VLS_2 = __VLS_1({
    align: "center",
    wrap: (false),
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
const { default: __VLS_5 } = __VLS_3.slots;
let __VLS_6;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    ...{ 'onClick': {} },
    quaternary: true,
}));
const __VLS_8 = __VLS_7({
    ...{ 'onClick': {} },
    quaternary: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
let __VLS_11;
const __VLS_12 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.goBack),
};
const { default: __VLS_13 } = __VLS_9.slots;
(__VLS_ctx.t('common.back'));
// @ts-ignore
[goBack, t,];
var __VLS_9;
var __VLS_10;
let __VLS_14;
/** @ts-ignore @type { | typeof __VLS_components.NDivider} */
NDivider;
// @ts-ignore
const __VLS_15 = __VLS_asFunctionalComponent1(__VLS_14, new __VLS_14({
    vertical: true,
}));
const __VLS_16 = __VLS_15({
    vertical: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_15));
let __VLS_19;
/** @ts-ignore @type { | typeof __VLS_components.NH3 | typeof __VLS_components.NH3} */
NH3;
// @ts-ignore
const __VLS_20 = __VLS_asFunctionalComponent1(__VLS_19, new __VLS_19({
    ...{ style: {} },
}));
const __VLS_21 = __VLS_20({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_20));
const { default: __VLS_24 } = __VLS_22.slots;
(__VLS_ctx.agent?.name ?? __VLS_ctx.t('agent.chat'));
// @ts-ignore
[t, agent,];
var __VLS_22;
if (__VLS_ctx.agent) {
    let __VLS_25;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_26 = __VLS_asFunctionalComponent1(__VLS_25, new __VLS_25({
        depth: "3",
        ...{ style: {} },
    }));
    const __VLS_27 = __VLS_26({
        depth: "3",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_26));
    const { default: __VLS_30 } = __VLS_28.slots;
    (__VLS_ctx.agent.agentId);
    // @ts-ignore
    [agent, agent,];
    var __VLS_28;
}
// @ts-ignore
[];
var __VLS_3;
let __VLS_31;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_32 = __VLS_asFunctionalComponent1(__VLS_31, new __VLS_31({
    align: "center",
    wrap: (false),
}));
const __VLS_33 = __VLS_32({
    align: "center",
    wrap: (false),
}, ...__VLS_functionalComponentArgsRest(__VLS_32));
const { default: __VLS_36 } = __VLS_34.slots;
let __VLS_37;
/** @ts-ignore @type { | typeof __VLS_components.NSelect} */
NSelect;
// @ts-ignore
const __VLS_38 = __VLS_asFunctionalComponent1(__VLS_37, new __VLS_37({
    value: (__VLS_ctx.overrideModelId),
    options: (__VLS_ctx.modelOptions),
    placeholder: (__VLS_ctx.t('chat.modelOverride')),
    clearable: true,
    size: "small",
    ...{ style: {} },
}));
const __VLS_39 = __VLS_38({
    value: (__VLS_ctx.overrideModelId),
    options: (__VLS_ctx.modelOptions),
    placeholder: (__VLS_ctx.t('chat.modelOverride')),
    clearable: true,
    size: "small",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_38));
let __VLS_42;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_43 = __VLS_asFunctionalComponent1(__VLS_42, new __VLS_42({
    ...{ 'onClick': {} },
    size: "small",
    disabled: (__VLS_ctx.isStreaming),
}));
const __VLS_44 = __VLS_43({
    ...{ 'onClick': {} },
    size: "small",
    disabled: (__VLS_ctx.isStreaming),
}, ...__VLS_functionalComponentArgsRest(__VLS_43));
let __VLS_47;
const __VLS_48 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.clearMessages),
};
const { default: __VLS_49 } = __VLS_45.slots;
(__VLS_ctx.t('common.refresh'));
// @ts-ignore
[t, t, overrideModelId, modelOptions, isStreaming, clearMessages,];
var __VLS_45;
var __VLS_46;
// @ts-ignore
[];
var __VLS_34;
const __VLS_50 = MessageList;
// @ts-ignore
const __VLS_51 = __VLS_asFunctionalComponent1(__VLS_50, new __VLS_50({
    messages: (__VLS_ctx.messages),
    isStreaming: (__VLS_ctx.isStreaming),
    streamingText: (__VLS_ctx.streamingText),
    streamingToolCalls: (__VLS_ctx.streamingToolCalls),
    ...{ class: "chat-messages" },
}));
const __VLS_52 = __VLS_51({
    messages: (__VLS_ctx.messages),
    isStreaming: (__VLS_ctx.isStreaming),
    streamingText: (__VLS_ctx.streamingText),
    streamingToolCalls: (__VLS_ctx.streamingToolCalls),
    ...{ class: "chat-messages" },
}, ...__VLS_functionalComponentArgsRest(__VLS_51));
/** @type {__VLS_StyleScopedClasses['chat-messages']} */ ;
const __VLS_55 = Composer;
// @ts-ignore
const __VLS_56 = __VLS_asFunctionalComponent1(__VLS_55, new __VLS_55({
    ...{ 'onSend': {} },
    ...{ 'onAbort': {} },
    isStreaming: (__VLS_ctx.isStreaming),
}));
const __VLS_57 = __VLS_56({
    ...{ 'onSend': {} },
    ...{ 'onAbort': {} },
    isStreaming: (__VLS_ctx.isStreaming),
}, ...__VLS_functionalComponentArgsRest(__VLS_56));
let __VLS_60;
const __VLS_61 = {
    ...{ send: {} },
    onSend: (__VLS_ctx.send),
    ...{ abort: {} },
    onAbort: (__VLS_ctx.abort),
};
var __VLS_58;
var __VLS_59;
// @ts-ignore
[isStreaming, isStreaming, messages, streamingText, streamingToolCalls, send, abort,];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
