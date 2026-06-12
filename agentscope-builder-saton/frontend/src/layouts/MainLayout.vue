<script setup lang="ts">
import { computed, h, type Component } from 'vue'
import {
  NLayout,
  NLayoutSider,
  NLayoutContent,
  NMenu,
  NButton,
  NPopover,
  NAvatar,
  NSpace,
  NDivider,
  NSwitch,
  NText,
  NIcon,
  useThemeVars,
  type MenuOption,
} from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const auth = useAuthStore()
const themeVars = useThemeVars()

// ── 菜单项 ──
function renderIcon(icon: string) {
  return () => h('span', { style: 'font-size:18px' }, icon)
}

const menuOptions = computed<MenuOption[]>(() => [
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
])

// ── 当前激活菜单：按路由顶级段匹配 ──
const activeKey = computed(() => {
  const path = route.path
  if (path.startsWith('/agents')) return '/agents'
  if (path.startsWith('/models')) return '/models'
  if (path.startsWith('/tools')) return '/tools'
  if (path.startsWith('/memory')) return '/memory'
  return '/dashboard'
})

function onMenuUpdate(key: string) {
  router.push(key)
}

// ── 用户 popover 操作 ──
function goProfile() {
  router.push('/profile')
}

function doLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <NLayout has-sider style="height: 100vh">
    <!-- 侧栏 -->
    <NLayoutSider
      bordered
      :collapsed="ui.sidebarCollapsed"
      :width="240"
      :collapsed-width="64"
      collapse-mode="width"
      show-trigger
      @collapse="ui.sidebarCollapsed = true"
      @expand="ui.sidebarCollapsed = false"
      :native-scrollbar="false"
      style="height: 100vh"
    >
      <!-- Logo -->
      <div class="sidebar-logo" :class="{ collapsed: ui.sidebarCollapsed }">
        <span class="logo-icon">🚀</span>
        <Transition name="fade">
          <span v-if="!ui.sidebarCollapsed" class="logo-text">AgentScope Builder</span>
        </Transition>
      </div>

      <!-- 导航菜单 -->
      <NMenu
        :collapsed="ui.sidebarCollapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :options="menuOptions"
        :value="activeKey"
        @update:value="onMenuUpdate"
      />

      <!-- 底部区域 -->
      <div class="sidebar-footer" :class="{ collapsed: ui.sidebarCollapsed }">
        <NDivider style="margin: 8px 0" />

        <!-- 语言切换 -->
        <div class="footer-row">
          <span class="footer-icon">🌐</span>
          <span v-if="!ui.sidebarCollapsed" class="footer-label">{{ t('sidebar.language') }}</span>
          <NSwitch
            v-if="!ui.sidebarCollapsed"
            :value="ui.locale === 'en-US'"
            @update:value="ui.toggleLocale()"
            size="small"
          >
            <template #checked>EN</template>
            <template #unchecked>中</template>
          </NSwitch>
        </div>

        <!-- 主题切换 -->
        <div class="footer-row">
          <span class="footer-icon">{{ ui.isDark ? '🌙' : '☀️' }}</span>
          <span v-if="!ui.sidebarCollapsed" class="footer-label">{{ t('sidebar.theme') }}</span>
          <NSwitch
            v-if="!ui.sidebarCollapsed"
            :value="ui.isDark"
            @update:value="ui.toggleTheme()"
            size="small"
          />
        </div>

        <!-- 用户头像 -->
        <div class="footer-row user-row">
          <NPopover trigger="click" placement="top-end" :width="160">
            <template #trigger>
              <div class="user-trigger">
                <NAvatar round size="small" :style="{ backgroundColor: '#f97316' }">
                  {{ auth.user?.username?.charAt(0)?.toUpperCase() ?? 'U' }}
                </NAvatar>
                <NText v-if="!ui.sidebarCollapsed" class="username">{{ auth.user?.username ?? 'User' }}</NText>
              </div>
            </template>
            <div class="popover-menu">
              <NButton quaternary block @click="goProfile">{{ t('auth.profile') }}</NButton>
              <NButton quaternary block @click="doLogout">{{ t('auth.logout') }}</NButton>
            </div>
          </NPopover>
        </div>
      </div>
    </NLayoutSider>

    <!-- 主内容区 -->
    <NLayoutContent
      :native-scrollbar="false"
      content-style="padding: 24px;"
      style="height: 100vh"
    >
      <RouterView />
    </NLayoutContent>
  </NLayout>
</template>

<style scoped>
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
}
.sidebar-logo.collapsed {
  justify-content: center;
  padding: 16px 0;
}
.logo-icon {
  font-size: 22px;
  flex-shrink: 0;
}
.logo-text {
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 8px 16px 12px;
}
.sidebar-footer.collapsed {
  padding: 8px 8px 12px;
}

.footer-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 4px;
  font-size: 13px;
}
.footer-icon {
  flex-shrink: 0;
  font-size: 16px;
}
.footer-label {
  flex: 1;
  white-space: nowrap;
}

.user-row {
  margin-top: 4px;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background-color 0.2s;
}
.user-trigger:hover {
  background-color: rgba(128, 128, 128, 0.1);
}
.username {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.popover-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
