package org.schabi.newpipe.local.subscription.item;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class EmptyStatePlaceholderIntegrationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void emptyStateItemsDoNotUseSharedRuntimeViewBinding() throws Exception {
        try (Stream<Path> sources = Files.walk(sourceDirectory)) {
            final boolean usesFragileBinding = sources
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")
                            || path.toString().endsWith(".kt"))
                    .anyMatch(this::usesListEmptyViewBinding);

            assertFalse(
                    "Empty-state items must use their matching static layout without "
                            + "ListEmptyViewBinding.bind(view)",
                    usesFragileBinding);
        }
    }

    private boolean usesListEmptyViewBinding(final Path path) {
        try {
            return Files.readString(path).contains("ListEmptyViewBinding");
        } catch (final Exception exception) {
            throw new IllegalStateException("Could not audit " + path, exception);
        }
    }
}
