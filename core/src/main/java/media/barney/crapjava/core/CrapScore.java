package media.barney.crapjava.core;

import org.jspecify.annotations.Nullable;

final class CrapScore {

    private CrapScore() {
    }

    static @Nullable Double calculate(int complexity, @Nullable Double coveragePercent) {
        if (coveragePercent == null) {
            return null;
        }
        double cc = complexity;
        double uncovered = 1.0 - (coveragePercent / 100.0);
        return (cc * cc * uncovered * uncovered * uncovered) + cc;
    }
}

/* mutate4java-manifest
version=1
moduleHash=7b5549b918766b6d093d959777868af63c8e42636963918872d84b62ff8a2e4b
scope.0.id=Y2xhc3M6Q3JhcFNjb3JlI0NyYXBTY29yZToz
scope.0.kind=class
scope.0.startLine=3
scope.0.endLine=16
scope.0.semanticHash=5d7026673c6b59315c82ac037754aa1a75c53e1ed261bc9d3bf4d2079340a643
scope.1.id=bWV0aG9kOkNyYXBTY29yZSNjYWxjdWxhdGUoMik6OA
scope.1.kind=method
scope.1.startLine=8
scope.1.endLine=15
scope.1.semanticHash=b6899c56720a2489fa82a88edbea39a4ed55addc0ea15883a701a8544f355568
scope.2.id=bWV0aG9kOkNyYXBTY29yZSNjdG9yKDApOjU
scope.2.kind=method
scope.2.startLine=5
scope.2.endLine=6
scope.2.semanticHash=cdb5a31015beb2e9ff75ebdc8edb49a3c51443ab9d8442f5e7f80000d6426725
*/
