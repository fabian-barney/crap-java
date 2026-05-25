package media.barney.crap.core;

import java.util.List;
import java.util.Objects;

public record SourceExclusionOptions(
        List<String> excludes,
        List<String> excludeClasses,
        List<String> excludeAnnotations,
        boolean useDefaultExclusions
) {
    public SourceExclusionOptions {
        excludes = normalized(excludes);
        excludeClasses = normalized(excludeClasses);
        excludeAnnotations = normalized(excludeAnnotations);
    }

    public static SourceExclusionOptions defaults() {
        return new SourceExclusionOptions(List.of(), List.of(), List.of(), true);
    }

    @Override
    public List<String> excludes() {
        return List.copyOf(excludes);
    }

    @Override
    public List<String> excludeClasses() {
        return List.copyOf(excludeClasses);
    }

    @Override
    public List<String> excludeAnnotations() {
        return List.copyOf(excludeAnnotations);
    }

    private static List<String> normalized(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return List.copyOf(values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList());
    }
}
