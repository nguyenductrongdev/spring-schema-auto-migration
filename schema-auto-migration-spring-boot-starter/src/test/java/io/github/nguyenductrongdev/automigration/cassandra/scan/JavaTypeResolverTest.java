package io.github.nguyenductrongdev.automigration.cassandra.scan;

import org.junit.jupiter.api.Test;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaTypeResolverTest {

    private final JavaTypeResolver resolver = new JavaTypeResolver();

    @Test
    void resolvesScalarAndCollectionTypes() throws NoSuchFieldException {
        assertThat(resolver.resolve(String.class)).isEqualTo("text");
        assertThat(resolver.resolve(UUID.class)).isEqualTo("uuid");
        assertThat(resolver.resolve(Instant.class)).isEqualTo("timestamp");
        assertThat(resolveField("tags")).isEqualTo("set<text>");
        assertThat(resolveField("scores")).isEqualTo("map<text, int>");
        assertThat(resolveField("addresses")).isEqualTo("list<frozen<\"address\">>");
    }

    @Test
    void rejectsUnknownTypesInsteadOfGuessing() {
        assertThatThrownBy(() -> resolver.resolve(UnsupportedValue.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(UnsupportedValue.class.getName());
    }

    private String resolveField(String name) throws NoSuchFieldException {
        Field field = TypeSamples.class.getDeclaredField(name);
        return resolver.resolve(field.getGenericType());
    }

    @SuppressWarnings("unused")
    private static class TypeSamples {
        private Set<String> tags;
        private Map<String, Integer> scores;
        private List<Address> addresses;
    }

    @UserDefinedType("address")
    private static class Address {
        private String street;
    }

    private static class UnsupportedValue {
    }
}
