<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import {
  NDataTable,
  NButton,
  NSpace,
  NH2,
  NPopconfirm,
  NText,
  NTag,
  useDialog,
  type DataTableColumns,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import * as agentApi from '@/api/agent'
import type { AgentDetail } from '@/types'

const { t } = useI18n()
const dialog = useDialog()
const router = useRouter()

// ── 列表数据 ──
const agents = ref<AgentDetail[]>([])
const loading = ref(false)

async function fetchAgents() {
  loading.value = true
  try {
    const resp = await agentApi.listAgents()
    agents.value = resp.data.data ?? []
  } catch { /* 拦截器已处理 */ } finally {
    loading.value = false
  }
}

onMounted(() => fetchAgents())

async function handleDelete(agent: AgentDetail) {
  try {
    await agentApi.deleteAgent(agent.id)
    await fetchAgents()
  } catch { /* 拦截器已处理 */ }
}

function openEdit(agent: AgentDetail) {
  router.push({ name: 'AgentEdit', params: { id: agent.id } })
}

function openChat(agent: AgentDetail) {
  router.push({ name: 'AgentChat', params: { id: agent.id } })
}

function openCreate() {
  router.push({ name: 'AgentCreate' })
}

// ── 表格列 ──
const columns: DataTableColumns<AgentDetail> = [
  {
    title: 'Agent ID',
    key: 'agentId',
    width: 160,
    ellipsis: { tooltip: true },
  },
  {
    title: t('common.name'),
    key: 'name',
    width: 160,
    ellipsis: { tooltip: true },
  },
  {
    title: t('agent.agentType'),
    key: 'agentType',
    width: 110,
    render(row) {
      return h(NTag, { size: 'small', type: row.agentType === 'HARNESS' ? 'info' : 'success' }, { default: () => row.agentType })
    },
  },
  {
    title: t('common.description'),
    key: 'description',
    ellipsis: { tooltip: true },
  },
  {
    title: t('agent.maxIters'),
    key: 'maxIters',
    width: 90,
    align: 'center',
  },
  {
    title: t('agent.tools'),
    key: 'toolCount',
    width: 80,
    align: 'center',
    render(row) {
      return h('span', {}, String(row.toolSpecs?.length ?? 0))
    },
  },
  {
    title: t('common.action'),
    key: 'actions',
    width: 200,
    render(row) {
      return h(NSpace, { size: 8 }, {
        default: () => [
          h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openChat(row) }, { default: () => t('agent.chat') }),
          h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openEdit(row) }, { default: () => t('common.edit') }),
          h(NPopconfirm, { onPositiveClick: () => handleDelete(row) }, {
            trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => t('common.delete') }),
            default: () => t('agent.deleteConfirm'),
          }),
        ],
      })
    },
  },
]
</script>

<template>
  <div>
    <NSpace justify="space-between" align="center" style="margin-bottom: 16px">
      <NH2 style="margin: 0">{{ t('agent.list') }}</NH2>
      <NSpace>
        <NButton @click="fetchAgents">{{ t('common.refresh') }}</NButton>
        <NButton type="primary" @click="openCreate">+ {{ t('agent.create') }}</NButton>
      </NSpace>
    </NSpace>

    <NDataTable
      :columns="columns"
      :data="agents"
      :loading="loading"
      :row-key="(row: AgentDetail) => row.id"
      striped
    />
  </div>
</template>
