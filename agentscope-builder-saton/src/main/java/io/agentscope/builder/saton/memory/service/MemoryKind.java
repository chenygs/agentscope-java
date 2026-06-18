package io.agentscope.builder.saton.memory.service;

/**
 * 用户级记忆文件种类。URL 上以 kind slug 出现({@link #slug()}),内部映射到具体文件名。
 *
 * <p>未来要新增日流水(memory/YYYY-MM-DD.md)等只需扩枚举,API 形态保持不变。
 */
public enum MemoryKind {

    /** 人格 / 行为约定 — 用户手写。 */
    PERSONA("persona", "AGENTS.md"),

    /** 长期记忆 — 用户可手写,运行时也会自动追加合并。 */
    LONG_TERM("long-term", "MEMORY.md");

    private final String slug;
    private final String fileName;

    MemoryKind(String slug, String fileName) {
        this.slug = slug;
        this.fileName = fileName;
    }

    public String slug() {
        return slug;
    }

    public String fileName() {
        return fileName;
    }

    /** URL slug → enum,大小写不敏感。未知值抛 {@link IllegalArgumentException}。 */
    public static MemoryKind fromSlug(String slug) {
        if (slug == null) {
            throw new IllegalArgumentException("memory kind must not be null");
        }
        for (MemoryKind k : values()) {
            if (k.slug.equalsIgnoreCase(slug)) return k;
        }
        throw new IllegalArgumentException("unknown memory kind: " + slug);
    }
}
