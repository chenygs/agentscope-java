import { defineStore } from 'pinia';
import { ref } from 'vue';
import * as factoryApi from '@/api/factory';
export const useFactoriesStore = defineStore('factories', () => {
    const modelTypes = ref([]);
    const toolTypes = ref([]);
    const skillRepoTypes = ref([]);
    const middlewareTypes = ref([]);
    const loaded = ref(false);
    /** 启动时调用一次，拉取所有工厂类型元数据 */
    async function loadAll() {
        if (loaded.value)
            return;
        try {
            const [models, tools, skills, middlewares] = await Promise.all([
                factoryApi.getModelTypes(),
                factoryApi.getToolTypes(),
                factoryApi.getSkillRepoTypes(),
                factoryApi.getMiddlewareTypes(),
            ]);
            modelTypes.value = models.data.data ?? [];
            toolTypes.value = tools.data.data ?? [];
            skillRepoTypes.value = skills.data.data ?? [];
            middlewareTypes.value = middlewares.data.data ?? [];
            loaded.value = true;
        }
        catch {
            // 拦截器已处理错误提示，此处静默
        }
    }
    /** 根据 type 查找模型类型元信息 */
    function getModelType(type) {
        return modelTypes.value.find((t) => t.type === type);
    }
    /** 根据 type 查找工具类型元信息 */
    function getToolType(type) {
        return toolTypes.value.find((t) => t.type === type);
    }
    /** 根据 type 查找技能仓库类型元信息 */
    function getSkillRepoType(type) {
        return skillRepoTypes.value.find((t) => t.type === type);
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
        getSkillRepoType,
    };
});
