package media.barney.crap.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class SourceExclusionMatcher {

    private static final List<String> DEFAULT_CLASS_REGEXES = List.of(
            "(^|.*\\.)generated(\\..*)?",
            "(^|.*\\.)gen(\\..*)?",
            "(^|.*\\.)[^.]*MapperImpl$",
            "(^|.*\\.)Dagger[^.]*$",
            "(^|.*\\.)Hilt_[^.]*$",
            "(^|.*\\.)AutoValue_[^.]*$"
    );
    private static final String DEFAULT_GENERATED_ANNOTATION = "Generated";

    private final Path projectRoot;
    private final boolean useDefaultExclusions;
    private final List<GlobRule> userPathRules;
    private final List<RegexRule> defaultClassRules;
    private final List<RegexRule> userClassRules;
    private final List<String> defaultAnnotationNames;
    private final List<String> userAnnotationNames;

    private SourceExclusionMatcher(Path projectRoot,
                                   SourceExclusionOptions options) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.useDefaultExclusions = options.useDefaultExclusions();
        this.userPathRules = options.excludes().stream()
                .map(pattern -> new GlobRule(pattern, globToRegex(pattern)))
                .toList();
        this.defaultClassRules = useDefaultExclusions
                ? DEFAULT_CLASS_REGEXES.stream().map(pattern -> regexRule(pattern, true)).toList()
                : List.of();
        this.userClassRules = options.excludeClasses().stream()
                .map(pattern -> regexRule(pattern, false))
                .toList();
        this.defaultAnnotationNames = useDefaultExclusions ? List.of(DEFAULT_GENERATED_ANNOTATION) : List.of();
        this.userAnnotationNames = List.copyOf(options.excludeAnnotations());
    }

    static SourceExclusionMatcher create(Path projectRoot, SourceExclusionOptions options) {
        return new SourceExclusionMatcher(projectRoot, options);
    }

    Optional<String> pathExclusionReason(Path file) {
        String relativePath = normalizedRelativePath(file);
        if (useDefaultExclusions) {
            Optional<String> defaultReason = defaultPathExclusionReason(relativePath);
            if (defaultReason.isPresent()) {
                return defaultReason;
            }
        }
        return userPathExclusionReason(relativePath);
    }

    private static Optional<String> defaultPathExclusionReason(String relativePath) {
        if (isUnderGeneratedDirectory(relativePath)) {
            return Optional.of("default:path:generated-directory");
        }
        if (isUnderSrcMainJavaGen(relativePath)) {
            return Optional.of("default:path:src-main-java-gen");
        }
        return Optional.empty();
    }

    private Optional<String> userPathExclusionReason(String relativePath) {
        for (GlobRule rule : userPathRules) {
            if (rule.pattern().matcher(relativePath).matches()) {
                return Optional.of("user:path:" + rule.glob());
            }
        }
        return Optional.empty();
    }

    Optional<String> classExclusionReason(String className, List<String> annotationNames) {
        return classRegexExclusionReason(className)
                .or(() -> annotationExclusionReason(annotationNames));
    }

    private Optional<String> classRegexExclusionReason(String className) {
        return matchingClassRule(className, defaultClassRules)
                .or(() -> matchingClassRule(className, userClassRules));
    }

    private static Optional<String> matchingClassRule(String className, List<RegexRule> rules) {
        for (RegexRule rule : rules) {
            if (rule.pattern().matcher(className).matches()) {
                return Optional.of(rule.reason());
            }
        }
        return Optional.empty();
    }

    private Optional<String> annotationExclusionReason(List<String> annotationNames) {
        for (String annotationName : annotationNames) {
            Optional<String> reason = annotationExclusionReason(annotationName, defaultAnnotationNames, "default");
            if (reason.isPresent()) {
                return reason;
            }
            reason = annotationExclusionReason(annotationName, userAnnotationNames, "user");
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> annotationExclusionReason(String annotationName,
                                                             List<String> excludedNames,
                                                             String origin) {
        for (String excludedName : excludedNames) {
            if (matchesAnnotation(annotationName, excludedName)) {
                return Optional.of(origin + ":annotation:" + excludedName);
            }
        }
        return Optional.empty();
    }

    private String normalizedRelativePath(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        Path path = normalized.startsWith(projectRoot) ? projectRoot.relativize(normalized) : normalized;
        return path.normalize().toString().replace('\\', '/');
    }

    private static boolean isUnderGeneratedDirectory(String normalizedPath) {
        int lastSeparator = normalizedPath.lastIndexOf('/');
        if (lastSeparator < 0) {
            return false;
        }
        String parentPath = normalizedPath.substring(0, lastSeparator);
        for (String segment : parentPath.split("/", -1)) {
            if (segment.contains("generated")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnderSrcMainJavaGen(String normalizedPath) {
        String marker = "src/main/java-gen/";
        return normalizedPath.startsWith(marker) || normalizedPath.contains("/" + marker);
    }

    private static boolean matchesAnnotation(String annotationName, String excludedName) {
        return annotationName.equals(excludedName) || simpleName(annotationName).equals(simpleName(excludedName));
    }

    private static String simpleName(String name) {
        int separator = name.lastIndexOf('.');
        return separator < 0 ? name : name.substring(separator + 1);
    }

    private static RegexRule regexRule(String pattern, boolean defaultRule) {
        try {
            return new RegexRule(
                    pattern,
                    Pattern.compile(pattern),
                    (defaultRule ? "default:class:" : "user:class:") + pattern
            );
        } catch (PatternSyntaxException ex) {
            throw new IllegalArgumentException("Invalid exclude-class regex: " + pattern, ex);
        }
    }

    private static Pattern globToRegex(String glob) {
        String normalized = normalizeGlob(glob);
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < normalized.length(); index++) {
            index = appendGlobToken(regex, normalized, index);
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    private static int appendGlobToken(StringBuilder regex, String glob, int index) {
        char current = glob.charAt(index);
        if (current == '*') {
            return appendStarGlobToken(regex, glob, index);
        }
        if (current == '?') {
            regex.append("[^/]");
            return index;
        }
        appendRegexLiteral(regex, current);
        return index;
    }

    private static int appendStarGlobToken(StringBuilder regex, String glob, int index) {
        if (index + 1 >= glob.length() || glob.charAt(index + 1) != '*') {
            regex.append("[^/]*");
            return index;
        }
        if (index + 2 < glob.length() && glob.charAt(index + 2) == '/') {
            regex.append("(?:.*/)?");
            return index + 2;
        }
        regex.append(".*");
        return index + 1;
    }

    private static String normalizeGlob(String glob) {
        String normalized = glob.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static void appendRegexLiteral(StringBuilder regex, char current) {
        if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
            regex.append('\\');
        }
        regex.append(current);
    }

    static List<Path> filterFiles(Path projectRoot,
                                  List<Path> files,
                                  SourceExclusionOptions options,
                                  SourceExclusionAudit.Builder audit) {
        SourceExclusionMatcher matcher = create(projectRoot, options);
        return filterFiles(files, matcher, audit);
    }

    static List<Path> filterFiles(List<Path> files,
                                  SourceExclusionMatcher matcher,
                                  SourceExclusionAudit.Builder audit) {
        List<Path> included = new ArrayList<>();
        for (Path file : files) {
            audit.recordCandidateFile();
            Optional<String> reason = matcher.pathExclusionReason(file);
            if (reason.isPresent()) {
                audit.recordExcludedFile(reason.get());
                continue;
            }
            audit.recordAnalyzedFile();
            included.add(file);
        }
        return included;
    }

    private record GlobRule(String glob, Pattern pattern) {
    }

    private record RegexRule(String regex, Pattern pattern, String reason) {
    }
}
