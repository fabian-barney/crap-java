package media.barney.crap.core;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaMethodParserTest {

    @Test
    void extractsConcreteMethodsWithLinesAndComplexity() {
        String source = """
                package demo;
                class Sample {
                    int alpha(boolean a, boolean b) {
                        if (a && b) {
                            return 1;
                        }
                        return 0;
                    }

                    int beta(int x) {
                        switch (x) {
                            case 1: return 1;
                            case 2: return 2;
                            default: return 0;
                        }
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("demo.Sample", source);

        assertEquals(List.of(
                new MethodDescriptor("demo.Sample", "alpha", 3, 8, 3),
                new MethodDescriptor("demo.Sample", "beta", 10, 16, 4)
        ), methods);
    }

    @Test
    void ignoresConstructorsAndAbstractMethods() {
        String source = """
                abstract class Sample {
                    Sample() {
                    }

                    abstract int missing();

                    int present() {
                        return 1;
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(new MethodDescriptor("Sample", "present", 7, 9, 1)), methods);
    }

    @Test
    void ignoresMethodsDeclaredInsideAnonymousClasses() {
        String source = """
                class Sample {
                    int outer() {
                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {
                                if (true) {
                                }
                            }
                        };
                        return 1;
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(new MethodDescriptor("Sample", "outer", 2, 11, 1)), methods);
    }

    @Test
    void parsesMethodsWithoutResolvingSiblingTypes() {
        String source = """
                package demo;

                class Sample {
                    Helper helper() {
                        return new Helper();
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("demo.Sample", source);

        assertEquals(List.of(new MethodDescriptor("demo.Sample", "helper", 4, 6, 1)), methods);
    }

    @Test
    void ignoresKeywordsInsideCommentsAndStrings() {
        String source = """
                class Sample {
                    int stable() {
                        String text = "if && || ? case default catch";
                        // if && || ? case default catch
                        /* if && || ? case default catch */
                        return 1;
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(new MethodDescriptor("Sample", "stable", 2, 7, 1)), methods);
    }

    @Test
    void countsDecisionNodesFromTheAst() {
        String source = """
                class Sample {
                    int score(boolean a, boolean b, int[] values) {
                        for (int i = 0; i < values.length; i++) {
                        }
                        for (int value : values) {
                        }
                        while (a) {
                            a = false;
                        }
                        do {
                            b = false;
                        } while (b);
                        if (a && b || values.length > 0) {
                        }
                        try {
                            return a ? 1 : 0;
                        } catch (RuntimeException ex) {
                            return 2;
                        }
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(new MethodDescriptor("Sample", "score", 2, 20, 10)), methods);
    }

    @Test
    void visitsNestedDecisionNodesInsideOtherDecisionNodes() {
        String source = """
                class Sample {
                    int nested(boolean a, boolean b, int[] values) {
                        for (int i = 0; i < values.length; i++) {
                            if (a) {
                            }
                        }
                        for (int value : values) {
                            if (b) {
                            }
                        }
                        while (a) {
                            if (b) {
                            }
                            a = false;
                        }
                        do {
                            if (a) {
                            }
                            b = false;
                        } while (b);
                        try {
                            return a ? (b ? 1 : 0) : 2;
                        } catch (RuntimeException ex) {
                            if (values.length > 0) {
                                return values[0];
                            }
                            return 3;
                        }
                    }

                    int switched(int value) {
                        switch (value) {
                            case 1:
                                if (value > 0) {
                                    return 1;
                                }
                                return 0;
                            default:
                                return 2;
                        }
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("Sample", source);

        assertEquals(List.of(
                new MethodDescriptor("Sample", "nested", 2, 29, 13),
                new MethodDescriptor("Sample", "switched", 31, 41, 4)
        ), methods);
    }

    @Test
    void includesOwningClassNameForNestedAndSecondaryTypes() {
        String source = """
                package demo;

                class Outer {
                    int outer() {
                        return 1;
                    }

                    static class Inner {
                        int inner() {
                            return 2;
                        }
                    }
                }

                class Secondary {
                    int beta() {
                        return 3;
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("demo.Outer", source);

        assertEquals(List.of(
                new MethodDescriptor("demo.Outer", "outer", 4, 6, 1),
                new MethodDescriptor("demo.Outer$Inner", "inner", 9, 11, 1),
                new MethodDescriptor("demo.Secondary", "beta", 16, 18, 1)
        ), methods);
    }

    @Test
    void acceptsClassNamesWithJavaSuffix() {
        String source = """
                class Sample {
                    int value() {
                        return 1;
                    }
                }
                """;

        List<MethodDescriptor> methods = JavaMethodParser.parse("demo.Sample.java", source);

        assertEquals(List.of(new MethodDescriptor("Sample", "value", 2, 4, 1)), methods);
    }

    @Test
    void buildsSourcePathAndUriFromClassNames() {
        assertEquals("demo/Sample.java", JavaMethodParser.sourcePath("demo.Sample"));
        assertEquals("demo/Sample.java", JavaMethodParser.sourcePath("demo.Sample.java"));
        assertEquals(URI.create("string:///demo/Sample.java"), JavaMethodParser.sourceUri("demo.Sample"));
        assertEquals(URI.create("string:///demo/Sample.java"), JavaMethodParser.sourceUri("demo.Sample.java"));
    }
}

