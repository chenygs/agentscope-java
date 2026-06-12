<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import {
  NDataTable,
  NButton,
  NDrawer,
  NDrawerContent,
  NInput,
  NSelect,
  NSpace,
  NH2,
  NEmpty,
  NPopconfirm,
  NText,
  NTag,
  useDialog,
  type DataTableColumns,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import * as resourceApi from '@/api/resource'
import { useFactoriesStore } from '@/stores/factories'
import JsonSchemaForm from '@/components/JsonSchemaForm.vue'
import type { ModelProvider, ModelProviderUpsertReq, ProviderMeta, JsonSchema } from '@/types'

const { t } = useI18n()
const dialog = useDialog()
const factories = useFactoriesStore()

// ── 列表数据 ──
const models = ref<ModelProvider[]>([])
const loading = ref(false)

async function fetchModels() {
  loading.value = true
  try {
    const resp = await resourceApi.listModels()
    models.value = resp.data.data ?? []
  } catch { /* 拦截器已处理 */ } finally {
    loading.value = false
  }
}

onMounted(() => fetchModels())

// ── Drawer 状态 ──
const drawerVisible = ref(false)
const editingModel = ref<ModelProvider | null>(null)
const isEdit = computed(() => !!editingModel.value)

// ── 表单 ──
const formName = ref('')
const formType = ref<string | null>(null)
const formProps = ref<Record<string, unknown>>({})
const saving = ref(false)

// ── 当前选中类型的 schema ──
const currentTypeMeta = computed<ProviderMeta | undefined>(() => {
  if (!formType.value) return undefined
  return factories.getModelType(formType.value)
})

const currentSchema = computed<JsonSchema>(() => {
  return currentTypeMeta.value?.schema as JsonSchema ?? { type: 'object', properties: {} }
})

// ── 类型选择下拉选项 ──
const typeOptions = computed(() =>
  factories.modelTypes.map((m) => ({
    label: `${m.displayName} (${m.type})`,
    value: m.type,
  })),
)

// ── 打开新建 Drawer ──
function openCreate() {
  editingModel.value = null
  formName.value = ''
  formType.value = factories.modelTypes[0]?.type ?? null
  formProps.value = {}
  drawerVisible.value = true
}

// ── 打开编辑 Drawer ──
function openEdit(row: ModelProvider) {
  editingModel.value = row
  formName.value = row.name
  formType.value = row.type
  // 深拷贝 props，避免直接修改 store 数据
  formProps.value = JSON.parse(JSON.stringify(row.props ?? {}))
  drawerVisible.value = true
}

// ── 切换类型时重置 props ──
function onTypeChange() {
  formProps.value = {}
}

// ── 保存 ──
async function handleSave() {
  // 校验基础字段
  if (!formName.value.trim()) {
    window.$message?.error(`${t('model.name')} ${t('common.required')}`)
    return
  }
  if (!formType.value) {
    window.$message?.error(`${t('model.modelType')} ${t('common.required')}`)
    return
  }
  // 校验 schema 中的 required 字段
  const requiredFields = currentSchema.value.required ?? []
  const schemaProps = currentSchema.value.properties ?? {}
  for (const field of requiredFields) {
    const val = formProps.value[field]
    if (val === undefined || val === null || val === '') {
      const label = (schemaProps[field]?.title ?? field)
      window.$message?.error(`${label} ${t('common.required')}`)
      return
    }
  }
  saving.value = true
  const req: ModelProviderUpsertReq = {
    name: formName.value,
    type: formType.value,
    props: formProps.value,
  }
  try {
    if (isEdit.value && editingModel.value) {
      await resourceApi.updateModel(editingModel.value.id, req)
      window.$message?.success(t('model.saveSuccess'))
    } else {
      await resourceApi.createModel(req)
      window.$message?.success(t('model.saveSuccess'))
    }
    drawerVisible.value = false
    await fetchModels()
  } catch { /* 拦截器已处理 */ } finally {
    saving.value = false
  }
}

// ── 删除 ──
function handleDelete(row: ModelProvider) {
  dialog.warning({
    title: t('model.delete'),
    content: t('model.deleteConfirm'),
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        await resourceApi.deleteModel(row.id)
        window.$message?.success(t('model.deleteSuccess'))
        await fetchModels()
      } catch { /* 拦截器已处理 */ }
    },
  })
}

// ── 表格列 ──
function formatDate(ts: number) {
  if (!ts) return '-'
  return new Date(ts).toLocaleDateString()
}

const columns: DataTableColumns<ModelProvider> = [
  { title: t('model.name'), key: 'name', ellipsis: { tooltip: true } },
  {
    title: t('model.type'),
    key: 'type',
    width: 140,
    render(row) {
      const meta = factories.getModelType(row.type)
      return h(NTag, { size: 'small', bordered: false }, () => meta?.displayName ?? row.type)
    },
  },
  {
    title: t('model.endpoint'),
    key: 'endpoint',
    ellipsis: { tooltip: true },
    render(row) {
      const baseUrl = (row.props?.baseUrl as string) ?? (row.props?.apiKey as string) ?? '-'
      return h(NText, { depth: 3 }, () => baseUrl)
    },
  },
  {
    title: t('model.updatedAt'),
    key: 'updatedAt',
    width: 120,
    render(row) {
      return formatDate(row.updatedAt)
    },
  },
  {
    title: t('common.action'),
    key: 'actions',
    width: 100,
    render(row) {
      return h(NSpace, { size: 4 }, () => [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => openEdit(row) }, () => '✎'),
        h(NButton, { text: true, type: 'error', size: 'small', onClick: () => handleDelete(row) }, () => '✕'),
      ])
    },
  },
]
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <NH2 style="margin: 0">{{ t('model.title') }}</NH2>
      <NButton type="primary" @click="openCreate">+ {{ t('model.add') }}</NButton>
    </div>

    <NDataTable
      :columns="columns"
      :data="models"
      :loading="loading"
      :bordered="false"
      :single-line="false"
      striped
      :locale="{ empty: t('model.noData') }"
    />

    <!-- 新建/编辑 Drawer -->
    <NDrawer v-model:show="drawerVisible" :width="480" placement="right">
      <NDrawerContent
        :title="isEdit ? t('model.editTitle') : t('model.addTitle')"
        closable
      >
        <NSpace vertical :size="16" style="width: 100%">
          <!-- 名称 -->
          <div>
            <NText strong style="display: block; margin-bottom: 4px">{{ t('model.name') }} *</NText>
            <NInput v-model:value="formName" :placeholder="t('model.name')" />
          </div>

          <!-- 模型类型选择 -->
          <div>
            <NText strong style="display: block; margin-bottom: 4px">{{ t('model.modelType') }} *</NText>
            <NSelect
              v-model:value="formType"
              :options="typeOptions"
              :disabled="isEdit"
              @update:value="onTypeChange"
            />
          </div>

          <!-- 类型描述 -->
          <NText v-if="currentTypeMeta" depth="3" style="font-size: 13px;">
            {{ currentTypeMeta.description }}
          </NText>

          <!-- 动态表单 -->
          <JsonSchemaForm
            v-if="currentSchema.properties"
            v-model="formProps"
            :schema="currentSchema"
            :is-edit="isEdit"
          />
        </NSpace>

        <template #footer>
          <NSpace>
            <NButton @click="drawerVisible = false">{{ t('common.cancel') }}</NButton>
            <NButton type="primary" :loading="saving" @click="handleSave">
              {{ t('common.save') }}
            </NButton>
          </NSpace>
        </template>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>
