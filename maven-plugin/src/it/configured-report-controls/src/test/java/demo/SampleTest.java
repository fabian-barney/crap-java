package demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SampleTest {

    @Test
    void alphaReturnsOneForTrue() {
        assertEquals(1, new Sample().alpha(true));
    }
}
