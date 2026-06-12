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
