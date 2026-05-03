package media.barney.crap.gradle;

import org.gradle.api.logging.Logger;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradleLoggingPrintStreamsTest {

    @Test
    void standardOutLogsCompleteLinesAndFlushesTrailingLine() {
        List<String> lifecycle = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try (PrintStream stream = GradleLoggingPrintStreams.standardOut(logger(lifecycle, warnings))) {
            stream.print("alpha");
            stream.write('\r');
            stream.write('\n');
            stream.print("beta");
            stream.flush();
            stream.flush();
        }

        assertEquals(List.of("alpha", "beta"), lifecycle);
        assertEquals(List.of(), warnings);
    }

    @Test
    void standardErrLogsWarnings() {
        List<String> lifecycle = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try (PrintStream stream = GradleLoggingPrintStreams.standardErr(logger(lifecycle, warnings))) {
            stream.println("problem");
        }

        assertEquals(List.of(), lifecycle);
        assertEquals(List.of("problem"), warnings);
    }

    private static Logger logger(List<String> lifecycle, List<String> warnings) {
        return (Logger) Proxy.newProxyInstance(
                Logger.class.getClassLoader(),
                new Class<?>[]{Logger.class},
                (proxy, method, args) -> {
                    if (args != null && args.length == 1 && args[0] instanceof String message) {
                        if ("lifecycle".equals(method.getName())) {
                            lifecycle.add(message);
                            return null;
                        }
                        if ("warn".equals(method.getName())) {
                            warnings.add(message);
                            return null;
                        }
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || void.class.equals(returnType)) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0.0f;
        }
        return 0.0d;
    }
}
