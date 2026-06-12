<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NButton, NResult } from 'naive-ui'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div style="display: flex; align-items: center; justify-content: center; min-height: 100vh">
    <NResult status="404" :title="t('notFound.title')" :description="t('notFound.message')">
      <template #footer>
        <NButton @click="router.push('/')">{{ t('notFound.back') }}</NButton>
      </template>
    </NResult>
  </div>
</template>
