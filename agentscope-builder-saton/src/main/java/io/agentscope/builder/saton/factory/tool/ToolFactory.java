package io.agentscope.builder.saton.factory.tool;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class ToolFactory {

    private static final Logger log = LoggerFactory.getLogger(ToolFactory.class);

    private final ToolProviderTypeRegistry registry;
    private final ApplicationContext appCtx;

    public ToolFactory(ToolProviderTypeRegistry registry, ApplicationContext appCtx) {
        this.registry = registry;
        this.appCtx = appCtx;
    }

    public List<TypeMeta> listTypes() {
        return registry.listMetas();
    }

    public Object instantiate(String type, Map<String, Object> props) {
        ToolType t;
        try {
            t = registry.get(type);
        } catch (NoSuchElementException e) {
            throw new NotFoundException("unknown tool type: " + type);
        }
        return t.instantiate(props != null ? props : Map.of());
    }

    /**
     * 通过构建临时 HarnessAgent 内省所有工具：
     * <ol>
     *   <li>core 内置工具（filesystem/shell/memory/todo 等）—— HarnessAgent.build() 自动注册</li>
     *   <li>用户自定义 Spring bean 中带 {@code @Tool} 方法的组件</li>
     * </ol>
     * 跟 dataagent 的 AgentToolsController 同样的模式。
     */
    public List<TypeMeta> listBuiltinTools() {
        try {
            Path tmpWorkspace = Files.createTempDirectory("tool-introspect-");
            HarnessAgent agent = HarnessAgent.builder()
                    .name("__tool_introspect__")
                    .model(new NoopModel())
                    .workspace(tmpWorkspace)
                    .build();
            Toolkit toolkit = agent.getDelegate().getToolkit();

            // 把用户自定义的 @Tool Spring beans 也注册进去
            registerCustomToolBeans(toolkit);

            List<ToolSchema> schemas = toolkit.getToolSchemas();
            return schemas.stream()
                    .map(s -> new TypeMeta(
                            s.getName(),
                            s.getName(),
                            s.getDescription(),
                            s.getParameters() != null ? s.getParameters() : Map.of()
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to introspect builtin tools: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 从 Spring 容器中发现所有工具 bean，注册到 toolkit。
     * <p>
     * 两类工具：
     * <ol>
     *   <li>{@link AgentTool} 实现（含 ToolBase 子类）—— 用 {@code getBeansOfType} 直接拿</li>
     *   <li>带 {@code @Tool} 方法的 POJO —— 遍历 bean 检查注解</li>
     * </ol>
     * 注册后由 {@code Toolkit} 统一处理 schema 生成（@ToolParam / getName / getParameters 等）。
     */
    private void registerCustomToolBeans(Toolkit toolkit) {
        // 1) AgentTool / ToolBase 子类 —— Spring 原生类型查找
        appCtx.getBeansOfType(AgentTool.class).forEach((name, bean) -> {
            toolkit.registerTool(bean);
            log.debug("Registered AgentTool bean: {} ({})", name, bean.getClass().getSimpleName());
        });

        // 2) @Tool POJO —— 检查每个 bean 是否有 @Tool 方法
        for (String beanName : appCtx.getBeanDefinitionNames()) {
            try {
                Object bean = appCtx.getBean(beanName);
                if (bean instanceof AgentTool) continue; // 已在上面处理
                Class<?> clazz = bean.getClass();
                if (clazz.getName().contains("$$")) clazz = clazz.getSuperclass();
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(Tool.class)) {
                        toolkit.registerTool(bean);
                        log.debug("Registered @Tool POJO bean: {} ({})", beanName, clazz.getSimpleName());
                        break;
                    }
                }
            } catch (Exception e) {
                log.trace("Skip bean {}: {}", beanName, e.getMessage());
            }
        }
    }

    /** 空模型，仅用于内省工具 schema，永远不会被调用 stream。 */
    private static final class NoopModel implements Model {
        @Override
        public Flux<ChatResponse> stream(
                List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            return Flux.empty();
        }

        @Override
        public String getModelName() {
            return "noop";
        }
    }
}
