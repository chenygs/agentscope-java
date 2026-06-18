package io.agentscope.builder.saton.factory.service.model;

import io.agentscope.builder.saton.factory.service.core.Provider;
import io.agentscope.builder.saton.resource.model.orm.entity.ModelProviderEntity;
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
