package media.barney.crapjava.core;

enum CoverageMode {
    GENERATE,
    USE_EXISTING;

    boolean shouldGenerateCoverage() {
        return this == GENERATE;
    }
}
