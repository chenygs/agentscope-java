# M3 工厂骨架 + ModelFactory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 `agentscope-builder-saton` 引入"五大工厂"中的第一个：`ModelFactory`。完成后端能告诉前端"系统支持哪些 model 类型 + 每种类型的表单 schema"，以及"用某行 `model_provider` 数据 instantiate 出一个真实可调的 `agentscope-core` `Model` 对象"。完成标志：(1) `GET /api/factories/model-types` 返回 5 个 provider 类型 + JSON schema；(2) `mvn test` 中 `ModelFactoryTest` 能 instantiate 一个 DashScopeChatModel（不发真请求，验对象创建）；(3) 已存在的 M2 的 `/api/models` CRUD 不受影响。

**Architecture:** 引入通用 SPI 抽象（适用于后续 5 大工厂全部复用）：
- `ProviderRegistry<T extends Provider>` —— Spring 启动时自动收集某 SPI 接口的所有实现，按 `type()` 字符串路由
- `TypeMeta` —— 给前端用的 `{type, displayName, description, schema}` record
- `ModelProviderType` SPI —— 接口定义"一种 model 类型怎么 instantiate"。5 个内置实现：dashscope / openai / anthropic / gemini / ollama，每个 `@Component`，启动期自动注册
- `ModelFactory` Service 门面 —— `listTypes() / typeOf(String) / instantiate(ModelProviderEntity)`
- `FactoriesController` —— `/api/factories/model-types` REST 入口（GET，只需登录无需写权限）

**关键决策**（仅本里程碑相关）：
- 不引入 JSON Schema 校验运行时（写在 spec §13 开放问题，留给"创建/更新 ModelProvider 时校验 props 必填字段"那个阶段）。本里程碑的 schema 只是给前端**渲染表单**用的描述性元数据。
- 工厂只负责创建 `Model` 对象，不负责存储/缓存（缓存等到 M4 的 `AgentRuntimeResolver` 引入）。
- 不校验 `apiKey` 真实有效性（即不发任何外部 HTTP）。create 出对象就算成功。

**Tech Stack:** 沿用，新增 `io.agentscope:agentscope-core:2.0.0-RC2` 依赖（已在本地 Maven 仓库 `D:\PROGRAM\maven\Repository\io\agentscope\agentscope-core\2.0.0-RC2\`）。

**踩坑预防（spec §12 已记 13 条，重点关注）**：
- `@PathVariable` 必须写显式名字 `@PathVariable("xxx")`（§12.11）
- ObjectMapper import 用 `tools.jackson.databind` （§12.3）
- controller 同步 service 调用要用 `SaReactorSyncHolder.setContext(exchange)/clearContext()`（§12.4） —— 但本里程碑工厂目录接口**不需要**（不调 `StpUtil`），仍走 `Mono.fromCallable` 即可
- 别给 `pom.xml` 加非必要依赖；本次只加 `agentscope-core`

---

## File Structure

新建/修改文件：

```
agentscope-builder-saton/
├── pom.xml                                                # 加 agentscope-core 依赖
└── src/main/java/io/agentscope/builder/saton/
    └── factory/
        ├── core/
        │   ├── Provider.java                              # 顶层 SPI 标记接口
        │   ├── TypeMeta.java                              # record: type / displayName / description / schema
        │   ├── ProviderRegistry.java                      # 泛型注册表骨架（Spring 启动期收集）
        │   └── JsonSchemaUtil.java                        # 小工具：从 Map 拼 JSON schema 树
        ├── model/
        │   ├── ModelProviderType.java                     # SPI 接口（extends Provider）
        │   ├── ModelFactory.java                          # 工厂门面 Service
        │   └── impl/
        │       ├── DashScopeModelProviderType.java        # 5 个内置实现
        │       ├── OpenAIModelProviderType.java
        │       ├── AnthropicModelProviderType.java
        │       ├── GeminiModelProviderType.java
        │       └── OllamaModelProviderType.java
        └── api/
            └── FactoriesController.java                   # /api/factories/model-types

src/test/java/io/agentscope/builder/saton/
└── factory/
    ├── core/
    │   └── ProviderRegistryTest.java                      # 注册表泛型行为
    ├── model/
    │   ├── ModelFactoryTest.java                          # 5 类型都能 instantiate
    │   └── impl/
    │       └── DashScopeModelProviderTypeTest.java        # 一个示例 type 的 schema 校验
    └── api/
        └── FactoriesControllerFlowTest.java               # GET /api/factories/model-types 集成
```

**职责说明**：

- `Provider` —— 空接口，仅起类型标记作用（让 `ProviderRegistry<P extends Provider>` 能约束泛型边界）。所有 SPI 接口（ModelProviderType / 未来的 ToolType / SkillRepoType / HookType / AgentType）都 extends 它。
- `TypeMeta` —— `record(String type, String displayName, String description, Map<String,Object> schema)`，整个项目共用，序列化给前端用。
- `ProviderRegistry<P extends Provider>` —— 抽象基类。子类 = 具体注册表（如未来 `ModelProviderTypeRegistry`），构造期接收 `List<P>` Spring 自动注入。提供 `get(String)` / `list()` / `listMetas()`。本 milestone **只用 ModelProviderType 那一份**，但骨架是泛型的。
- `JsonSchemaUtil` —— 一个 `field(name, type, required, desc, ...)` builder 风格的小工具，避免每个 Provider impl 手撸 Map of Map。
- `ModelProviderType` —— SPI：`type()` `meta()` `instantiate(ModelProviderEntity)`。
- 5 个 impl —— `@Component`，分别返回不同的 `XxxChatModel` 对象。**字段差异**：
  - dashscope / openai / anthropic / gemini：apiKey + modelName + 可选 baseUrl
  - ollama：仅 modelName + 可选 baseUrl（**无 apiKey**）
- `ModelFactory` —— `@Service`，`@Autowired ModelProviderTypeRegistry registry`，提供 `listTypes() → List<TypeMeta>`, `typeOf(String type) → ModelProviderType`, `instantiate(ModelProviderEntity) → Model`。
- `FactoriesController` —— `@RestController @RequestMapping("/api/factories")`，`GET /model-types` 返回 `List<TypeMeta>`。鉴权由 sa-token 全局 filter 处理（要求登录）；无需 `SaReactorSyncHolder`（不调 `StpUtil`）。

---

## Task 1: 引入 agentscope-core 依赖 + 通用工厂骨架

**Files:**
- Modify: `pom.xml` (add agentscope-core)
- Create: `src/main/java/io/agentscope/builder/saton/factory/core/Provider.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/core/TypeMeta.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/core/ProviderRegistry.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/core/JsonSchemaUtil.java`

- [ ] **Step 1: 加 agentscope-core 依赖到 pom.xml**

Edit `D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton\pom.xml`. 在已有 `<dependencies>` 块尾部、`<!-- Test -->` 之前，加：

```xml
        <!-- AgentScope core: Model / Tool / Agent / Hook / Memory abstractions -->
        <dependency>
            <groupId>io.agentscope</groupId>
            <artifactId>agentscope-core</artifactId>
            <version>2.0.0-RC2</version>
        </dependency>
```

- [ ] **Step 2: 验证 pom 可解析 + 依赖能拉到**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton"
mvn -q dependency:resolve 2>&1 | Select-Object -Last 10
```

Expected: 输出无 ERROR，本地 Maven 仓库 `D:\PROGRAM\maven\Repository\io\agentscope\agentscope-core\2.0.0-RC2\` 已有 jar 应该直接命中。若提示找不到 reactor 等 transitive deps，确认 settings.xml `localRepository` 配置正确。

- [ ] **Step 3: 写 Provider 标记接口**

Write `src/main/java/io/agentscope/builder/saton/factory/core/Provider.java`:

```java
package io.agentscope.builder.saton.factory.core;

/**
 * 所有 SPI 接口（ModelProviderType / 未来的 ToolType / SkillRepoType / HookType / AgentType）
 * 的顶层标记接口。仅用于约束 {@link ProviderRegistry} 的泛型边界。
 */
public interface Provider {

    /** 唯一标识，前端选型用，写库时也存这个字符串。例如 "dashscope" / "openai"。 */
    String type();

    /** 给前端列表 + 表单渲染的元信息。 */
    TypeMeta meta();
}
```

- [ ] **Step 4: 写 TypeMeta record**

Write `src/main/java/io/agentscope/builder/saton/factory/core/TypeMeta.java`:

```java
package io.agentscope.builder.saton.factory.core;

import java.util.Map;

/**
 * 给前端用的"类型元信息"。
 *
 * @param type        唯一标识（与 {@link Provider#type()} 一致）
 * @param displayName 给人看的名字，如 "通义千问"
 * @param description 说明文本
 * @param schema      JSON Schema-like map（fields/required/...），前端按它渲染参数表单
 */
public record TypeMeta(
        String type,
        String displayName,
        String description,
        Map<String, Object> schema
) {}
```

- [ ] **Step 5: 写 ProviderRegistry 泛型基类**

Write `src/main/java/io/agentscope/builder/saton/factory/core/ProviderRegistry.java`:

```java
package io.agentscope.builder.saton.factory.core;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 通用注册表骨架。具体子类（如 {@code ModelProviderTypeRegistry}）只需把
 * Spring 注入的 {@code List<P>} 传给父构造器，无需重复实现 get / list / listMetas。
 *
 * <p>不是 Spring bean —— 子类才是。
 *
 * @param <P> 具体 SPI 接口类型，必须 extends {@link Provider}
 */
public abstract class ProviderRegistry<P extends Provider> {

    private final Map<String, P> byType;

    protected ProviderRegistry(List<P> providers) {
        this.byType = providers.stream().collect(Collectors.toUnmodifiableMap(
                Provider::type,
                p -> p,
                (a, b) -> {
                    throw new IllegalStateException(
                            "duplicate provider type: " + a.type()
                                    + " (" + a.getClass() + " vs " + b.getClass() + ")");
                }));
    }

    /** 按 type 查；找不到抛 {@link NoSuchElementException}（业务层应包成 404）。 */
    public P get(String type) {
        P p = byType.get(type);
        if (p == null) {
            throw new NoSuchElementException("unknown type: " + type);
        }
        return p;
    }

    /** 是否有 */
    public boolean has(String type) {
        return byType.containsKey(type);
    }

    /** 全量 provider 实例。按 type 字母序。 */
    public List<P> list() {
        return byType.values().stream()
                .sorted((a, b) -> a.type().compareTo(b.type()))
                .toList();
    }

    /** 全量元信息。 */
    public List<TypeMeta> listMetas() {
        return list().stream().map(Provider::meta).toList();
    }
}
```

- [ ] **Step 6: 写 JsonSchemaUtil**

Write `src/main/java/io/agentscope/builder/saton/factory/core/JsonSchemaUtil.java`:

```java
package io.agentscope.builder.saton.factory.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简的 JSON Schema builder，专门拼 type=object + properties + required 的扁平结构。
 * 输出形状（用作 {@link TypeMeta#schema()}）：
 * <pre>{@code
 * {
 *   "type": "object",
 *   "properties": {
 *     "apiKey":    { "type": "string", "description": "...", "secret": true },
 *     "modelName": { "type": "string", "description": "..." }
 *   },
 *   "required": ["apiKey", "modelName"]
 * }
 * }</pre>
 * 前端可用 react-jsonschema-form 之类的库直接渲染。
 */
public final class JsonSchemaUtil {

    public static Builder object() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();

        public Builder field(String name, String type, boolean required, String description) {
            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("type", type);
            if (description != null && !description.isBlank()) {
                spec.put("description", description);
            }
            properties.put(name, spec);
            if (required) {
                this.required.add(name);
            }
            return this;
        }

        /** 加一个"敏感字段"标记（前端把它渲染成 password input；后端配合 SensitiveFields 做加密）。 */
        public Builder secretField(String name, boolean required, String description) {
            field(name, "string", required, description);
            properties.get(name).put("secret", true);
            return this;
        }

        public Map<String, Object> build() {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("type", "object");
            root.put("properties", properties);
            root.put("required", List.copyOf(required));
            return root;
        }
    }

    private JsonSchemaUtil() {}
}
```

- [ ] **Step 7: 编译验证**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton"
mvn -q compile
```

Expected: BUILD SUCCESS, agentscope-core 类（如 `io.agentscope.core.model.Model`）在 classpath 上但本步还没用到。

- [ ] **Step 8: Commit**

```powershell
git add pom.xml src/
git commit -m "feat(factory/core): generic Provider SPI + ProviderRegistry + TypeMeta + JsonSchema util

Adds agentscope-core:2.0.0-RC2 dep — first use of upstream Model/Agent
abstractions in this project."
```

---

## Task 2: ProviderRegistry 单测（验证泛型 + Spring 自动收集）

**Files:**
- Test: `src/test/java/io/agentscope/builder/saton/factory/core/ProviderRegistryTest.java`

- [ ] **Step 1: 写测试**

Write `src/test/java/io/agentscope/builder/saton/factory/core/ProviderRegistryTest.java`:

```java
package io.agentscope.builder.saton.factory.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class ProviderRegistryTest {

    interface FooProvider extends Provider {}

    static final class FooA implements FooProvider {
        @Override public String type() { return "a"; }
        @Override public TypeMeta meta() {
            return new TypeMeta("a", "A name", "A desc", Map.of());
        }
    }

    static final class FooB implements FooProvider {
        @Override public String type() { return "b"; }
        @Override public TypeMeta meta() {
            return new TypeMeta("b", "B name", "B desc", Map.of());
        }
    }

    static final class FooDuplicateA implements FooProvider {
        @Override public String type() { return "a"; }
        @Override public TypeMeta meta() {
            return new TypeMeta("a", "dup", "dup", Map.of());
        }
    }

    static final class FooRegistry extends ProviderRegistry<FooProvider> {
        FooRegistry(List<FooProvider> providers) { super(providers); }
    }

    @Test
    void getByType() {
        FooRegistry reg = new FooRegistry(List.of(new FooA(), new FooB()));
        assertEquals("a", reg.get("a").type());
        assertEquals("b", reg.get("b").type());
    }

    @Test
    void unknownTypeThrows() {
        FooRegistry reg = new FooRegistry(List.of(new FooA()));
        assertThrows(NoSuchElementException.class, () -> reg.get("nope"));
    }

    @Test
    void listSortedByType() {
        FooRegistry reg = new FooRegistry(List.of(new FooB(), new FooA()));
        assertEquals(List.of("a", "b"), reg.list().stream().map(Provider::type).toList());
    }

    @Test
    void listMetasMaps() {
        FooRegistry reg = new FooRegistry(List.of(new FooA(), new FooB()));
        List<TypeMeta> metas = reg.listMetas();
        assertEquals(2, metas.size());
        assertEquals("a", metas.get(0).type());
        assertEquals("A name", metas.get(0).displayName());
    }

    @Test
    void duplicateTypeThrowsAtConstruction() {
        assertThrows(IllegalStateException.class,
                () -> new FooRegistry(List.of(new FooA(), new FooDuplicateA())));
    }

    @Test
    void hasReportsMembership() {
        FooRegistry reg = new FooRegistry(List.of(new FooA()));
        assertTrue(reg.has("a"));
        assertFalse(reg.has("nope"));
    }
}
```

- [ ] **Step 2: 跑测试**

```powershell
mvn test -Dtest=ProviderRegistryTest
```

Expected: `Tests run: 6, Failures: 0`. 纯 POJO 单测，不启 Spring 上下文。

- [ ] **Step 3: Commit**

```powershell
git add src/
git commit -m "test(factory/core): ProviderRegistry generic registry behavior"
```

---

## Task 3: ModelProviderType SPI + ModelProviderTypeRegistry + 5 个内置实现

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/factory/model/ModelProviderType.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/model/ModelProviderTypeRegistry.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/model/impl/DashScopeModelProviderType.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/model/impl/OpenAIModelProviderType.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/model/impl/AnthropicModelProviderType.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/model/impl/GeminiModelProviderType.java`
- Create: `src/main/java/io/agentscope/builder/saton/factory/model/impl/OllamaModelProviderType.java`

- [ ] **Step 1: 写 ModelProviderType SPI**

Write `src/main/java/io/agentscope/builder/saton/factory/model/ModelProviderType.java`:

```java
package io.agentscope.builder.saton.factory.model;

import io.agentscope.builder.saton.factory.core.Provider;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.Model;

/**
 * "一种 model provider 类型"的 SPI 抽象。每个实现负责：
 * <ul>
 *   <li>声明 {@link #type()} 唯一标识（写入 {@link ModelProviderEntity#getType()}）</li>
 *   <li>提供 {@link #meta()} 中的 JSON schema，前端按它渲染表单</li>
 *   <li>{@link #instantiate(ModelProviderEntity)} 把数据库一行配置 new 成可调的 {@link Model}</li>
 * </ul>
 *
 * <p>所有实现都必须是 Spring {@code @Component}，启动期被 {@link ModelProviderTypeRegistry} 收集。
 */
public interface ModelProviderType extends Provider {

    /**
     * 用持久化层来的 {@link ModelProviderEntity} 实例化一个 {@link Model}。
     * <ul>
     *   <li>entity 的 {@code propsJson} 已经被 {@code EncryptedJsonConverter} 解密，里头是明文</li>
     *   <li>必填字段缺失应抛 {@link IllegalArgumentException}（业务层会包成 400）</li>
     *   <li>不发任何外部网络请求；apiKey 真实性等到首次调用时由 agentscope-core 自己处理</li>
     * </ul>
     */
    Model instantiate(ModelProviderEntity entity);
}
```

- [ ] **Step 2: 写 ModelProviderTypeRegistry**

Write `src/main/java/io/agentscope/builder/saton/factory/model/ModelProviderTypeRegistry.java`:

```java
package io.agentscope.builder.saton.factory.model;

import io.agentscope.builder.saton.factory.core.ProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring 启动期自动注入所有 {@link ModelProviderType} 实现并按 type 路由。
 */
@Component
public class ModelProviderTypeRegistry extends ProviderRegistry<ModelProviderType> {

    public ModelProviderTypeRegistry(List<ModelProviderType> providers) {
        super(providers);
    }
}
```

- [ ] **Step 3: 实现 DashScopeModelProviderType**

Write `src/main/java/io/agentscope/builder/saton/factory/model/impl/DashScopeModelProviderType.java`:

```java
package io.agentscope.builder.saton.factory.model.impl;

import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class DashScopeModelProviderType implements ModelProviderType {

    @Override
    public String type() {
        return "dashscope";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "dashscope",
                "通义千问 (DashScope)",
                "阿里云 DashScope OpenAI-compatible / Generation 接口",
                JsonSchemaUtil.object()
                        .secretField("apiKey", true, "DashScope API Key (sk-...)")
                        .field("modelName", "string", true, "模型名，如 qwen-max / qwen-plus / qwen-turbo")
                        .field("baseUrl", "string", false, "自定义网关地址；不填走官方")
                        .build()
        );
    }

    @Override
    public Model instantiate(ModelProviderEntity entity) {
        JsonNode props = readProps(entity);
        String apiKey = requireString(props, "apiKey");
        String modelName = requireString(props, "modelName");
        String baseUrl = optionalString(props, "baseUrl");

        DashScopeChatModel.Builder b = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }

    /* ----- shared by all *ModelProviderType impls; keep duplicated for clarity ----- */

    static JsonNode readProps(ModelProviderEntity e) {
        try {
            String json = (e.getPropsJson() == null || e.getPropsJson().isBlank())
                    ? "{}" : e.getPropsJson();
            return JsonUtil.mapper().readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid propsJson on model provider " + e.getId(), ex);
        }
    }

    static String requireString(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asString().isBlank()) {
            throw new IllegalArgumentException("model props missing required field: " + field);
        }
        return v.asString();
    }

    static String optionalString(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || !v.isTextual() || v.asString().isBlank()) ? null : v.asString();
    }
}
```

- [ ] **Step 4: 实现 OpenAIModelProviderType**

Write `src/main/java/io/agentscope/builder/saton/factory/model/impl/OpenAIModelProviderType.java`:

```java
package io.agentscope.builder.saton.factory.model.impl;

import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class OpenAIModelProviderType implements ModelProviderType {

    @Override
    public String type() {
        return "openai";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "openai",
                "OpenAI",
                "OpenAI Chat Completions / Compatible",
                JsonSchemaUtil.object()
                        .secretField("apiKey", true, "OpenAI API Key")
                        .field("modelName", "string", true, "如 gpt-4o / gpt-4o-mini")
                        .field("baseUrl", "string", false, "自定义网关 / Compatible Endpoint")
                        .build()
        );
    }

    @Override
    public Model instantiate(ModelProviderEntity entity) {
        JsonNode props = DashScopeModelProviderType.readProps(entity);
        String apiKey = DashScopeModelProviderType.requireString(props, "apiKey");
        String modelName = DashScopeModelProviderType.requireString(props, "modelName");
        String baseUrl = DashScopeModelProviderType.optionalString(props, "baseUrl");

        OpenAIChatModel.Builder b = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }
}
```

> 复用 `DashScopeModelProviderType.{readProps,requireString,optionalString}` 静态方法，避免每个 impl 都搬同样的 6 行 helper。

- [ ] **Step 5: 实现 AnthropicModelProviderType**

Write `src/main/java/io/agentscope/builder/saton/factory/model/impl/AnthropicModelProviderType.java`:

```java
package io.agentscope.builder.saton.factory.model.impl;

import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class AnthropicModelProviderType implements ModelProviderType {

    @Override
    public String type() {
        return "anthropic";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "anthropic",
                "Anthropic Claude",
                "Anthropic Messages API（Claude 4.x / Sonnet / Opus / Haiku）",
                JsonSchemaUtil.object()
                        .secretField("apiKey", true, "Anthropic API Key (sk-ant-...)")
                        .field("modelName", "string", true, "如 claude-sonnet-4-6 / claude-haiku-4-5-20251001")
                        .field("baseUrl", "string", false, "自定义网关")
                        .build()
        );
    }

    @Override
    public Model instantiate(ModelProviderEntity entity) {
        JsonNode props = DashScopeModelProviderType.readProps(entity);
        String apiKey = DashScopeModelProviderType.requireString(props, "apiKey");
        String modelName = DashScopeModelProviderType.requireString(props, "modelName");
        String baseUrl = DashScopeModelProviderType.optionalString(props, "baseUrl");

        AnthropicChatModel.Builder b = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }
}
```

- [ ] **Step 6: 实现 GeminiModelProviderType**

Write `src/main/java/io/agentscope/builder/saton/factory/model/impl/GeminiModelProviderType.java`:

```java
package io.agentscope.builder.saton.factory.model.impl;

import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.GeminiChatModel;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class GeminiModelProviderType implements ModelProviderType {

    @Override
    public String type() {
        return "gemini";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "gemini",
                "Google Gemini",
                "Google Generative AI（Gemini 1.5 / 2.x）",
                JsonSchemaUtil.object()
                        .secretField("apiKey", true, "Google AI Studio API Key")
                        .field("modelName", "string", true, "如 gemini-2.0-flash / gemini-2.5-pro")
                        .field("baseUrl", "string", false, "自定义网关")
                        .build()
        );
    }

    @Override
    public Model instantiate(ModelProviderEntity entity) {
        JsonNode props = DashScopeModelProviderType.readProps(entity);
        String apiKey = DashScopeModelProviderType.requireString(props, "apiKey");
        String modelName = DashScopeModelProviderType.requireString(props, "modelName");
        String baseUrl = DashScopeModelProviderType.optionalString(props, "baseUrl");

        GeminiChatModel.Builder b = GeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }
}
```

> 若 `GeminiChatModel.Builder.apiKey/modelName/baseUrl` 方法签名不同，按编译器报错调；agentscope-core 2.0.0-RC2 这三个方法应都存在（参考其他 4 个 builder）。如果方法名是 `setApiKey` 等，按实际改。

- [ ] **Step 7: 实现 OllamaModelProviderType（无 apiKey）**

Write `src/main/java/io/agentscope/builder/saton/factory/model/impl/OllamaModelProviderType.java`:

```java
package io.agentscope.builder.saton.factory.model.impl;

import io.agentscope.builder.saton.factory.core.JsonSchemaUtil;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.model.ModelProviderType;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OllamaChatModel;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 本地 Ollama —— 没有 apiKey，只需 modelName + baseUrl（默认 http://localhost:11434）。
 */
@Component
public class OllamaModelProviderType implements ModelProviderType {

    @Override
    public String type() {
        return "ollama";
    }

    @Override
    public TypeMeta meta() {
        return new TypeMeta(
                "ollama",
                "Ollama (本地)",
                "本地 Ollama 服务，默认监听 http://localhost:11434",
                JsonSchemaUtil.object()
                        .field("modelName", "string", true, "如 llama3.2 / qwen2.5:7b")
                        .field("baseUrl", "string", false, "默认 http://localhost:11434")
                        .build()
        );
    }

    @Override
    public Model instantiate(ModelProviderEntity entity) {
        JsonNode props = DashScopeModelProviderType.readProps(entity);
        String modelName = DashScopeModelProviderType.requireString(props, "modelName");
        String baseUrl = DashScopeModelProviderType.optionalString(props, "baseUrl");

        OllamaChatModel.Builder b = OllamaChatModel.builder()
                .modelName(modelName);
        if (baseUrl != null) {
            b.baseUrl(baseUrl);
        }
        return b.build();
    }
}
```

- [ ] **Step 8: 编译验证**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton"
mvn -q compile
```

Expected: BUILD SUCCESS。若某个 `XxxChatModel.Builder` 方法名实际不同（如 `setBaseUrl` 不是 `baseUrl`），按编译器报错改对应那行。**5 个 impl 全要能编译**才能进下一步。

- [ ] **Step 9: Commit**

```powershell
git add src/
git commit -m "feat(factory/model): ModelProviderType SPI + registry + 5 builtin impls (dashscope/openai/anthropic/gemini/ollama)"
```

---

## Task 4: ModelFactory 门面 Service + 单测

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/factory/model/ModelFactory.java`
- Test: `src/test/java/io/agentscope/builder/saton/factory/model/ModelFactoryTest.java`
- Test: `src/test/java/io/agentscope/builder/saton/factory/model/impl/DashScopeModelProviderTypeTest.java`

- [ ] **Step 1: 写 ModelFactory**

Write `src/main/java/io/agentscope/builder/saton/factory/model/ModelFactory.java`:

```java
package io.agentscope.builder.saton.factory.model;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 工厂门面：屏蔽 {@link ModelProviderTypeRegistry} 的存在，提供高层 API。
 * <ul>
 *   <li>{@link #listTypes()} — 给前端的 type 目录（含 schema）</li>
 *   <li>{@link #instantiate(ModelProviderEntity)} — 给业务的"从 DB 行 → 可调 Model"</li>
 * </ul>
 */
@Service
public class ModelFactory {

    private final ModelProviderTypeRegistry registry;

    public ModelFactory(ModelProviderTypeRegistry registry) {
        this.registry = registry;
    }

    public List<TypeMeta> listTypes() {
        return registry.listMetas();
    }

    /** 实例化。entity.type 未注册时抛 {@link NotFoundException}。 */
    public Model instantiate(ModelProviderEntity entity) {
        ModelProviderType type;
        try {
            type = registry.get(entity.getType());
        } catch (NoSuchElementException e) {
            throw new NotFoundException("unknown model provider type: " + entity.getType());
        }
        return type.instantiate(entity);
    }
}
```

- [ ] **Step 2: 写 ModelFactoryTest**

Write `src/test/java/io/agentscope/builder/saton/factory/model/ModelFactoryTest.java`:

```java
package io.agentscope.builder.saton.factory.model;

import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ModelFactoryTest {

    @Autowired ModelFactory factory;

    @Test
    void allFiveBuiltinTypesRegistered() {
        List<TypeMeta> types = factory.listTypes();
        Set<String> names = types.stream().map(TypeMeta::type).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("anthropic", "dashscope", "gemini", "ollama", "openai"), names);
    }

    @Test
    void typesAreSortedAlphabetically() {
        List<TypeMeta> types = factory.listTypes();
        List<String> sorted = types.stream().map(TypeMeta::type).sorted().toList();
        assertEquals(sorted, types.stream().map(TypeMeta::type).toList());
    }

    @Test
    void schemaContainsExpectedFieldsForDashScope() {
        TypeMeta dash = factory.listTypes().stream()
                .filter(m -> "dashscope".equals(m.type())).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var props = (java.util.Map<String, Object>) dash.schema().get("properties");
        assertTrue(props.containsKey("apiKey"));
        assertTrue(props.containsKey("modelName"));
        assertTrue(props.containsKey("baseUrl"));
        @SuppressWarnings("unchecked")
        var required = (List<String>) dash.schema().get("required");
        assertTrue(required.contains("apiKey"));
        assertTrue(required.contains("modelName"));
        assertFalse(required.contains("baseUrl"));
    }

    @Test
    void ollamaSchemaHasNoApiKey() {
        TypeMeta ol = factory.listTypes().stream()
                .filter(m -> "ollama".equals(m.type())).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var props = (java.util.Map<String, Object>) ol.schema().get("properties");
        assertFalse(props.containsKey("apiKey"));
        assertTrue(props.containsKey("modelName"));
    }

    @Test
    void instantiateDashScopeReturnsModel() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setId(99L);
        e.setOwnerId("test");
        e.setName("t");
        e.setType("dashscope");
        e.setPropsJson("{\"apiKey\":\"sk-not-real\",\"modelName\":\"qwen-max\"}");
        Model m = factory.instantiate(e);
        assertNotNull(m);
        // 不发请求；只验对象创建成功 + 类型正确
        assertTrue(m.getClass().getSimpleName().contains("DashScope"));
    }

    @Test
    void instantiateMissingRequiredFieldThrows() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setType("openai");
        e.setPropsJson("{\"modelName\":\"gpt-4o\"}");  // apiKey 缺
        assertThrows(IllegalArgumentException.class, () -> factory.instantiate(e));
    }

    @Test
    void instantiateUnknownTypeThrowsNotFound() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setType("nope-such-provider");
        e.setPropsJson("{}");
        assertThrows(NotFoundException.class, () -> factory.instantiate(e));
    }

    @Test
    void instantiateOllamaDoesNotRequireApiKey() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setType("ollama");
        e.setPropsJson("{\"modelName\":\"llama3.2\"}");
        Model m = factory.instantiate(e);
        assertNotNull(m);
        assertTrue(m.getClass().getSimpleName().contains("Ollama"));
    }
}
```

- [ ] **Step 3: 写 DashScopeModelProviderTypeTest（单独验 helper 行为）**

Write `src/test/java/io/agentscope/builder/saton/factory/model/impl/DashScopeModelProviderTypeTest.java`:

```java
package io.agentscope.builder.saton.factory.model.impl;

import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DashScopeModelProviderTypeTest {

    @Test
    void typeAndDisplayName() {
        DashScopeModelProviderType t = new DashScopeModelProviderType();
        assertEquals("dashscope", t.type());
        assertEquals("dashscope", t.meta().type());
        assertNotNull(t.meta().displayName());
        assertNotNull(t.meta().schema());
    }

    @Test
    void instantiateWithEmptyPropsThrows() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setType("dashscope");
        e.setPropsJson("{}");
        assertThrows(IllegalArgumentException.class,
                () -> new DashScopeModelProviderType().instantiate(e));
    }

    @Test
    void instantiateWithBaseUrlOverride() {
        ModelProviderEntity e = new ModelProviderEntity();
        e.setType("dashscope");
        e.setPropsJson("""
                {"apiKey":"sk-x","modelName":"qwen-max","baseUrl":"https://my-gw"}""");
        var m = new DashScopeModelProviderType().instantiate(e);
        assertNotNull(m);
    }
}
```

- [ ] **Step 4: 跑测试**

```powershell
mvn test -Dtest=ModelFactoryTest,DashScopeModelProviderTypeTest
```

Expected: `Tests run: 11, Failures: 0`（8 from ModelFactoryTest + 3 from DashScopeModelProviderTypeTest）。

如有任何 Builder 方法签名错（编译期发现），改对应 impl 那行后再跑。

- [ ] **Step 5: Commit**

```powershell
git add src/
git commit -m "feat(factory/model): ModelFactory facade + tests covering all 5 types"
```

---

## Task 5: FactoriesController + 集成测试

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/factory/api/FactoriesController.java`
- Test: `src/test/java/io/agentscope/builder/saton/factory/api/FactoriesControllerFlowTest.java`

- [ ] **Step 1: 写 FactoriesController**

Write `src/main/java/io/agentscope/builder/saton/factory/api/FactoriesController.java`:

```java
package io.agentscope.builder.saton.factory.api;

import io.agentscope.builder.saton.factory.core.TypeMeta;
import io.agentscope.builder.saton.factory.model.ModelFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 给前端的"工厂目录"接口。未来 Tool / Skill / Hook / Agent 类型也会在这里挂端点
 * （/tool-types / /skill-repo-types / /hook-types / /agent-types），形成统一的 5 大类目录。
 *
 * <p>本里程碑（M3）仅暴露 {@code /api/factories/model-types}。
 *
 * <p>所有端点都不调 {@link cn.dev33.satoken.stp.StpUtil}（只读 in-memory 注册表），
 * 因此不需要 {@code SaReactorSyncHolder} 上下文绑定；sa-token 全局过滤器负责"必须登录"。
 */
@RestController
@RequestMapping("/api/factories")
public class FactoriesController {

    private final ModelFactory modelFactory;

    public FactoriesController(ModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @GetMapping("/model-types")
    public Mono<List<TypeMeta>> modelTypes() {
        return Mono.fromCallable(modelFactory::listTypes);
    }
}
```

- [ ] **Step 2: 写集成测试**

Write `src/test/java/io/agentscope/builder/saton/factory/api/FactoriesControllerFlowTest.java`:

```java
package io.agentscope.builder.saton.factory.api;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.factory.core.TypeMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FactoriesControllerFlowTest {

    @LocalServerPort int port;

    WebTestClient client;
    String token;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();

        LoginResponse login = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult().getResponseBody();
        assertNotNull(login);
        this.token = login.token();
    }

    @Test
    void listAllFiveModelTypes() {
        List<TypeMeta> types = client.get().uri("/api/factories/model-types")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TypeMeta>>() {})
                .returnResult().getResponseBody();

        assertNotNull(types);
        Set<String> names = types.stream().map(TypeMeta::type).collect(Collectors.toSet());
        assertEquals(Set.of("anthropic", "dashscope", "gemini", "ollama", "openai"), names);
    }

    @Test
    void everyTypeHasMetaAndSchema() {
        List<TypeMeta> types = client.get().uri("/api/factories/model-types")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TypeMeta>>() {})
                .returnResult().getResponseBody();
        assertNotNull(types);
        for (TypeMeta t : types) {
            assertNotNull(t.displayName(), "displayName for " + t.type());
            assertNotNull(t.description(), "description for " + t.type());
            assertNotNull(t.schema(), "schema for " + t.type());
            assertEquals("object", t.schema().get("type"));
            assertNotNull(t.schema().get("properties"));
            assertNotNull(t.schema().get("required"));
        }
    }

    @Test
    void modelTypesRequiresLogin() {
        client.get().uri("/api/factories/model-types")
                .exchange().expectStatus().isUnauthorized();
    }
}
```

- [ ] **Step 3: 跑测试**

```powershell
mvn test -Dtest=FactoriesControllerFlowTest
```

Expected: `Tests run: 3, Failures: 0`.

- [ ] **Step 4: Commit**

```powershell
git add src/
git commit -m "feat(factory/api): FactoriesController + GET /api/factories/model-types"
```

---

## Task 6: 全量回归 + smoke + tag

- [ ] **Step 1: 全量回归**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton"
mvn test
```

Expected: previous **32 (M2)** + **6 (Task 2)** + **8+3 (Task 4)** + **3 (Task 5)** = **52 tests PASS** 左右。

- [ ] **Step 2: 手动 smoke**

Start backend in background:

```bash
cd "D:/GIT/ownsource/AI/AGENT_SCOPE/agentscope-java/agentscope-builder-saton" && mvn spring-boot:run 2>&1
```

Wait for `Netty started on port 8080`.

```powershell
# Login
$resp = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin"}'
$token = $resp.token

# GET model-types
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/factories/model-types `
  -Headers @{ satoken=$token } | ConvertTo-Json -Depth 6
```

Expected: 5 个 TypeMeta，每个含 type / displayName / description / schema（schema 有 properties 和 required 数组）。dashscope 应见 `apiKey/modelName/baseUrl`，ollama 应只有 `modelName/baseUrl`。

不带 token：

```powershell
try {
    Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/factories/model-types
} catch {
    $_.Exception.Response.StatusCode    # Unauthorized
}
```

停掉服务。

- [ ] **Step 3: 验证 M2 接口未回归**

确认 `/api/models` 等 4 个旧接口仍能工作（mvn test 已覆盖，无需手动）。

- [ ] **Step 4: Tag**

```powershell
git log --oneline | Select-Object -First 12
git tag m3-model-factory-done
git tag --list
```

Expected: 看到 `m3-model-factory-done` 标签；列表里有 `m1-bootstrap-done`、`m2-resources-done`、`m3-model-factory-done`。

---

## Self-Review 检查表

- [x] **Spec coverage**：实现 spec §4 五大工厂中的第一个完整端到端切片 —— ModelFactory + REST 目录 + 5 个内置 ModelProviderType。
- [x] **No placeholders**：所有 code block 完整，无 TODO / TBD。
- [x] **Type consistency**：`ModelProviderType / ModelProviderTypeRegistry / ModelFactory / TypeMeta / Provider / ProviderRegistry / JsonSchemaUtil` 命名全程一致。
- [x] **踩坑预防**：sa-token (SaReactorSyncHolder 不需要，本里程碑无 `StpUtil`)；Jackson 3 import `tools.jackson`；`@PathVariable` 暂未涉及，未来加 `/api/factories/model-types/{type}` 时记得显式写名字。
- [x] **测试粒度**：每层都有测试 —— ProviderRegistry 纯 POJO 单测、ModelFactory `@SpringBootTest`、Controller 集成测试。

---

## M3 完成定义

1. `mvn test` 全部 PASS（约 52 个）
2. `GET /api/factories/model-types` 返回 5 个 TypeMeta（按字母序：anthropic/dashscope/gemini/ollama/openai）
3. 每个 TypeMeta 含 type / displayName / description / schema
4. ollama 的 schema 无 apiKey 字段
5. 不带 token 返回 401（sa-token 全局拦截器生效）
6. M2 全部 CRUD 接口未回归
7. tag `m3-model-factory-done` 已打

M3 完成后告知用户：开始写 M4 plan（第一个能聊的 Agent —— AgentDefinition CRUD + AgentBuildOrchestrator + ChatController 非流式版）。
