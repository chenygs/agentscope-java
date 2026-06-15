import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NButton, NSpace, NH3, NText, NSelect, NDivider, NLayout, NLayoutSider, NLayoutContent, useDialog, useMessage, useThemeVars, } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import { useChat } from '@/composables/useChat';
import * as agentApi from '@/api/agent';
import * as modelApi from '@/api/resource';
import * as sessionApi from '@/api/session';
import MessageList from '@/components/chat/MessageList.vue';
import Composer from '@/components/chat/Composer.vue';
import SessionList from '@/components/chat/SessionList.vue';
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const dialog = useDialog();
const message = useMessage();
const themeVars = useThemeVars();
const agentId = computed(() => Number(route.params.id));
const agent = ref(null);
const models = ref([]);
const sessions = ref([]);
const sessionsLoading = ref(false);
const { messages, isStreaming, isLoadingHistory, streamingText, streamingToolCalls, overrideModelId, sessionKey, send, abort, clearMessages, loadSession, newSession, } = useChat(agentId.value);
// 拉取一次会话列表
async function refreshSessions() {
    sessionsLoading.value = true;
    try {
        const resp = await sessionApi.listSessions(agentId.value);
        sessions.value = resp.data.data ?? [];
    }
    catch {
        // interceptor 已处理
    }
    finally {
        sessionsLoading.value = false;
    }
}
// 切换到一个已存在会话：拉历史并切 sessionKey
async function onSelect(key) {
    if (key === sessionKey.value)
        return;
    await loadSession(key);
}
// 新建会话：sessionKey 切到新生成的，messages 清空。
// 真正落库要等用户发第一条消息（HarnessAgent 自动建 session）。
function onNew() {
    newSession();
}
// 删除会话：先确认，删完后刷新列表；如果删的是当前选中的，转去新建一个。
function onDelete(key) {
    dialog.warning({
        title: t('chat.deleteSession'),
        content: t('chat.deleteSessionConfirm', { key }),
        positiveText: t('common.confirm'),
        negativeText: t('common.cancel'),
        onPositiveClick: async () => {
            try {
                await sessionApi.resetSession(agentId.value, key);
                message.success(t('chat.deleteSessionOk'));
                if (key === sessionKey.value) {
                    newSession();
                }
                await refreshSessions();
            }
            catch {
                // interceptor 已处理
            }
        },
    });
}
// 进入页面：拉 agent 信息 + 模型列表 + 会话列表，并自动选中第一个或新建一个
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
    await refreshSessions();
    // 默认行为：有会话就选第一个，没有就新建一个临时 key
    if (sessions.value.length > 0) {
        await loadSession(sessions.value[0].sessionKey);
    }
    else {
        newSession();
    }
});
const modelOptions = computed(() => models.value.map(m => ({ label: m.name, value: m.id })));
function goBack() {
    router.push({ name: 'Agents' });
}
// 用户发送一条消息：先做乐观更新（侧栏立刻出现/更新这条会话），再走真正的 stream。
// stream 结束后静默 refresh 一次，与服务器最终一致。
async function onSend(text) {
    const key = sessionKey.value;
    if (key) {
        const idx = sessions.value.findIndex(s => s.sessionKey === key);
        if (idx === -1) {
            // 新会话：插到顶部，标题用本条消息内容
            sessions.value = [
                { sessionKey: key, lastActiveAt: Date.now(), title: text },
                ...sessions.value,
            ];
        }
        else if (!sessions.value[idx].title) {
            // 已存在但 title 为空（罕见兜底）：补上
            sessions.value[idx] = { ...sessions.value[idx], title: text };
        }
    }
    await send(text);
    await refreshSessions();
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
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NLayout | typeof __VLS_components.NLayout} */
NLayout;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    hasSider: true,
    ...{ class: "chat-layout" },
}));
const __VLS_2 = __VLS_1({
    hasSider: true,
    ...{ class: "chat-layout" },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
/** @type {__VLS_StyleScopedClasses['chat-layout']} */ ;
const { default: __VLS_5 } = __VLS_3.slots;
let __VLS_6;
/** @ts-ignore @type { | typeof __VLS_components.NLayoutSider | typeof __VLS_components.NLayoutSider} */
NLayoutSider;
// @ts-ignore
const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
    width: (240),
    nativeScrollbar: (false),
    collapsedWidth: (0),
    showTrigger: "bar",
    bordered: true,
}));
const __VLS_8 = __VLS_7({
    width: (240),
    nativeScrollbar: (false),
    collapsedWidth: (0),
    showTrigger: "bar",
    bordered: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_7));
const { default: __VLS_11 } = __VLS_9.slots;
const __VLS_12 = SessionList;
// @ts-ignore
const __VLS_13 = __VLS_asFunctionalComponent1(__VLS_12, new __VLS_12({
    ...{ 'onSelect': {} },
    ...{ 'onNew': {} },
    ...{ 'onDelete': {} },
    sessions: (__VLS_ctx.sessions),
    activeKey: (__VLS_ctx.sessionKey),
    loading: (__VLS_ctx.sessionsLoading),
}));
const __VLS_14 = __VLS_13({
    ...{ 'onSelect': {} },
    ...{ 'onNew': {} },
    ...{ 'onDelete': {} },
    sessions: (__VLS_ctx.sessions),
    activeKey: (__VLS_ctx.sessionKey),
    loading: (__VLS_ctx.sessionsLoading),
}, ...__VLS_functionalComponentArgsRest(__VLS_13));
let __VLS_17;
const __VLS_18 = {
    ...{ select: {} },
    onSelect: (__VLS_ctx.onSelect),
    ...{ new: {} },
    onNew: (__VLS_ctx.onNew),
    ...{ delete: {} },
    onDelete: (__VLS_ctx.onDelete),
};
var __VLS_15;
var __VLS_16;
// @ts-ignore
[sessions, sessionKey, sessionsLoading, onSelect, onNew, onDelete,];
var __VLS_9;
let __VLS_19;
/** @ts-ignore @type { | typeof __VLS_components.NLayoutContent | typeof __VLS_components.NLayoutContent} */
NLayoutContent;
// @ts-ignore
const __VLS_20 = __VLS_asFunctionalComponent1(__VLS_19, new __VLS_19({
    ...{ class: "chat-main" },
}));
const __VLS_21 = __VLS_20({
    ...{ class: "chat-main" },
}, ...__VLS_functionalComponentArgsRest(__VLS_20));
/** @type {__VLS_StyleScopedClasses['chat-main']} */ ;
const { default: __VLS_24 } = __VLS_22.slots;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "chat-header" },
});
/** @type {__VLS_StyleScopedClasses['chat-header']} */ ;
let __VLS_25;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_26 = __VLS_asFunctionalComponent1(__VLS_25, new __VLS_25({
    align: "center",
    wrap: (false),
}));
const __VLS_27 = __VLS_26({
    align: "center",
    wrap: (false),
}, ...__VLS_functionalComponentArgsRest(__VLS_26));
const { default: __VLS_30 } = __VLS_28.slots;
let __VLS_31;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_32 = __VLS_asFunctionalComponent1(__VLS_31, new __VLS_31({
    ...{ 'onClick': {} },
    quaternary: true,
}));
const __VLS_33 = __VLS_32({
    ...{ 'onClick': {} },
    quaternary: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_32));
let __VLS_36;
const __VLS_37 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.goBack),
};
const { default: __VLS_38 } = __VLS_34.slots;
(__VLS_ctx.t('common.back'));
// @ts-ignore
[goBack, t,];
var __VLS_34;
var __VLS_35;
let __VLS_39;
/** @ts-ignore @type { | typeof __VLS_components.NDivider} */
NDivider;
// @ts-ignore
const __VLS_40 = __VLS_asFunctionalComponent1(__VLS_39, new __VLS_39({
    vertical: true,
}));
const __VLS_41 = __VLS_40({
    vertical: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_40));
let __VLS_44;
/** @ts-ignore @type { | typeof __VLS_components.NH3 | typeof __VLS_components.NH3} */
NH3;
// @ts-ignore
const __VLS_45 = __VLS_asFunctionalComponent1(__VLS_44, new __VLS_44({
    ...{ style: {} },
}));
const __VLS_46 = __VLS_45({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_45));
const { default: __VLS_49 } = __VLS_47.slots;
(__VLS_ctx.agent?.name ?? __VLS_ctx.t('agent.chat'));
// @ts-ignore
[t, agent,];
var __VLS_47;
if (__VLS_ctx.agent) {
    let __VLS_50;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_51 = __VLS_asFunctionalComponent1(__VLS_50, new __VLS_50({
        depth: "3",
        ...{ style: {} },
    }));
    const __VLS_52 = __VLS_51({
        depth: "3",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_51));
    const { default: __VLS_55 } = __VLS_53.slots;
    (__VLS_ctx.agent.agentId);
    // @ts-ignore
    [agent, agent,];
    var __VLS_53;
}
if (__VLS_ctx.sessionKey) {
    let __VLS_56;
    /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
    NText;
    // @ts-ignore
    const __VLS_57 = __VLS_asFunctionalComponent1(__VLS_56, new __VLS_56({
        depth: "3",
        ...{ style: {} },
    }));
    const __VLS_58 = __VLS_57({
        depth: "3",
        ...{ style: {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_57));
    const { default: __VLS_61 } = __VLS_59.slots;
    (__VLS_ctx.sessionKey);
    // @ts-ignore
    [sessionKey, sessionKey,];
    var __VLS_59;
}
// @ts-ignore
[];
var __VLS_28;
let __VLS_62;
/** @ts-ignore @type { | typeof __VLS_components.NSpace | typeof __VLS_components.NSpace} */
NSpace;
// @ts-ignore
const __VLS_63 = __VLS_asFunctionalComponent1(__VLS_62, new __VLS_62({
    align: "center",
    wrap: (false),
}));
const __VLS_64 = __VLS_63({
    align: "center",
    wrap: (false),
}, ...__VLS_functionalComponentArgsRest(__VLS_63));
const { default: __VLS_67 } = __VLS_65.slots;
let __VLS_68;
/** @ts-ignore @type { | typeof __VLS_components.NSelect} */
NSelect;
// @ts-ignore
const __VLS_69 = __VLS_asFunctionalComponent1(__VLS_68, new __VLS_68({
    value: (__VLS_ctx.overrideModelId),
    options: (__VLS_ctx.modelOptions),
    placeholder: (__VLS_ctx.t('chat.modelOverride')),
    clearable: true,
    size: "small",
    ...{ style: {} },
}));
const __VLS_70 = __VLS_69({
    value: (__VLS_ctx.overrideModelId),
    options: (__VLS_ctx.modelOptions),
    placeholder: (__VLS_ctx.t('chat.modelOverride')),
    clearable: true,
    size: "small",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_69));
let __VLS_73;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_74 = __VLS_asFunctionalComponent1(__VLS_73, new __VLS_73({
    ...{ 'onClick': {} },
    size: "small",
    disabled: (__VLS_ctx.isStreaming),
}));
const __VLS_75 = __VLS_74({
    ...{ 'onClick': {} },
    size: "small",
    disabled: (__VLS_ctx.isStreaming),
}, ...__VLS_functionalComponentArgsRest(__VLS_74));
let __VLS_78;
const __VLS_79 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.clearMessages),
};
const { default: __VLS_80 } = __VLS_76.slots;
(__VLS_ctx.t('common.refresh'));
// @ts-ignore
[t, t, overrideModelId, modelOptions, isStreaming, clearMessages,];
var __VLS_76;
var __VLS_77;
// @ts-ignore
[];
var __VLS_65;
if (__VLS_ctx.isLoadingHistory) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "chat-loading" },
        ...{ style: ({ color: __VLS_ctx.themeVars.textColor3 }) },
    });
    /** @type {__VLS_StyleScopedClasses['chat-loading']} */ ;
    (__VLS_ctx.t('chat.loadingHistory'));
}
else {
    const __VLS_81 = MessageList;
    // @ts-ignore
    const __VLS_82 = __VLS_asFunctionalComponent1(__VLS_81, new __VLS_81({
        messages: (__VLS_ctx.messages),
        isStreaming: (__VLS_ctx.isStreaming),
        streamingText: (__VLS_ctx.streamingText),
        streamingToolCalls: (__VLS_ctx.streamingToolCalls),
        ...{ class: "chat-messages" },
    }));
    const __VLS_83 = __VLS_82({
        messages: (__VLS_ctx.messages),
        isStreaming: (__VLS_ctx.isStreaming),
        streamingText: (__VLS_ctx.streamingText),
        streamingToolCalls: (__VLS_ctx.streamingToolCalls),
        ...{ class: "chat-messages" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_82));
    /** @type {__VLS_StyleScopedClasses['chat-messages']} */ ;
}
const __VLS_86 = Composer;
// @ts-ignore
const __VLS_87 = __VLS_asFunctionalComponent1(__VLS_86, new __VLS_86({
    ...{ 'onSend': {} },
    ...{ 'onAbort': {} },
    isStreaming: (__VLS_ctx.isStreaming),
}));
const __VLS_88 = __VLS_87({
    ...{ 'onSend': {} },
    ...{ 'onAbort': {} },
    isStreaming: (__VLS_ctx.isStreaming),
}, ...__VLS_functionalComponentArgsRest(__VLS_87));
let __VLS_91;
const __VLS_92 = {
    ...{ send: {} },
    onSend: (__VLS_ctx.onSend),
    ...{ abort: {} },
    onAbort: (__VLS_ctx.abort),
};
var __VLS_89;
var __VLS_90;
// @ts-ignore
[t, isStreaming, isStreaming, isLoadingHistory, themeVars, messages, streamingText, streamingToolCalls, onSend, abort,];
var __VLS_22;
// @ts-ignore
[];
var __VLS_3;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
