package io.github.nguyenductrongdev.automigration.cassandra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringBootCompatibilityTest {

    @Test
    void acceptsEveryDocumentedMinorLine() {
        assertThatCode(() -> SpringBootCompatibility.verifySupported("3.5.0")).doesNotThrowAnyException();
        assertThatCode(() -> SpringBootCompatibility.verifySupported("3.5.16")).doesNotThrowAnyException();
        assertThatCode(() -> SpringBootCompatibility.verifySupported("4.0.7")).doesNotThrowAnyException();
        assertThatCode(() -> SpringBootCompatibility.verifySupported("4.1.1-SNAPSHOT")).doesNotThrowAnyException();
    }

    @Test
    void rejectsUntestedMinorLines() {
        assertThatThrownBy(() -> SpringBootCompatibility.verifySupported("3.4.13"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("supports 3.5.x, 4.0.x, and 4.1.x");
        assertThatThrownBy(() -> SpringBootCompatibility.verifySupported("4.2.0"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnknownVersionFormats() {
        assertThatThrownBy(() -> SpringBootCompatibility.verifySupported(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SpringBootCompatibility.verifySupported("development"))
                .isInstanceOf(IllegalStateException.class);
    }
}