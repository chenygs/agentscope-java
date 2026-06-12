import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as factoryApi from '@/api/factory'
import type { ProviderMeta } from '@/types'

export const useFactoriesStore = defineStore('factories', () => {
  const modelTypes = ref<ProviderMeta[]>([])
  const toolTypes = ref<ProviderMeta[]>([])
  const skillRepoTypes = ref<ProviderMeta[]>([])
  const middlewareTypes = ref<ProviderMeta[]>([])
  const loaded = ref(false)

  /** 启动时调用一次，拉取所有工厂类型元数据 */
  async function loadAll() {
    if (loaded.value) return
    try {
      const [models, tools, skills, middlewares] = await Promise.all([
        factoryApi.getModelTypes(),
        factoryApi.getToolTypes(),
        factoryApi.getSkillRepoTypes(),
        factoryApi.getMiddlewareTypes(),
      ])
      modelTypes.value = models.data.data ?? []
      toolTypes.value = tools.data.data ?? []
      skillRepoTypes.value = skills.data.data ?? []
      middlewareTypes.value = middlewares.data.data ?? []
      loaded.value = true
    } catch {
      // 拦截器已处理错误提示，此处静默
    }
  }

  /** 根据 type 查找模型类型元信息 */
  function getModelType(type: string): ProviderMeta | undefined {
    return modelTypes.value.find((t) => t.type === type)
  }

  /** 根据 type 查找工具类型元信息 */
  function getToolType(type: string): ProviderMeta | undefined {
    return toolTypes.value.find((t) => t.type === type)
  }

  return {
    modelTypes,
    toolTypes,
    skillRepoTypes,
    middlewareTypes,
    loaded,
    loadAll,
    getModelType,
    getToolType,
  }
})
