<script setup lang="ts">
import { NEmpty, NH2, NText } from 'naive-ui'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div>
    <NH2>{{ t('memory.title') }}</NH2>
    <NEmpty style="margin-top: 60px">
      <template #extra>
        <NText depth="3">记忆功能需要后端 MemoryProvider API 支持，当前暂未开放。</NText>
      </template>
    </NEmpty>
  </div>
</template>
