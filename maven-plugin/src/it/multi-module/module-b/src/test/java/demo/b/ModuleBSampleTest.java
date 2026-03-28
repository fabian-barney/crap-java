package demo.b;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleBSampleTest {

    @Test
    void betaReturnsTwo() {
        assertEquals(2, new ModuleBSample().beta());
    }
}
