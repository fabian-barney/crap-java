package media.barney.crap.core;

record CoverageData(int missedInstructions, int coveredInstructions) {

    double coveragePercent() {
        int total = missedInstructions + coveredInstructions;
        if (total == 0) {
            return 0.0;
        }
        return (coveredInstructions * 100.0) / total;
    }
}

