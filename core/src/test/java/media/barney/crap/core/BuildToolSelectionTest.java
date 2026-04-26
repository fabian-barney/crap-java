package media.barney.crap.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildToolSelectionTest {

    @Test
    void parsesKnownSelections() {
        assertEquals(BuildToolSelection.AUTO, BuildToolSelection.parse("auto"));
        assertEquals(BuildToolSelection.MAVEN, BuildToolSelection.parse("maven"));
        assertEquals(BuildToolSelection.GRADLE, BuildToolSelection.parse("gradle"));
        assertEquals(BuildToolSelection.GRADLE, BuildToolSelection.parse("GRADLE"));
    }

    @Test
    void rejectsMissingBlankAndUnknownSelections() {
        assertThrows(IllegalArgumentException.class, () -> BuildToolSelection.parse(null));
        assertThrows(IllegalArgumentException.class, () -> BuildToolSelection.parse(" "));
        assertThrows(IllegalArgumentException.class, () -> BuildToolSelection.parse("ant"));
    }

    @Test
    void mapsConcreteSelectionsToBuildTools() {
        assertEquals(BuildTool.MAVEN, BuildToolSelection.MAVEN.toBuildTool());
        assertEquals(BuildTool.GRADLE, BuildToolSelection.GRADLE.toBuildTool());
    }

    @Test
    void autoSelectionDoesNotMapToConcreteBuildTool() {
        assertThrows(IllegalStateException.class, () -> BuildToolSelection.AUTO.toBuildTool());
    }
}
