<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NInput,
  NInputNumber,
  NSelect,
  NSpace,
  NH2,
  NH3,
  NTabs,
  NTabPane,
  NText,
  NCard,
  NTag,
  NDivider,
  NFormItem,
  NForm,
  NDynamicTags,
  NEmpty,
  NGrid,
  NGi,
  NSwitch,
  NCollapse,
  NCollapseItem,
  NUpload,
  useMessage,
} from 'naive-ui'
import { useI18n } from 'vue-i18n'
import * as agentApi from '@/api/agent'
import * as resourceApi from '@/api/resource'
import { useFactoriesStore } from '@/stores/factories'
import type { AgentDetail, AgentUpsertReq, ToolSpec, SkillRepoSpec, MiddlewareSpec, ModelProvider, ProviderMeta, SkillRepository, WorkspaceSkill } from '@/types'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const message = useMessage()
const factories = useFactoriesStore()

// ── 路由参数 ──
const agentParamId = computed(() => {
  const id = route.params.id as string | undefined
  return id ? Number(id) : null
})
const isEdit = computed(() => !!agentParamId.value)
const saving = ref(false)

// ── 模型选项 ──
const modelOptions = ref<{ label: string; value: number }[]>([])
async function loadModelOptions() {
  try {
    const resp = await resourceApi.listModels()
    modelOptions.value = (resp.data.data ?? []).map((m: ModelProvider) => ({
      label: `${m.name} (${m.type})`,
      value: m.id,
    }))
  } catch { /* ignore */ }
}

// ── 可用工具列表（builtin-tools）──
const builtinTools = ref<ProviderMeta[]>([])
async function loadBuiltinTools() {
  try {
    const resp = await resourceApi.listBuiltinTools()
    builtinTools.value = resp.data.data ?? []
  } catch { /* ignore */ }
}

// ── 可用技能仓库列表 ──
const allSkillRepos = ref<SkillRepository[]>([])
async function loadSkillRepos() {
  try {
    const resp = await resourceApi.listSkillRepos()
    allSkillRepos.value = resp.data.data ?? []
  } catch { /* ignore */ }
}

onMounted(async () => {
  await Promise.all([loadModelOptions(), loadBuiltinTools(), loadSkillRepos(), factories.loadAll()])
  await loadAgent()
  loadWorkspaceSkills()
})

// ── 表单状态 ──
const formAgentId = ref('')
const formName = ref('')
const formDescription = ref('')
const formSysPrompt = ref('')
const formAgentType = ref<'REACT' | 'HARNESS'>('HARNESS')
const formModelId = ref<number | null>(null)
const formMaxIters = ref<number | null>(10)

const formToolSpecs = ref<ToolSpec[]>([])
const formSkillRepos = ref<SkillRepoSpec[]>([])
const formMiddlewareSpecs = ref<MiddlewareSpec[]>([])
const formSubagentRefs = ref<string[]>([])

// ── 加载 Agent（编辑模式）──
async function loadAgent() {
  if (!agentParamId.value) return
  try {
    const resp = await agentApi.getAgent(agentParamId.value)
    const a = resp.data.data
    if (!a) return
    formAgentId.value = a.agentId
    formName.value = a.name ?? ''
    formDescription.value = a.description ?? ''
    formSysPrompt.value = a.sysPrompt ?? ''
    formAgentType.value = a.agentType
    formModelId.value = a.defaultModelProviderId
    formMaxIters.value = a.maxIters ?? 10
    formToolSpecs.value = a.toolSpecs ?? []
    formSkillRepos.value = a.skillRepositories ?? []
    formMiddlewareSpecs.value = a.middlewareSpecs ?? []
    formSubagentRefs.value = a.subagentRefs ?? []
  } catch { /* interceptor */ }
}

// ── 工具选择 ──
const activeToolTypes = computed(() => new Set(formToolSpecs.value.map(s => s.type)))

function toggleTool(tool: ProviderMeta) {
  const idx = formToolSpecs.value.findIndex(s => s.type === tool.type)
  if (idx >= 0) {
    formToolSpecs.value.splice(idx, 1)
  } else {
    formToolSpecs.value.push({ type: tool.type, props: {} })
  }
}

// ── 技能仓库选择（从已创建的技能数据中选）──
const activeSkillRepoIds = computed(() => {
  const set = new Set<number>()
  for (const spec of formSkillRepos.value) {
    const repo = allSkillRepos.value.find(r => r.type === spec.type && JSON.stringify(r.props) === JSON.stringify(spec.props))
    if (repo) set.add(repo.id)
  }
  return set
})

function toggleSkillRepo(repo: SkillRepository) {
  const idx = formSkillRepos.value.findIndex(
    s => s.type === repo.type && JSON.stringify(s.props) === JSON.stringify(repo.props)
  )
  if (idx >= 0) {
    formSkillRepos.value.splice(idx, 1)
  } else {
    formSkillRepos.value.push({ type: repo.type, props: { ...repo.props } })
  }
}

// ── 新建技能仓库 ──
const showSkillForm = ref(false)
const newSkillName = ref('')
const newSkillType = ref<string | null>(null)
const newSkillProps = ref<Record<string, string>>({})
const creatingSkill = ref(false)

const skillTypeOptions = computed(() =>
  factories.skillRepoTypes.map(s => ({ label: `${s.displayName} — ${s.description}`, value: s.type }))
)

function onNewSkillTypeChange() {
  newSkillProps.value = {}
}

async function createSkillRepo() {
  if (!newSkillName.value.trim() || !newSkillType.value) {
    message.warning(t('agent.requiredFields'))
    return
  }
  creatingSkill.value = true
  try {
    const props: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(newSkillProps.value)) {
      if (v.trim()) props[k] = v.trim()
    }
    const resp = await resourceApi.createSkillRepo({ name: newSkillName.value, type: newSkillType.value, props })
    message.success(t('common.saveSuccess'))
    // 重新加载并自动勾选
    await loadSkillRepos()
    const created = resp.data.data
    if (created) {
      formSkillRepos.value.push({ type: created.type, props: { ...created.props } })
    }
    // 重置表单
    newSkillName.value = ''
    newSkillType.value = null
    newSkillProps.value = {}
    showSkillForm.value = false
  } catch { /* interceptor */ } finally {
    creatingSkill.value = false
  }
}

// ── 上传技能压缩包 ──
const uploadingSkills = ref(false)
const uploadSkillName = ref('')
const uploadFile = ref<File | null>(null)

// ── workspace 技能列表 ──
const workspaceSkills = ref<WorkspaceSkill[]>([])
async function loadWorkspaceSkills() {
  if (!agentParamId.value) return
  try {
    const resp = await agentApi.listWorkspaceSkills(agentParamId.value)
    workspaceSkills.value = resp.data.data ?? []
  } catch { /* ignore */ }
}

async function deleteWorkspaceSkill(name: string) {
  if (!agentParamId.value) return
  try {
    await agentApi.deleteWorkspaceSkill(agentParamId.value, name)
    message.success(t('common.saveSuccess'))
    await loadWorkspaceSkills()
  } catch { /* interceptor */ }
}

async function handleSkillUpload() {
  if (!agentParamId.value) {
    message.warning(t('agent.saveBeforeUpload'))
    return
  }
  if (!uploadSkillName.value.trim()) {
    message.warning(t('agent.skillNameRequired'))
    return
  }
  if (!uploadFile.value) {
    message.warning(t('agent.selectZipFile'))
    return
  }
  uploadingSkills.value = true
  try {
    await agentApi.uploadSkill(agentParamId.value, uploadSkillName.value.trim(), uploadFile.value)
    message.success(t('agent.uploadSuccess', { name: uploadSkillName.value.trim() }))
    // Reset & reload
    uploadSkillName.value = ''
    uploadFile.value = null
    await loadWorkspaceSkills()
  } catch { /* interceptor */ } finally {
    uploadingSkills.value = false
  }
}

// ── 中间件选择 ──
const selectedMwType = ref<string | null>(null)
const mwTypeOptions = computed(() =>
  factories.middlewareTypes.map(m => ({ label: `${m.displayName} — ${m.description}`, value: m.type }))
)
function addMiddleware() {
  if (!selectedMwType.value) return
  if (formMiddlewareSpecs.value.some(s => s.type === selectedMwType.value)) {
    message.warning(t('agent.alreadyAdded'))
    return
  }
  formMiddlewareSpecs.value.push({ type: selectedMwType.value, props: {} })
  selectedMwType.value = null
}
function removeMiddleware(idx: number) { formMiddlewareSpecs.value.splice(idx, 1) }

// ── Props JSON 辅助 ──
function propsToStr(props: Record<string, unknown> | undefined): string {
  if (!props || Object.keys(props).length === 0) return ''
  return JSON.stringify(props, null, 2)
}
function updateProps(list: { props?: Record<string, unknown> }[], idx: number, val: string) {
  try { list[idx].props = val.trim() ? JSON.parse(val) : {} } catch { /* editing */ }
}

// ── 保存 ──
function buildReq(): AgentUpsertReq {
  return {
    agentId: formAgentId.value,
    name: formName.value,
    description: formDescription.value || undefined,
    sysPrompt: formSysPrompt.value || undefined,
    agentType: formAgentType.value,
    defaultModelProviderId: formModelId.value!,
    maxIters: formMaxIters.value ?? undefined,
    toolSpecs: formToolSpecs.value.length > 0 ? formToolSpecs.value : undefined,
    skillRepositories: formSkillRepos.value.length > 0 ? formSkillRepos.value : undefined,
    middlewareSpecs: formMiddlewareSpecs.value.length > 0 ? formMiddlewareSpecs.value : undefined,
    subagentRefs: formSubagentRefs.value.length > 0 ? formSubagentRefs.value : undefined,
  }
}

async function handleSave() {
  if (!formAgentId.value || !formName.value || !formModelId.value) {
    message.warning(t('agent.requiredFields'))
    return
  }
  saving.value = true
  try {
    const req = buildReq()
    if (isEdit.value && agentParamId.value) {
      await agentApi.updateAgent(agentParamId.value, req)
      message.success(t('common.saveSuccess'))
    } else {
      const resp = await agentApi.createAgent(req)
      message.success(t('common.saveSuccess'))
      const newId = resp.data.data?.id
      if (newId) router.replace({ name: 'AgentEdit', params: { id: newId } })
    }
  } catch { /* interceptor */ } finally {
    saving.value = false
  }
}

function goBack() { router.push({ name: 'Agents' }) }

const agentTypeOptions = [
  { label: 'Harness', value: 'HARNESS' },
  { label: 'ReAct', value: 'REACT' },
]
</script>

<template>
  <div>
    <!-- ── 顶部栏 ── -->
    <NSpace justify="space-between" align="center" style="margin-bottom: 20px">
      <NSpace align="center" :size="12">
        <NButton quaternary @click="goBack">&larr; {{ t('common.back') }}</NButton>
        <NH2 style="margin: 0">{{ isEdit ? formName || t('agent.edit') : t('agent.create') }}</NH2>
        <NTag v-if="isEdit" size="small" :type="formAgentType === 'HARNESS' ? 'info' : 'success'">{{ formAgentType }}</NTag>
      </NSpace>
      <NSpace>
        <NButton @click="goBack">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" :loading="saving" @click="handleSave">{{ t('common.save') }}</NButton>
      </NSpace>
    </NSpace>

    <!-- ── Tabs ── -->
    <NTabs type="line" animated>
      <!-- ═══ 基本信息 ═══ -->
      <NTabPane name="basic" :tab="t('agent.tabBasic')">
        <div style="max-width: 720px">
          <!-- Agent 标识 -->
          <NCard size="small" :title="t('agent.agentInfo')" style="margin-bottom: 16px">
            <NForm label-placement="left" label-width="120" :show-feedback="false">
              <NFormItem :label="t('agent.agentId')">
                <NInput v-model:value="formAgentId" :disabled="isEdit" placeholder="my-agent" />
              </NFormItem>
              <NFormItem :label="t('common.name')">
                <NInput v-model:value="formName" />
              </NFormItem>
              <NFormItem :label="t('common.description')">
                <NInput v-model:value="formDescription" type="textarea" :rows="2" />
              </NFormItem>
            </NForm>
          </NCard>

          <!-- 运行配置 -->
          <NCard size="small" :title="t('agent.runtimeConfig')" style="margin-bottom: 16px">
            <NForm label-placement="left" label-width="120" :show-feedback="false">
              <NFormItem :label="t('agent.agentType')">
                <NSelect v-model:value="formAgentType" :options="agentTypeOptions" />
              </NFormItem>
              <NFormItem :label="t('agent.model')">
                <NSelect v-model:value="formModelId" :options="modelOptions" :placeholder="t('agent.selectModel')" filterable />
              </NFormItem>
              <NFormItem :label="t('agent.maxIters')">
                <NInputNumber v-model:value="formMaxIters" :min="1" :max="100" style="width: 200px" />
              </NFormItem>
            </NForm>
          </NCard>

          <!-- 系统提示词 -->
          <NCard size="small" :title="t('agent.sysPrompt')">
            <NInput v-model:value="formSysPrompt" type="textarea" :rows="10" :placeholder="t('agent.sysPromptPlaceholder')" />
          </NCard>
        </div>
      </NTabPane>

      <!-- ═══ 工具 ═══ -->
      <NTabPane name="tools" :tab="t('agent.tabTools')">
        <NText depth="3" style="display: block; margin-bottom: 16px">{{ t('agent.toolsHint') }}</NText>
        <NGrid :cols="4" :x-gap="12" :y-gap="12" responsive="screen" item-responsive>
          <NGi v-for="tool in builtinTools" :key="tool.type" :span="1">
            <NCard
              size="small"
              hoverable
              :class="{ 'tool-card-active': activeToolTypes.has(tool.type) }"
              @click="toggleTool(tool)"
              style="cursor: pointer; height: 100%"
            >
              <div style="display: flex; justify-content: space-between; align-items: flex-start">
                <NText strong style="font-size: 14px">{{ tool.displayName }}</NText>
                <NSwitch :value="activeToolTypes.has(tool.type)" size="small" />
              </div>
              <NDivider style="margin: 8px 0" />
              <NText depth="3" style="font-size: 12px">{{ tool.description }}</NText>
            </NCard>
          </NGi>
        </NGrid>
        <NEmpty v-if="!builtinTools.length" :description="t('agent.noTools')" style="padding: 40px 0" />
      </NTabPane>

      <!-- ═══ 技能仓库 ═══ -->
      <NTabPane name="skills" :tab="t('agent.tabSkills')">
        <!-- 上传技能压缩包 -->
        <NCard size="small" style="margin-bottom: 16px; max-width: 720px" :title="t('agent.uploadSkills')">
          <NForm label-placement="left" label-width="80" :show-feedback="false">
            <NFormItem :label="t('agent.skillName')">
              <NInput v-model:value="uploadSkillName" placeholder="my-skill" />
            </NFormItem>
            <NFormItem :label="t('agent.zipFile')">
              <NUpload
                :multiple="false"
                accept=".zip"
                :max="1"
                :default-upload="false"
                @update:file-list="(list: any[]) => { uploadFile = list.length ? list[0].file : null }"
              >
                <NButton :disabled="uploadingSkills">{{ t('agent.selectZipFile') }}</NButton>
              </NUpload>
            </NFormItem>
          </NForm>
          <NSpace justify="end">
            <NButton
              type="primary"
              :loading="uploadingSkills"
              :disabled="!uploadSkillName.trim() || !uploadFile || !isEdit"
              @click="handleSkillUpload"
            >
              {{ t('agent.uploadSkills') }}
            </NButton>
          </NSpace>
          <NText depth="3" style="font-size: 12px; display: block; margin-top: 8px">
            {{ t('agent.uploadFormatHint') }}
          </NText>
          <NText v-if="!isEdit" depth="3" style="font-size: 12px; display: block; margin-top: 4px">
            {{ t('agent.saveBeforeUpload') }}
          </NText>
        </NCard>

        <!-- 已安装的技能 -->
        <div v-if="workspaceSkills.length" style="margin-bottom: 20px">
          <NText strong style="display: block; margin-bottom: 12px; font-size: 14px">{{ t('agent.installedSkills') }}</NText>
          <NGrid :cols="4" :x-gap="12" :y-gap="12" responsive="screen" item-responsive>
            <NGi v-for="skill in workspaceSkills" :key="skill.name" :span="1">
              <NCard size="small" :title="skill.name" closable @close="deleteWorkspaceSkill(skill.name)">
                <NText depth="3" style="font-size: 12px">{{ skill.description || '-' }}</NText>
                <NDivider style="margin: 8px 0" />
                <NTag size="small" :bordered="false">{{ skill.source }}</NTag>
              </NCard>
            </NGi>
          </NGrid>
        </div>

        <!-- 新建技能仓库 -->
        <NCard size="small" style="margin-bottom: 20px; max-width: 720px">
          <div v-if="!showSkillForm">
            <NButton dashed block @click="showSkillForm = true">+ {{ t('agent.createSkillRepo') }}</NButton>
          </div>
          <div v-else>
            <NForm label-placement="left" label-width="80" :show-feedback="false">
              <NFormItem :label="t('common.name')">
                <NInput v-model:value="newSkillName" placeholder="my-skills" />
              </NFormItem>
              <NFormItem :label="t('common.type')">
                <NSelect v-model:value="newSkillType" :options="skillTypeOptions" @update:value="onNewSkillTypeChange" filterable />
              </NFormItem>
              <!-- local 类型 -->
              <template v-if="newSkillType === 'local'">
                <NFormItem label="Path">
                  <NInput v-model:value="newSkillProps['path']" placeholder="skills" />
                  <template #feedback>
                    <NText depth="3" style="font-size: 12px">{{ t('agent.localPathHint') }}</NText>
                  </template>
                </NFormItem>
              </template>
              <!-- git 类型 -->
              <template v-if="newSkillType === 'git'">
                <NFormItem label="Remote URL">
                  <NInput v-model:value="newSkillProps['remoteUrl']" placeholder="https://github.com/..." />
                </NFormItem>
                <NFormItem label="Branch">
                  <NInput v-model:value="newSkillProps['branch']" placeholder="main" />
                </NFormItem>
              </template>
            </NForm>
            <NSpace justify="end">
              <NButton @click="showSkillForm = false">{{ t('common.cancel') }}</NButton>
              <NButton type="primary" :loading="creatingSkill" @click="createSkillRepo">{{ t('common.create') }}</NButton>
            </NSpace>
          </div>
        </NCard>

        <!-- 已有仓库列表（toggle） -->
        <NText depth="3" style="display: block; margin-bottom: 12px">{{ t('agent.skillsHint') }}</NText>
        <NGrid :cols="4" :x-gap="12" :y-gap="12" responsive="screen" item-responsive>
          <NGi v-for="repo in allSkillRepos" :key="repo.id" :span="1">
            <NCard
              size="small"
              hoverable
              :class="{ 'tool-card-active': activeSkillRepoIds.has(repo.id) }"
              @click="toggleSkillRepo(repo)"
              style="cursor: pointer; height: 100%"
            >
              <div style="display: flex; justify-content: space-between; align-items: flex-start">
                <NText strong style="font-size: 14px">{{ repo.name }}</NText>
                <NSwitch :value="activeSkillRepoIds.has(repo.id)" size="small" />
              </div>
              <NDivider style="margin: 8px 0" />
              <NTag size="small" :bordered="false">{{ repo.type }}</NTag>
            </NCard>
          </NGi>
        </NGrid>
        <NEmpty v-if="!allSkillRepos.length && !showSkillForm" :description="t('agent.noSkillReposHint')" style="padding: 40px 0" />
      </NTabPane>

      <!-- ═══ 中间件 ═══ -->
      <NTabPane name="middlewares" :tab="t('agent.tabMiddleware')">
        <div style="max-width: 720px">
          <!-- 添加 -->
          <NCard size="small" :title="t('agent.addMiddleware')" style="margin-bottom: 16px">
            <NSpace align="end">
              <NSelect v-model:value="selectedMwType" :options="mwTypeOptions" :placeholder="t('agent.selectMwType')" style="width: 360px" filterable />
              <NButton type="primary" :disabled="!selectedMwType" @click="addMiddleware">+ {{ t('common.add') }}</NButton>
            </NSpace>
          </NCard>

          <!-- 已选列表 -->
          <NCard v-for="(spec, idx) in formMiddlewareSpecs" :key="idx" size="small" closable @close="removeMiddleware(idx)" style="margin-bottom: 12px">
            <NSpace align="center" style="margin-bottom: 8px">
              <NTag type="warning" size="small">{{ spec.type }}</NTag>
              <NText depth="3">{{ factories.middlewareTypes.find(m => m.type === spec.type)?.description }}</NText>
            </NSpace>
            <NInput :value="propsToStr(spec.props)" @update:value="(v: string) => updateProps(formMiddlewareSpecs, idx, v)" type="textarea" :rows="3" placeholder="{ ... }" />
          </NCard>
          <NEmpty v-if="!formMiddlewareSpecs.length" :description="t('agent.noMiddlewares')" style="padding: 24px 0" />
        </div>
      </NTabPane>

      <!-- ═══ 子 Agent ═══ -->
      <NTabPane name="subagents" :tab="t('agent.tabSubAgents')">
        <div style="max-width: 720px">
          <NText depth="3" style="display: block; margin-bottom: 12px">{{ t('agent.subagentRefsHint') }}</NText>
          <NDynamicTags v-model:value="formSubagentRefs" />
          <NEmpty v-if="!formSubagentRefs.length" :description="t('agent.noSubagents')" style="padding: 24px 0" />
        </div>
      </NTabPane>
    </NTabs>
  </div>
</template>

<style scoped>
.tool-card-active {
  border-color: var(--n-color-primary, #63e2b7) !important;
  box-shadow: 0 0 0 1px var(--n-color-primary, #63e2b7);
}
</style>
