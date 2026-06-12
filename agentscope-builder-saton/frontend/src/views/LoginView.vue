<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NCard, NForm, NFormItem, NInput, NButton, NH1, NText, NSpace } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const showPassword = ref(false)

async function handleLogin() {
  if (!username.value || !password.value) return
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch {
    // 拦截器已经处理了错误提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <NCard style="max-width: 400px; width: 100%">
      <NSpace vertical :size="24" align="center">
        <NH1 style="text-align: center; margin: 0">{{ t('auth.title') }}</NH1>
        <NForm @submit.prevent="handleLogin" style="width: 100%">
          <NFormItem :label="t('auth.username')">
            <NInput
              v-model:value="username"
              :placeholder="t('auth.username')"
              @keyup.enter="handleLogin"
            />
          </NFormItem>
          <NFormItem :label="t('auth.password')">
            <NInput
              v-model:value="password"
              :type="showPassword ? 'text' : 'password'"
              show-password-on="click"
              :placeholder="t('auth.password')"
              @keyup.enter="handleLogin"
            />
          </NFormItem>
          <NButton
            type="primary"
            block
            :loading="loading"
            @click="handleLogin"
            style="margin-top: 8px"
          >
            {{ loading ? t('auth.loggingIn') : t('auth.login') }}
          </NButton>
        </NForm>
      </NSpace>
    </NCard>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background-color: v-bind("ui.isDark ? '#0a0a0a' : '#fafafa'");
  transition: background-color 0.3s;
}
</style>
