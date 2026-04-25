package media.barney.crap.core;

enum MethodStatus {
    PASSED("passed"),
    FAILED("failed"),
    SKIPPED("skipped");

    private final String value;

    MethodStatus(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}
