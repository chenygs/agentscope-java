package com.brainmed.ai.qa.config;

import java.util.Base64;
import java.util.Map;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 配置：初始化 GlobalOpenTelemetry，将 Agent 追踪数据导出到 OTLP 后端。
 *
 * <p>AgentScope 内置的 {@code OtelTracingMiddleware} 使用 {@code GlobalOpenTelemetry.getTracer()}
 * 获取 Tracer，因此只需在此初始化全局 SDK 即可。
 *
 * <p>后端推荐：
 * <ul>
 *   <li>Langfuse：开源 LLM 可观测平台，支持 OTLP，UI 友好</li>
 *   <li>开发环境：{@code docker run -d -p 16686:16686 -p 4317:4317 jaegertracing/all-in-one:latest}</li>
 *   <li>生产环境：Grafana Tempo / 阿里云 ARMS / Datadog 等支持 OTLP 的平台</li>
 * </ul>
 */
@Configuration
@Slf4j
public class OpenTelemetryConfig {

    @Value("${otel.exporter.endpoint:http://localhost:4317}")
    private String endpoint;

    @Value("${otel.service.name:ai-qa}")
    private String serviceName;

    @Value("${otel.enabled:true}")
    private boolean enabled;

    /** 开发模式：每条 span 立即发送（SimpleSpanProcessor），生产建议用 batch。 */
    @Value("${otel.simple-exporter:true}")
    private boolean simpleExporter;

    /** 自定义 HTTP headers（用于 Langfuse 等需要认证的 OTLP 后端）。 */
    @Value("${otel.exporter.headers:}")
    private String headersConfig;

    /** Langfuse 专用配置（可选）。 */
    @Value("${otel.exporter.langfuse.public-key:}")
    private String langfusePublicKey;

    @Value("${otel.exporter.langfuse.secret-key:}")
    private String langfuseSecretKey;

    private OpenTelemetrySdk openTelemetrySdk;

    @Bean
    public OpenTelemetry openTelemetry() {
        if (!enabled) {
            log.info("OpenTelemetry 已禁用（otel.enabled=false），使用 no-op provider");
            return GlobalOpenTelemetry.get();
        }

        Resource resource =
                Resource.getDefault().toBuilder()
                        .put(AttributeKey.stringKey("service.name"), serviceName)
                        .build();

        // 根据端口自动选择 gRPC (4317) 或 HTTP (4318) 导出器
        SpanExporter exporter = buildExporter();

        // 开发模式用 SimpleSpanProcessor（每条立即发送），生产用 BatchSpanProcessor
        var spanProcessor = simpleExporter
                ? SimpleSpanProcessor.create(exporter)
                : BatchSpanProcessor.builder(exporter).build();

        SdkTracerProvider tracerProvider =
                SdkTracerProvider.builder()
                        .addSpanProcessor(spanProcessor)
                        .setResource(resource)
                        .build();

        openTelemetrySdk =
                OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

        // 注册到全局，OtelTracingMiddleware 通过 GlobalOpenTelemetry.getTracer() 使用
        try {
            GlobalOpenTelemetry.set(openTelemetrySdk);
        } catch (IllegalStateException e) {
            // 已被其他组件设置过，先 reset 再 set
            log.warn("GlobalOpenTelemetry 已被设置，尝试重置: {}", e.getMessage());
            GlobalOpenTelemetry.resetForTest();
            GlobalOpenTelemetry.set(openTelemetrySdk);
        }

        log.info(
                "OpenTelemetry 已初始化 service={} endpoint={}", serviceName, endpoint);

        return openTelemetrySdk;
    }

    @PreDestroy
    public void shutdown() {
        if (openTelemetrySdk != null) {
            openTelemetrySdk.close();
            GlobalOpenTelemetry.resetForTest();
            log.info("OpenTelemetry SDK 已关闭");
        }
    }

    /**
     * 构建 SpanExporter。
     * <p>优先级：Langfuse 配置 > 自定义 headers > 自动检测 gRPC/HTTP
     */
    private SpanExporter buildExporter() {
        // Langfuse 专用模式：自动生成 Authorization + ingestion header
        if (langfusePublicKey != null && !langfusePublicKey.isBlank()
                && langfuseSecretKey != null && !langfuseSecretKey.isBlank()) {
            String encoded = Base64.getEncoder().encodeToString(
                    (langfusePublicKey + ":" + langfuseSecretKey).getBytes());
            var builder = OtlpHttpSpanExporter.builder()
                    .setEndpoint(endpoint)
                    .addHeader("Authorization", "Basic " + encoded)
                    .addHeader("x-langfuse-ingestion-version", "4");
            log.info("[OTel] 使用 Langfuse 模式: endpoint={}", endpoint);
            return builder.build();
        }

        // 有自定义 headers 时使用 HTTP exporter
        if (headersConfig != null && !headersConfig.isBlank()) {
            var builder = OtlpHttpSpanExporter.builder()
                    .setEndpoint(endpoint.contains("/v1/traces") ? endpoint : endpoint + "/v1/traces");
            parseHeaders(headersConfig).forEach(builder::addHeader);
            log.info("[OTel] 使用 HTTP + 自定义 headers 模式");
            return builder.build();
        }

        // 默认：根据端口自动选择 gRPC(4317) 或 HTTP(4318)
        if (endpoint != null && endpoint.contains(":4318")) {
            return OtlpHttpSpanExporter.builder().setEndpoint(endpoint + "/v1/traces").build();
        }
        return OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).build();
    }

    /** 解析逗号分隔的 headers，格式：key1=value1,key2=value2 */
    private Map<String, String> parseHeaders(String config) {
        var map = new java.util.LinkedHashMap<String, String>();
        if (config == null || config.isBlank()) return map;
        for (String pair : config.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }
}
