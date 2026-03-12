package crap4java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class SourceFileFinder {

    private SourceFileFinder() {
    }

    static List<Path> findAllJavaFilesUnderSrc(Path projectRoot) throws IOException {
        Path src = projectRoot.resolve("src");
        if (!Files.exists(src)) {
            return List.of();
        }

        try (var stream = Files.walk(src)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }
}

/* mutate4java-manifest
version=1
moduleHash=21199b1e7988ac2b433d1228947d962a5a2af272adf722ee5f55cd2eeb385784
scope.0.id=Y2xhc3M6U291cmNlRmlsZUZpbmRlciNTb3VyY2VGaWxlRmluZGVyOjk
scope.0.kind=class
scope.0.startLine=9
scope.0.endLine=27
scope.0.semanticHash=e44d77790f24780d1574e94b00ec8055d64dda3940dee87617bd65fdc1033cbb
scope.1.id=bWV0aG9kOlNvdXJjZUZpbGVGaW5kZXIjY3RvcigwKToxMQ
scope.1.kind=method
scope.1.startLine=11
scope.1.endLine=12
scope.1.semanticHash=952989561249035658f6719d569bc70bd2b9d10da263124c47f965e77064bb82
scope.2.id=bWV0aG9kOlNvdXJjZUZpbGVGaW5kZXIjZmluZEFsbEphdmFGaWxlc1VuZGVyU3JjKDEpOjE0
scope.2.kind=method
scope.2.startLine=14
scope.2.endLine=26
scope.2.semanticHash=a11a3345bb541c4571b681b1447f0496b1f16c9b46d9f0449ca2162039b2e078
*/
