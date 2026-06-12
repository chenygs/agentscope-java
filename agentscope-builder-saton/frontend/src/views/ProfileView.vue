<script setup lang="ts">
import { NCard, NH2, NText, NSpace } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
</script>

<template>
  <div>
    <NH2>{{ t('profile.title') }}</NH2>
    <NCard style="max-width: 500px; margin-top: 24px">
      <NSpace vertical :size="12">
        <div>
          <NText depth="3">{{ t('profile.username') }}: </NText>
          <NText>{{ auth.user?.username ?? '-' }}</NText>
        </div>
      </NSpace>
    </NCard>
  </div>
</template>
