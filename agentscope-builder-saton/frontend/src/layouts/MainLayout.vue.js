import { computed, h } from 'vue';
import { NLayout, NLayoutSider, NLayoutContent, NMenu, NButton, NPopover, NAvatar, NDivider, NSwitch, NText, useThemeVars, } from 'naive-ui';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { useUiStore } from '@/stores/ui';
import { useAuthStore } from '@/stores/auth';
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const ui = useUiStore();
const auth = useAuthStore();
const themeVars = useThemeVars();
// ── 菜单项 ──
function renderIcon(icon) {
    return () => h('span', { style: 'font-size:18px' }, icon);
}
const menuOptions = computed(() => [
    {
        label: t('sidebar.dashboard'),
        key: '/dashboard',
        icon: renderIcon('📊'),
    },
    {
        label: t('sidebar.agents'),
        key: '/agents',
        icon: renderIcon('🤖'),
    },
    {
        label: t('sidebar.models'),
        key: '/models',
        icon: renderIcon('🧠'),
    },
    {
        label: t('sidebar.tools'),
        key: '/tools',
        icon: renderIcon('🔧'),
    },
    {
        label: t('sidebar.memory'),
        key: '/memory',
        icon: renderIcon('💾'),
    },
]);
// ── 当前激活菜单：按路由顶级段匹配 ──
const activeKey = computed(() => {
    const path = route.path;
    if (path.startsWith('/agents'))
        return '/agents';
    if (path.startsWith('/models'))
        return '/models';
    if (path.startsWith('/tools'))
        return '/tools';
    if (path.startsWith('/memory'))
        return '/memory';
    return '/dashboard';
});
function onMenuUpdate(key) {
    router.push(key);
}
// ── 用户 popover 操作 ──
function goProfile() {
    router.push('/profile');
}
function doLogout() {
    auth.logout();
    router.push('/login');
}
const __VLS_ctx = {
    ...{},
    ...{},
};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
/** @type {__VLS_StyleScopedClasses['sidebar-logo']} */ ;
/** @type {__VLS_StyleScopedClasses['sidebar-footer']} */ ;
/** @type {__VLS_StyleScopedClasses['collapsed']} */ ;
/** @type {__VLS_StyleScopedClasses['user-trigger']} */ ;
let __VLS_0;
/** @ts-ignore @type { | typeof __VLS_components.NLayout | typeof __VLS_components.NLayout} */
NLayout;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
    hasSider: true,
    ...{ style: {} },
}));
const __VLS_2 = __VLS_1({
    hasSider: true,
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
var __VLS_5;
const { default: __VLS_6 } = __VLS_3.slots;
let __VLS_7;
/** @ts-ignore @type { | typeof __VLS_components.NLayoutSider | typeof __VLS_components.NLayoutSider} */
NLayoutSider;
// @ts-ignore
const __VLS_8 = __VLS_asFunctionalComponent1(__VLS_7, new __VLS_7({
    ...{ 'onCollapse': {} },
    ...{ 'onExpand': {} },
    bordered: true,
    collapsed: (__VLS_ctx.ui.sidebarCollapsed),
    width: (240),
    collapsedWidth: (64),
    collapseMode: "width",
    showTrigger: true,
    nativeScrollbar: (false),
    ...{ style: {} },
}));
const __VLS_9 = __VLS_8({
    ...{ 'onCollapse': {} },
    ...{ 'onExpand': {} },
    bordered: true,
    collapsed: (__VLS_ctx.ui.sidebarCollapsed),
    width: (240),
    collapsedWidth: (64),
    collapseMode: "width",
    showTrigger: true,
    nativeScrollbar: (false),
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_8));
let __VLS_12;
const __VLS_13 = {
    ...{ collapse: {} },
    onCollapse: (...[$event]) => {
        __VLS_ctx.ui.sidebarCollapsed = true;
        // @ts-ignore
        [ui, ui,];
    },
    ...{ expand: {} },
    onExpand: (...[$event]) => {
        __VLS_ctx.ui.sidebarCollapsed = false;
        // @ts-ignore
        [ui,];
    },
};
const { default: __VLS_14 } = __VLS_10.slots;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "sidebar-logo" },
    ...{ class: ({ collapsed: __VLS_ctx.ui.sidebarCollapsed }) },
});
/** @type {__VLS_StyleScopedClasses['sidebar-logo']} */ ;
/** @type {__VLS_StyleScopedClasses['collapsed']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
    ...{ class: "logo-icon" },
});
/** @type {__VLS_StyleScopedClasses['logo-icon']} */ ;
let __VLS_15;
/** @ts-ignore @type { | typeof __VLS_components.Transition | typeof __VLS_components.Transition} */
Transition;
// @ts-ignore
const __VLS_16 = __VLS_asFunctionalComponent1(__VLS_15, new __VLS_15({
    name: "fade",
}));
const __VLS_17 = __VLS_16({
    name: "fade",
}, ...__VLS_functionalComponentArgsRest(__VLS_16));
const { default: __VLS_20 } = __VLS_18.slots;
if (!__VLS_ctx.ui.sidebarCollapsed) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
        ...{ class: "logo-text" },
    });
    /** @type {__VLS_StyleScopedClasses['logo-text']} */ ;
}
// @ts-ignore
[ui, ui,];
var __VLS_18;
let __VLS_21;
/** @ts-ignore @type { | typeof __VLS_components.NMenu} */
NMenu;
// @ts-ignore
const __VLS_22 = __VLS_asFunctionalComponent1(__VLS_21, new __VLS_21({
    ...{ 'onUpdate:value': {} },
    collapsed: (__VLS_ctx.ui.sidebarCollapsed),
    collapsedWidth: (64),
    collapsedIconSize: (22),
    options: (__VLS_ctx.menuOptions),
    value: (__VLS_ctx.activeKey),
}));
const __VLS_23 = __VLS_22({
    ...{ 'onUpdate:value': {} },
    collapsed: (__VLS_ctx.ui.sidebarCollapsed),
    collapsedWidth: (64),
    collapsedIconSize: (22),
    options: (__VLS_ctx.menuOptions),
    value: (__VLS_ctx.activeKey),
}, ...__VLS_functionalComponentArgsRest(__VLS_22));
let __VLS_26;
const __VLS_27 = {
    ...{ 'update:value': {} },
    'onUpdate:value': (__VLS_ctx.onMenuUpdate),
};
var __VLS_24;
var __VLS_25;
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "sidebar-footer" },
    ...{ class: ({ collapsed: __VLS_ctx.ui.sidebarCollapsed }) },
});
/** @type {__VLS_StyleScopedClasses['sidebar-footer']} */ ;
/** @type {__VLS_StyleScopedClasses['collapsed']} */ ;
let __VLS_28;
/** @ts-ignore @type { | typeof __VLS_components.NDivider} */
NDivider;
// @ts-ignore
const __VLS_29 = __VLS_asFunctionalComponent1(__VLS_28, new __VLS_28({
    ...{ style: {} },
}));
const __VLS_30 = __VLS_29({
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_29));
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "footer-row" },
});
/** @type {__VLS_StyleScopedClasses['footer-row']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
    ...{ class: "footer-icon" },
});
/** @type {__VLS_StyleScopedClasses['footer-icon']} */ ;
if (!__VLS_ctx.ui.sidebarCollapsed) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
        ...{ class: "footer-label" },
    });
    /** @type {__VLS_StyleScopedClasses['footer-label']} */ ;
    (__VLS_ctx.t('sidebar.language'));
}
if (!__VLS_ctx.ui.sidebarCollapsed) {
    let __VLS_33;
    /** @ts-ignore @type { | typeof __VLS_components.NSwitch | typeof __VLS_components.NSwitch} */
    NSwitch;
    // @ts-ignore
    const __VLS_34 = __VLS_asFunctionalComponent1(__VLS_33, new __VLS_33({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.ui.locale === 'en-US'),
        size: "small",
    }));
    const __VLS_35 = __VLS_34({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.ui.locale === 'en-US'),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_34));
    let __VLS_38;
    const __VLS_39 = {
        ...{ 'update:value': {} },
        'onUpdate:value': (...[$event]) => {
            if (!(!__VLS_ctx.ui.sidebarCollapsed))
                return;
            __VLS_ctx.ui.toggleLocale();
            // @ts-ignore
            [ui, ui, ui, ui, ui, ui, menuOptions, activeKey, onMenuUpdate, t,];
        },
    };
    const { default: __VLS_40 } = __VLS_36.slots;
    {
        const { checked: __VLS_41 } = __VLS_36.slots;
        // @ts-ignore
        [];
    }
    {
        const { unchecked: __VLS_42 } = __VLS_36.slots;
        // @ts-ignore
        [];
    }
    // @ts-ignore
    [];
    var __VLS_36;
    var __VLS_37;
}
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "footer-row" },
});
/** @type {__VLS_StyleScopedClasses['footer-row']} */ ;
__VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
    ...{ class: "footer-icon" },
});
/** @type {__VLS_StyleScopedClasses['footer-icon']} */ ;
(__VLS_ctx.ui.isDark ? '🌙' : '☀️');
if (!__VLS_ctx.ui.sidebarCollapsed) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
        ...{ class: "footer-label" },
    });
    /** @type {__VLS_StyleScopedClasses['footer-label']} */ ;
    (__VLS_ctx.t('sidebar.theme'));
}
if (!__VLS_ctx.ui.sidebarCollapsed) {
    let __VLS_43;
    /** @ts-ignore @type { | typeof __VLS_components.NSwitch} */
    NSwitch;
    // @ts-ignore
    const __VLS_44 = __VLS_asFunctionalComponent1(__VLS_43, new __VLS_43({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.ui.isDark),
        size: "small",
    }));
    const __VLS_45 = __VLS_44({
        ...{ 'onUpdate:value': {} },
        value: (__VLS_ctx.ui.isDark),
        size: "small",
    }, ...__VLS_functionalComponentArgsRest(__VLS_44));
    let __VLS_48;
    const __VLS_49 = {
        ...{ 'update:value': {} },
        'onUpdate:value': (...[$event]) => {
            if (!(!__VLS_ctx.ui.sidebarCollapsed))
                return;
            __VLS_ctx.ui.toggleTheme();
            // @ts-ignore
            [ui, ui, ui, ui, ui, t,];
        },
    };
    var __VLS_46;
    var __VLS_47;
}
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "footer-row user-row" },
});
/** @type {__VLS_StyleScopedClasses['footer-row']} */ ;
/** @type {__VLS_StyleScopedClasses['user-row']} */ ;
let __VLS_50;
/** @ts-ignore @type { | typeof __VLS_components.NPopover | typeof __VLS_components.NPopover} */
NPopover;
// @ts-ignore
const __VLS_51 = __VLS_asFunctionalComponent1(__VLS_50, new __VLS_50({
    trigger: "click",
    placement: "top-end",
    width: (160),
}));
const __VLS_52 = __VLS_51({
    trigger: "click",
    placement: "top-end",
    width: (160),
}, ...__VLS_functionalComponentArgsRest(__VLS_51));
const { default: __VLS_55 } = __VLS_53.slots;
{
    const { trigger: __VLS_56 } = __VLS_53.slots;
    __VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
        ...{ class: "user-trigger" },
    });
    /** @type {__VLS_StyleScopedClasses['user-trigger']} */ ;
    let __VLS_57;
    /** @ts-ignore @type { | typeof __VLS_components.NAvatar | typeof __VLS_components.NAvatar} */
    NAvatar;
    // @ts-ignore
    const __VLS_58 = __VLS_asFunctionalComponent1(__VLS_57, new __VLS_57({
        round: true,
        size: "small",
        ...{ style: ({ backgroundColor: '#f97316' }) },
    }));
    const __VLS_59 = __VLS_58({
        round: true,
        size: "small",
        ...{ style: ({ backgroundColor: '#f97316' }) },
    }, ...__VLS_functionalComponentArgsRest(__VLS_58));
    const { default: __VLS_62 } = __VLS_60.slots;
    (__VLS_ctx.auth.user?.username?.charAt(0)?.toUpperCase() ?? 'U');
    // @ts-ignore
    [auth,];
    var __VLS_60;
    if (!__VLS_ctx.ui.sidebarCollapsed) {
        let __VLS_63;
        /** @ts-ignore @type { | typeof __VLS_components.NText | typeof __VLS_components.NText} */
        NText;
        // @ts-ignore
        const __VLS_64 = __VLS_asFunctionalComponent1(__VLS_63, new __VLS_63({
            ...{ class: "username" },
        }));
        const __VLS_65 = __VLS_64({
            ...{ class: "username" },
        }, ...__VLS_functionalComponentArgsRest(__VLS_64));
        /** @type {__VLS_StyleScopedClasses['username']} */ ;
        const { default: __VLS_68 } = __VLS_66.slots;
        (__VLS_ctx.auth.user?.username ?? 'User');
        // @ts-ignore
        [ui, auth,];
        var __VLS_66;
    }
    // @ts-ignore
    [];
}
__VLS_asFunctionalElement1(__VLS_intrinsics.div, __VLS_intrinsics.div)({
    ...{ class: "popover-menu" },
});
/** @type {__VLS_StyleScopedClasses['popover-menu']} */ ;
let __VLS_69;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_70 = __VLS_asFunctionalComponent1(__VLS_69, new __VLS_69({
    ...{ 'onClick': {} },
    quaternary: true,
    block: true,
}));
const __VLS_71 = __VLS_70({
    ...{ 'onClick': {} },
    quaternary: true,
    block: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_70));
let __VLS_74;
const __VLS_75 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.goProfile),
};
const { default: __VLS_76 } = __VLS_72.slots;
(__VLS_ctx.t('auth.profile'));
// @ts-ignore
[t, goProfile,];
var __VLS_72;
var __VLS_73;
let __VLS_77;
/** @ts-ignore @type { | typeof __VLS_components.NButton | typeof __VLS_components.NButton} */
NButton;
// @ts-ignore
const __VLS_78 = __VLS_asFunctionalComponent1(__VLS_77, new __VLS_77({
    ...{ 'onClick': {} },
    quaternary: true,
    block: true,
}));
const __VLS_79 = __VLS_78({
    ...{ 'onClick': {} },
    quaternary: true,
    block: true,
}, ...__VLS_functionalComponentArgsRest(__VLS_78));
let __VLS_82;
const __VLS_83 = {
    ...{ click: {} },
    onClick: (__VLS_ctx.doLogout),
};
const { default: __VLS_84 } = __VLS_80.slots;
(__VLS_ctx.t('auth.logout'));
// @ts-ignore
[t, doLogout,];
var __VLS_80;
var __VLS_81;
// @ts-ignore
[];
var __VLS_53;
// @ts-ignore
[];
var __VLS_10;
var __VLS_11;
let __VLS_85;
/** @ts-ignore @type { | typeof __VLS_components.NLayoutContent | typeof __VLS_components.NLayoutContent} */
NLayoutContent;
// @ts-ignore
const __VLS_86 = __VLS_asFunctionalComponent1(__VLS_85, new __VLS_85({
    nativeScrollbar: (false),
    contentStyle: "padding: 24px;",
    ...{ style: {} },
}));
const __VLS_87 = __VLS_86({
    nativeScrollbar: (false),
    contentStyle: "padding: 24px;",
    ...{ style: {} },
}, ...__VLS_functionalComponentArgsRest(__VLS_86));
const { default: __VLS_90 } = __VLS_88.slots;
let __VLS_91;
/** @ts-ignore @type { | typeof __VLS_components.RouterView} */
RouterView;
// @ts-ignore
const __VLS_92 = __VLS_asFunctionalComponent1(__VLS_91, new __VLS_91({}));
const __VLS_93 = __VLS_92({}, ...__VLS_functionalComponentArgsRest(__VLS_92));
// @ts-ignore
[];
var __VLS_88;
// @ts-ignore
[];
var __VLS_3;
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
