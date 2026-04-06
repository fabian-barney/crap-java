package media.barney.crap.core;

enum CoverageMode {
    GENERATE,
    USE_EXISTING;

    boolean shouldGenerateCoverage() {
        return this == GENERATE;
    }
}

