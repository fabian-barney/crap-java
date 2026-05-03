package media.barney.crap.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportFormatTest {

    @Test
    void parsesKnownFormats() {
        assertEquals(ReportFormat.TOON, ReportFormat.parse("toon"));
        assertEquals(ReportFormat.JSON, ReportFormat.parse("json"));
        assertEquals(ReportFormat.TEXT, ReportFormat.parse("text"));
        assertEquals(ReportFormat.JUNIT, ReportFormat.parse("junit"));
        assertEquals(ReportFormat.NONE, ReportFormat.parse("none"));
        assertEquals(ReportFormat.JSON, ReportFormat.parse("JSON"));
    }

    @Test
    void rejectsUnknownFormats() {
        assertThrows(IllegalArgumentException.class, () -> ReportFormat.parse("yaml"));
    }
}
