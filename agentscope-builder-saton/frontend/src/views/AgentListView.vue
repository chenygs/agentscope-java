<script setup lang="ts">
import { NEmpty, NH2 } from 'naive-ui'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div>
    <NH2>{{ t('agent.list') }}</NH2>
    <NEmpty :description="t('common.loading')" style="margin-top: 60px" />
  </div>
</template>
