package com.brainmed.ai.qa.core.tool;

import io.agentscope.core.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 获取当前时间工具：返回精确的当前日期和时间，供模型在需要时间感知时调用。
 */
public class CurrentTimeTool {

    private static final Logger log = LoggerFactory.getLogger(CurrentTimeTool.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm:ss");

    @Tool(
            name = "get_current_time",
            description = """
                    获取当前的精确日期和时间（年月日、星期、时分秒）。
                    当用户的问题涉及 "今天""现在""最近""最新"等时间相关概念时，请先调用此工具确认当前时间。
                    """, readOnly = true,
            concurrencySafe = true)
    public Mono<String> getCurrentTime() {
        String now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(FMT);
        log.info("get_current_time called, now={}", now);
        return Mono.just("当前时间：" + now + "（北京时间）");
    }
}
