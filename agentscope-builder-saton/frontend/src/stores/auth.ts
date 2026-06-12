import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import type { MeResponse } from '@/api/auth'

export const useAuthStore = defineStore(
  'auth',
  () => {
    const user = ref<MeResponse | null>(null)

    const isLoggedIn = computed(() => !!user.value)

    async function login(username: string, password: string) {
      const resp = await authApi.login(username, password)
      // 登录成功后拉 /me 拿用户信息
      await fetchMe()
      return resp.data.data
    }

    async function fetchMe() {
      try {
        const resp = await authApi.getMe()
        user.value = resp.data.data
      } catch {
        user.value = null
      }
    }

    function logout() {
      user.value = null
    }

    return { user, isLoggedIn, login, fetchMe, logout }
  },
  {
    persist: {
      pick: ['user'],
    },
  },
)
