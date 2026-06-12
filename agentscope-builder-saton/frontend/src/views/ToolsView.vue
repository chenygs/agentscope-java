<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import {
  NH2,
  NButton,
  NSpace,
  NTabs,
  NTabPane,
  NEmpty,
  NDrawer,
  NDrawerContent,
  NInput,
  NSelect,
  NText,
  NSpin,
  NGrid,
  NGi,
  NCard,
  NTag,
  NDivider,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import * as resourceApi from '@/api/resource'
import { useFactoriesStore } from '@/stores/factories'
import type {
  McpServer,
  McpServerUpsertReq,
  SkillRepository,
  SkillRepositoryUpsertReq,
  ProviderMeta,
  JsonSchema,
} from '@/types'
import ResourceCard from '@/components/ResourceCard.vue'
import JsonSchemaForm from '@/components/JsonSchemaForm.vue'

const { t } = useI18n()
const factories = useFactoriesStore()

// ── 当前 tab ──
const activeTab = ref('mcp')

// ── MCP Servers ──
const mcpLoading = ref(false)
const mcpList = ref<McpServer[]>([])

async function loadMcpServers() {
  mcpLoading.value = true
  try {
    const resp = await resourceApi.listMcpServers()
    mcpList.value = resp.data.data ?? []
  } catch { /* interceptor handles */ }
  mcpLoading.value = false
}

// ── Skill Repositories ──
const repoLoading = ref(false)
const repoList = ref<SkillRepository[]>([])

async function loadSkillRepos() {
  repoLoading.value = true
  try {
    const resp = await resourceApi.listSkillRepos()
    repoList.value = resp.data.data ?? []
  } catch { /* interceptor handles */ }
  repoLoading.value = false
}

// ── Builtin Tools (HarnessAgent 内省) ──
const builtinToolsLoading = ref(false)
const builtinTools = ref<ProviderMeta[]>([])

async function loadBuiltinTools() {
  builtinToolsLoading.value = true
  try {
    const resp = await resourceApi.listBuiltinTools()
    builtinTools.value = resp.data.data ?? []
  } catch { /* interceptor handles */ }
  builtinToolsLoading.value = false
}

// ── Drawer ──
const drawerVisible = ref(false)
const drawerMode = ref<'mcp' | 'skill'>('mcp')
const isEdit = ref(false)
const saving = ref(false)

const formName = ref('')
const formType = ref('')
const formProps = ref<Record<string, unknown>>({})
const editingId = ref<number | null>(null)

// ── MCP 固定表单字段 ──
type McpTransport = 'streamable-http' | 'stdio'
type McpAuth = 'none' | 'oauth' | 'header'
const mcpTransport = ref<McpTransport>('streamable-http')
const mcpUrl = ref('')
const mcpAuth = ref<McpAuth>('none')
const mcpAuthToken = ref('')

const transportOptions = [
  { label: 'HTTP', value: 'streamable-http' },
  { label: '标准 IO', value: 'stdio' },
]
const authOptions = [
  { label: 'None', value: 'none' },
  { label: 'OAuth', value: 'oauth' },
  { label: 'Header', value: 'header' },
]

// 技能仓库类型选项
const skillTypeOptions = computed(() =>
  factories.skillRepoTypes.map((t) => ({ label: t.displayName, value: t.type })),
)
const currentSkillTypeMeta = computed<ProviderMeta | undefined>(() =>
  factories.skillRepoTypes.find((t) => t.type === formType.value),
)
const currentSkillSchema = computed<JsonSchema>(() =>
  currentSkillTypeMeta.value?.schema ?? { type: 'object', properties: {} },
)

function onSkillTypeChange() {
  formProps.value = {}
}

// ── 打开新建/编辑 MCP ──
function openCreateMcp() {
  drawerMode.value = 'mcp'
  isEdit.value = false
  editingId.value = null
  formName.value = ''
  mcpTransport.value = 'streamable-http'
  mcpUrl.value = ''
  mcpAuth.value = 'none'
  mcpAuthToken.value = ''
  drawerVisible.value = true
}

function openEditMcp(item: McpServer) {
  drawerMode.value = 'mcp'
  isEdit.value = true
  editingId.value = item.id
  formName.value = item.name
  mcpTransport.value = (item.type as McpTransport) || 'streamable-http'
  mcpUrl.value = (item.props.url as string) || ''
  mcpAuth.value = (item.props.auth as McpAuth) || 'none'
  mcpAuthToken.value = (item.props.authToken as string) || ''
  drawerVisible.value = true
}

// ── 打开新建/编辑 Skill ──
function openCreateSkill() {
  drawerMode.value = 'skill'
  isEdit.value = false
  editingId.value = null
  formName.value = ''
  formType.value = ''
  formProps.value = {}
  drawerVisible.value = true
}

function openEditSkill(item: SkillRepository) {
  drawerMode.value = 'skill'
  isEdit.value = true
  editingId.value = item.id
  formName.value = item.name
  formType.value = item.type
  formProps.value = { ...item.props }
  drawerVisible.value = true
}

// ── 保存 ──
async function handleSave() {
  if (!formName.value.trim()) {
    window.$message?.error(`${t('common.name')} ${t('common.required')}`)
    return
  }

  if (drawerMode.value === 'mcp') {
    // MCP: HTTP 时 URL 必填
    if (mcpTransport.value === 'streamable-http' && !mcpUrl.value.trim()) {
      window.$message?.error(`URL ${t('common.required')}`)
      return
    }
    // 构建 props
    const props: Record<string, unknown> = {}
    if (mcpTransport.value === 'streamable-http') {
      props.url = mcpUrl.value
    }
    if (mcpAuth.value !== 'none') {
      props.auth = mcpAuth.value
      if (mcpAuthToken.value) props.authToken = mcpAuthToken.value
    }

    const req: McpServerUpsertReq = { name: formName.value, type: mcpTransport.value, props }
    saving.value = true
    try {
      if (isEdit.value && editingId.value) {
        await resourceApi.updateMcpServer(editingId.value, req)
      } else {
        await resourceApi.createMcpServer(req)
      }
      window.$message?.success(t('tools.saveSuccess'))
      await loadMcpServers()
      drawerVisible.value = false
    } catch { /* interceptor handles */ }
  } else {
    // Skill Repository
    if (!formType.value) {
      window.$message?.error(`${t('common.type')} ${t('common.required')}`)
      return
    }
    const requiredFields = currentSkillSchema.value.required ?? []
    const schemaProps = currentSkillSchema.value.properties ?? {}
    for (const field of requiredFields) {
      const val = formProps.value[field]
      if (val === undefined || val === null || val === '') {
        const label = schemaProps[field]?.title ?? field
        window.$message?.error(`${label} ${t('common.required')}`)
        return
      }
    }

    const req: SkillRepositoryUpsertReq = { name: formName.value, type: formType.value, props: formProps.value }
    saving.value = true
    try {
      if (isEdit.value && editingId.value) {
        await resourceApi.updateSkillRepo(editingId.value, req)
      } else {
        await resourceApi.createSkillRepo(req)
      }
      window.$message?.success(t('tools.saveSuccess'))
      await loadSkillRepos()
      drawerVisible.value = false
    } catch { /* interceptor handles */ }
  }
  saving.value = false
}

// ── 删除 ──
async function handleDeleteMcp(item: McpServer) {
  try {
    await resourceApi.deleteMcpServer(item.id)
    window.$message?.success(t('tools.deleteSuccess'))
    await loadMcpServers()
  } catch { /* interceptor handles */ }
}

async function handleDeleteSkill(item: SkillRepository) {
  try {
    await resourceApi.deleteSkillRepo(item.id)
    window.$message?.success(t('tools.deleteSuccess'))
    await loadSkillRepos()
  } catch { /* interceptor handles */ }
}

// ── 初始化 ──
onMounted(() => {
  loadMcpServers()
  loadSkillRepos()
  loadBuiltinTools()
})

watch(activeTab, (tab) => {
  if (tab === 'mcp' && mcpList.value.length === 0) loadMcpServers()
  if (tab === 'skill' && repoList.value.length === 0) loadSkillRepos()
  if (tab === 'tools' && builtinTools.value.length === 0) loadBuiltinTools()
})
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <NH2 style="margin: 0">{{ t('tools.title') }}</NH2>
    </div>

    <NTabs v-model:value="activeTab" type="line" animated>
      <!-- ── MCP 服务器 ── -->
      <NTabPane name="mcp" :tab="t('tools.tabMcp')">
        <NSpace justify="end" style="margin-bottom: 16px">
          <NButton @click="loadMcpServers">{{ t('common.refresh') }}</NButton>
          <NButton type="primary" @click="openCreateMcp">+ {{ t('tools.add') }}</NButton>
        </NSpace>
        <NSpin :show="mcpLoading">
          <NGrid v-if="mcpList.length" :cols="4" :x-gap="16" :y-gap="16" responsive="screen"
            item-responsive>
            <NGi v-for="item in mcpList" :key="item.id"
              :span="1">
              <ResourceCard
                :title="item.name"
                :type="item.type"
                :props-summary="item.props"
                @edit="openEditMcp(item)"
                @delete="handleDeleteMcp(item)"
              />
            </NGi>
          </NGrid>
          <NEmpty v-else :description="t('tools.noData')" style="margin-top: 60px" />
        </NSpin>
      </NTabPane>

      <!-- ── 技能仓库 ── -->
      <NTabPane name="skill" :tab="t('tools.tabSkills')">
        <NSpace justify="end" style="margin-bottom: 16px">
          <NButton @click="loadSkillRepos">{{ t('common.refresh') }}</NButton>
          <NButton type="primary" @click="openCreateSkill">+ {{ t('tools.add') }}</NButton>
        </NSpace>
        <NSpin :show="repoLoading">
          <NGrid v-if="repoList.length" :cols="4" :x-gap="16" :y-gap="16" responsive="screen"
            item-responsive>
            <NGi v-for="item in repoList" :key="item.id"
              :span="1">
              <ResourceCard
                :title="item.name"
                :type="item.type"
                :props-summary="item.props"
                @edit="openEditSkill(item)"
                @delete="handleDeleteSkill(item)"
              />
            </NGi>
          </NGrid>
          <NEmpty v-else :description="t('tools.noData')" style="margin-top: 60px" />
        </NSpin>
      </NTabPane>

      <!-- ── 内置工具 (HarnessAgent 内省) ── -->
      <NTabPane name="tools" :tab="t('tools.tabTools')">
        <NSpace justify="end" style="margin-bottom: 16px">
          <NButton @click="loadBuiltinTools">{{ t('common.refresh') }}</NButton>
        </NSpace>
        <NSpin :show="builtinToolsLoading">
          <NGrid v-if="builtinTools.length" :cols="4" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
            <NGi v-for="tool in builtinTools" :key="tool.type" :span="1">
              <NCard size="small" hoverable style="height: 100%">
                <div style="display: flex; justify-content: space-between; align-items: flex-start">
                  <NText strong style="font-size: 15px">{{ tool.displayName }}</NText>
                  <NTag :bordered="false" size="small" type="info">builtin</NTag>
                </div>
                <NDivider style="margin: 12px 0" />
                <NText depth="3" style="font-size: 13px">{{ tool.description }}</NText>
              </NCard>
            </NGi>
          </NGrid>
          <NEmpty v-else :description="t('tools.noData')" style="margin-top: 60px" />
        </NSpin>
      </NTabPane>
    </NTabs>

    <!-- ── 新建/编辑 Drawer ── -->
    <NDrawer v-model:show="drawerVisible" :width="480" placement="right">
      <NDrawerContent
        :title="isEdit ? t('tools.editTitle') : t('tools.addTitle')"
        closable
      >
        <NSpace vertical :size="16" style="width: 100%">
          <!-- 名称 -->
          <div>
            <NText strong style="display: block; margin-bottom: 4px">{{ t('common.name') }}</NText>
            <NInput v-model:value="formName" :placeholder="t('common.name')" />
          </div>

          <!-- ── MCP 固定表单 ── -->
          <template v-if="drawerMode === 'mcp'">
            <!-- Transport -->
            <div>
              <NText strong style="display: block; margin-bottom: 4px">{{ t('tools.transport') }}</NText>
              <NSelect
                v-model:value="mcpTransport"
                :options="transportOptions"
              />
            </div>

            <!-- URL (HTTP 时显示) -->
            <div v-if="mcpTransport === 'streamable-http'">
              <NText strong style="display: block; margin-bottom: 4px">URL</NText>
              <NInput
                v-model:value="mcpUrl"
                placeholder="https://example.com/mcp"
              />
            </div>

            <!-- Authentication -->
            <div>
              <NText strong style="display: block; margin-bottom: 4px">{{ t('tools.authentication') }}</NText>
              <NSelect
                v-model:value="mcpAuth"
                :options="authOptions"
              />
            </div>

            <!-- Auth Token (非 None 时显示) -->
            <div v-if="mcpAuth !== 'none'">
              <NText strong style="display: block; margin-bottom: 4px">Token</NText>
              <NInput
                v-model:value="mcpAuthToken"
                type="password"
                show-password-on="click"
                :placeholder="isEdit ? t('model.placeholderNoChange') : 'Token'"
              />
            </div>
          </template>

          <!-- ── Skill Repository 动态表单 ── -->
          <template v-else>
            <div>
              <NText strong style="display: block; margin-bottom: 4px">{{ t('common.type') }} *</NText>
              <NSelect
                v-model:value="formType"
                :options="skillTypeOptions"
                :disabled="isEdit"
                @update:value="onSkillTypeChange"
              />
            </div>

            <NText v-if="currentSkillTypeMeta" depth="3" style="font-size: 13px">
              {{ currentSkillTypeMeta.description }}
            </NText>

            <JsonSchemaForm
              v-if="currentSkillSchema.properties"
              v-model="formProps"
              :schema="currentSkillSchema"
              :is-edit="isEdit"
            />
          </template>
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
