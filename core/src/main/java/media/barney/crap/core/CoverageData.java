package media.barney.crap.core;

import org.jspecify.annotations.Nullable;

record CoverageData(CoverageCounter instructionCoverage, @Nullable CoverageCounter branchCoverage) {

    static final String INSTRUCTION_KIND = "instruction";
    static final String BRANCH_KIND = "branch";
    static final String UNAVAILABLE_KIND = "N/A";

    CoverageData(int missedInstructions, int coveredInstructions) {
        this(new CoverageCounter(missedInstructions, coveredInstructions), null);
    }

    CoverageData(int missedInstructions, int coveredInstructions, int missedBranches, int coveredBranches) {
        this(
                new CoverageCounter(missedInstructions, coveredInstructions),
                new CoverageCounter(missedBranches, coveredBranches)
        );
    }

    EffectiveCoverage effectiveCoverage() {
        double instructionPercent = instructionCoverage.coveragePercent();
        if (branchCoverage == null) {
            return new EffectiveCoverage(instructionPercent, INSTRUCTION_KIND);
        }

        double branchPercent = branchCoverage.coveragePercent();
        if (Double.compare(branchPercent, instructionPercent) < 0) {
            return new EffectiveCoverage(branchPercent, BRANCH_KIND);
        }
        return new EffectiveCoverage(instructionPercent, INSTRUCTION_KIND);
    }

    double coveragePercent() {
        return effectiveCoverage().percent();
    }

    String coverageKind() {
        return effectiveCoverage().kind();
    }
}

record CoverageCounter(int missed, int covered) {

    double coveragePercent() {
        int total = missed + covered;
        if (total == 0) {
            return 0.0;
        }
        return (covered * 100.0) / total;
    }
}

record EffectiveCoverage(double percent, String kind) {
}

