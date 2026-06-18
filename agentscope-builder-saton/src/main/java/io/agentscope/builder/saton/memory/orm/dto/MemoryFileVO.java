package io.agentscope.builder.saton.memory.orm.dto;

/**
 * 单个记忆文件的元信息。
 *
 * @param kind       URL slug,如 {@code persona} / {@code long-term}
 * @param fileName   实际文件名,如 {@code AGENTS.md} / {@code MEMORY.md}
 * @param exists     文件是否存在(不存在时 size=0、modifiedAt=null)
 * @param size       字节数
 * @param modifiedAt 最近修改的 epoch 毫秒;不存在时为 {@code null}
 */
public record MemoryFileVO(String kind, String fileName, boolean exists, long size, Long modifiedAt) { }
