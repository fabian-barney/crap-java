package media.barney.crap.core;

import java.math.BigDecimal;

final class Thresholds {

    static final double DEFAULT = 6.0;
    // Keep the warning boundary independent from the recommended default.
    private static final double TOO_LENIENT_BOUNDARY = 8.0;

    private Thresholds() {
    }

    static double parse(String value) {
        try {
            return validate(Double.parseDouble(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Threshold must be a finite number greater than 0", ex);
        }
    }

    static double validate(double value) {
        if (!Double.isFinite(value) || Double.compare(value, 0.0) <= 0) {
            throw new IllegalArgumentException("Threshold must be a finite number greater than 0");
        }
        return value;
    }

    static boolean isLikelyTooNoisy(double value) {
        return Double.compare(value, 4.0) < 0;
    }

    static boolean isTooLenient(double value) {
        return Double.compare(value, TOO_LENIENT_BOUNDARY) > 0;
    }

    static String warning(double value) {
        if (isLikelyTooNoisy(value)) {
            return "Warning: CRAP threshold below 4.0 is likely too noisy. "
                    + recommendation();
        }
        if (isTooLenient(value)) {
            return "Warning: CRAP threshold above " + formatBoundary(TOO_LENIENT_BOUNDARY)
                    + " is too lenient even for hard gates. " + recommendation();
        }
        return "";
    }

    private static String formatBoundary(double value) {
        BigDecimal boundary = BigDecimal.valueOf(value).stripTrailingZeros();
        if (boundary.scale() <= 0) {
            return boundary.toPlainString() + ".0";
        }
        return boundary.toPlainString();
    }

    private static String recommendation() {
        return "Use 8.0 for hard gates, target 6.0 during implementation, "
                + "and use the 6.0 default when in doubt.";
    }
}
