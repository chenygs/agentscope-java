package io.agentscope.builder.saton.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 基础工具集合 —— 演示 @Tool 注解方式
 */
@Component
public class BaseTools {

    @Tool(name = "get_current_time", description = "获取当前时间")
    public String getCurrentTime(
            @ToolParam(name = "format", description = "时间格式，默认 yyyy-MM-dd HH:mm:ss", required = false)
            String format) {
        if (format == null || format.isEmpty()) {
            format = "yyyy-MM-dd HH:mm:ss";
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
    }

}
