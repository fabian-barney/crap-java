package media.barney.crapjava.core;

import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CrapScoreTest {

    @Test
    void returnsComplexityWhenFullyCovered() {
        assertEquals(5.0, Objects.requireNonNull(CrapScore.calculate(5, 100.0)), 0.0001);
    }

    @Test
    void returnsCcSquaredPlusCcWhenUncovered() {
        assertEquals(30.0, Objects.requireNonNull(CrapScore.calculate(5, 0.0)), 0.0001);
    }

    @Test
    void computesPartialCoverage() {
        assertEquals(18.648, Objects.requireNonNull(CrapScore.calculate(8, 45.0)), 0.01);
    }

    @Test
    void returnsNullForUnknownCoverage() {
        assertNull(CrapScore.calculate(3, null));
    }
}
