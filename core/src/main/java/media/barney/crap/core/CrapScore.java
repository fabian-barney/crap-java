package media.barney.crap.core;

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

