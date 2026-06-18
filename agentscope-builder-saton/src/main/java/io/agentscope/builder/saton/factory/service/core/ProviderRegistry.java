package io.agentscope.builder.saton.factory.service.core;

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
