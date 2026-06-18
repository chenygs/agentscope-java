package io.agentscope.builder.saton.memory.orm.dto;

import java.util.List;
import io.agentscope.builder.saton.memory.service.MemoryKind;

/**
 * GET /api/memory 摘要:列出当前用户级 workspace 下所有记忆文件的元信息。
 *
 * @param root  用户级 workspace 根绝对路径
 * @param files 按 {@link io.agentscope.builder.saton.memory.service.MemoryKind} 顺序的元信息列表
 */
public record MemorySummaryVO(String root, List<MemoryFileVO> files) { }
