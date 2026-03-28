package demo.a;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleASampleTest {

    @Test
    void alphaReturnsOne() {
        assertEquals(1, new ModuleASample().alpha());
    }
}
