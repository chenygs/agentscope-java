<script setup lang="ts">
import { computed, watch } from 'vue'
import {
  NFormItem,
  NInput,
  NInputNumber,
  NSelect,
  NSwitch,
  NDynamicTags,
  NText,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import type { JsonSchema, JsonSchemaProperty } from '@/types'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  schema: JsonSchema
  modelValue: Record<string, unknown>
  isEdit?: boolean
}>(), {
  isEdit: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
}>()

// ── 字段列表（按 properties 顺序） ──
const fields = computed(() => {
  if (!props.schema?.properties) return []
  const required = new Set(props.schema.required ?? [])
  return Object.entries(props.schema.properties).map(([key, prop]) => ({
    key,
    prop,
    required: required.has(key),
  }))
})

// ── 初始化默认值 ──
watch(
  () => props.schema,
  (schema) => {
    if (!schema?.properties) return
    const current = { ...props.modelValue }
    let changed = false
    for (const [key, prop] of Object.entries(schema.properties)) {
      if (current[key] === undefined && prop.default !== undefined) {
        current[key] = prop.default
        changed = true
      }
    }
    if (changed) emit('update:modelValue', current)
  },
  { immediate: true },
)

function updateField(key: string, value: unknown) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

function getFieldValue(key: string): unknown {
  return props.modelValue[key]
}

function getPlaceholder(prop: JsonSchemaProperty): string {
  if (prop.secret && props.isEdit) {
    return t('model.placeholderNoChange')
  }
  return prop.description ?? ''
}
</script>

<template>
  <div class="json-schema-form">
    <template v-for="field in fields" :key="field.key">
      <NFormItem
        :label="field.prop.title ?? field.key"
        :required="field.required"
        :path="field.key"
      >
        <!-- string + enum → NSelect -->
        <NSelect
          v-if="field.prop.type === 'string' && field.prop.enum"
          :value="(getFieldValue(field.key) as string) ?? ''"
          :options="field.prop.enum.map(e => ({ label: e, value: e }))"
          @update:value="(v: string) => updateField(field.key, v)"
          clearable
        />

        <!-- string + secret → NInput password -->
        <NInput
          v-else-if="field.prop.type === 'string' && field.prop.secret"
          type="password"
          show-password-on="click"
          :value="(getFieldValue(field.key) as string) ?? ''"
          :placeholder="getPlaceholder(field.prop)"
          @update:value="(v: string) => updateField(field.key, v)"
        />

        <!-- string + textarea → NInput textarea -->
        <NInput
          v-else-if="field.prop.type === 'string' && field.prop.format === 'textarea'"
          type="textarea"
          :rows="3"
          :value="(getFieldValue(field.key) as string) ?? ''"
          :placeholder="field.prop.description"
          @update:value="(v: string) => updateField(field.key, v)"
        />

        <!-- string (default) → NInput -->
        <NInput
          v-else-if="field.prop.type === 'string'"
          :value="(getFieldValue(field.key) as string) ?? ''"
          :placeholder="field.prop.description"
          @update:value="(v: string) => updateField(field.key, v)"
        />

        <!-- integer / number → NInputNumber -->
        <NInputNumber
          v-else-if="field.prop.type === 'integer' || field.prop.type === 'number'"
          :value="(getFieldValue(field.key) as number) ?? undefined"
          :placeholder="field.prop.description"
          @update:value="(v: number | null) => updateField(field.key, v)"
          style="width: 100%"
        />

        <!-- boolean → NSwitch -->
        <NSwitch
          v-else-if="field.prop.type === 'boolean'"
          :value="!!getFieldValue(field.key)"
          @update:value="(v: boolean) => updateField(field.key, v)"
        />

        <!-- array of strings → NDynamicTags -->
        <NDynamicTags
          v-else-if="field.prop.type === 'array' && field.prop.items?.type === 'string'"
          :value="((getFieldValue(field.key) as string[]) ?? [])"
          @update:value="(v: string[]) => updateField(field.key, v)"
        />

        <!-- fallback: NInput -->
        <NInput
          v-else
          :value="String(getFieldValue(field.key) ?? '')"
          :placeholder="field.prop.description"
          @update:value="(v: string) => updateField(field.key, v)"
        />
      </NFormItem>

      <!-- description hint -->
      <NText
        v-if="field.prop.description && !field.prop.secret"
        depth="3"
        style="font-size: 12px; margin-top: -8px; margin-bottom: 8px; display: block;"
      >
        {{ field.prop.description }}
      </NText>
    </template>
  </div>
</template>

<style scoped>
.json-schema-form {
  display: flex;
  flex-direction: column;
}
</style>
