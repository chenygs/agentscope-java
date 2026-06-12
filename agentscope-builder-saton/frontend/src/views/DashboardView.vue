<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NButton, NSpace, NH1, NText, NP } from 'naive-ui'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div class="dashboard-container">
    <NSpace vertical :size="24" align="center" style="text-align: center">
      <NH1 style="margin: 0; font-size: 36px">{{ t('dashboard.title') }}</NH1>
      <NP style="font-size: 18px; opacity: 0.7">{{ t('dashboard.subtitle') }}</NP>
      <NButton type="primary" size="large" @click="router.push('/agents/new')">
        + {{ t('dashboard.cta') }}
      </NButton>
      <NButton text type="primary" @click="router.push('/agents')">
        {{ t('dashboard.secondary') }}
      </NButton>
    </NSpace>
  </div>
</template>

<style scoped>
.dashboard-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 100px);
}
</style>
