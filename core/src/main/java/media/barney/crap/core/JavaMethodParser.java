package media.barney.crap.core;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.tools.JavaCompiler;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class JavaMethodParser {

    private JavaMethodParser() {
    }

    static List<MethodDescriptor> parse(String sourceName, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler is available");
        }

        try {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    null,
                    diagnostics,
                    List.of("-proc:none"),
                    null,
                    List.of(new SourceFileObject(sourceName, source))
            );
            Iterable<? extends CompilationUnitTree> units = task.parse();
            throwIfParseErrors(sourceName, diagnostics.getDiagnostics());
            return collectMethods(task, units);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static String sourcePath(String sourceName) {
        String normalized = sourceName.endsWith(".java")
                ? sourceName.substring(0, sourceName.length() - ".java".length())
                : sourceName;
        return normalized.replace('.', '/') + ".java";
    }

    static URI sourceUri(String sourceName) {
        return URI.create("string:///" + sourcePath(sourceName));
    }

    static boolean hasKnownSourceRange(long start, long endExclusive) {
        return start != Diagnostic.NOPOS
                && endExclusive != Diagnostic.NOPOS
                && endExclusive > start;
    }

    private static void throwIfParseErrors(String sourceName, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        List<String> errors = diagnostics.stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(JavaMethodParser::formatDiagnostic)
                .toList();
        if (!errors.isEmpty()) {
            throw new JavaMethodParseException(sourcePath(sourceName), errors);
        }
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        return formatDiagnosticLocation(diagnostic.getLineNumber(), diagnostic.getColumnNumber())
                + ": " + diagnostic.getMessage(Locale.ROOT);
    }

    static String formatDiagnosticLocation(long line, long column) {
        if (line < 0) {
            return "unknown location";
        }
        if (column < 0) {
            return "line " + line;
        }
        return "line " + line + ", column " + column;
    }

    private static List<MethodDescriptor> collectMethods(JavacTask task,
                                                         Iterable<? extends CompilationUnitTree> units) {
        List<MethodDescriptor> methods = new ArrayList<>();
        Trees trees = Trees.instance(task);
        for (CompilationUnitTree unit : units) {
            collectMethods(unit, trees.getSourcePositions(), methods);
        }
        return methods;
    }

    static List<MethodDescriptor> collectMethods(CompilationUnitTree unit, SourcePositions positions) {
        List<MethodDescriptor> methods = new ArrayList<>();
        collectMethods(unit, positions, methods);
        return methods;
    }

    private static void collectMethods(CompilationUnitTree unit,
                                       SourcePositions positions,
                                       List<MethodDescriptor> methods) {
        new MethodScanner(unit, positions, methods).scan(unit, null);
    }

    private static final class MethodScanner extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final String packageName;
        private final SourcePositions positions;
        private final List<MethodDescriptor> methods;
        private final List<String> enclosingClassNames = new ArrayList<>();
        private final List<List<String>> enclosingClassAnnotations = new ArrayList<>();

        private MethodScanner(CompilationUnitTree unit,
                              SourcePositions positions,
                              List<MethodDescriptor> methods) {
            this.unit = unit;
            this.packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            this.positions = positions;
            this.methods = methods;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            String simpleName = node.getSimpleName().toString();
            if (simpleName.isEmpty()) {
                return null;
            }
            enclosingClassNames.add(simpleName);
            enclosingClassAnnotations.add(classAnnotations(node));
            try {
                return super.visitClass(node, null);
            } finally {
                enclosingClassAnnotations.remove(enclosingClassAnnotations.size() - 1);
                enclosingClassNames.remove(enclosingClassNames.size() - 1);
            }
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            if (node.getBody() == null) {
                return null;
            }

            long start = positions.getStartPosition(unit, node);
            long bodyEndExclusive = positions.getEndPosition(unit, node.getBody());
            if (!hasKnownSourceRange(start, bodyEndExclusive)) {
                return null;
            }
            int startLine = lineNumber(start);
            int endLine = lineNumber(Math.decrementExact(bodyEndExclusive));
            int complexity = ComplexityCounter.count(node);
            methods.add(new MethodDescriptor(
                    currentClassName(),
                    node.getName().toString(),
                    startLine,
                    endLine,
                    complexity,
                    currentClassAnnotations()
            ));
            return null;
        }

        private static List<String> classAnnotations(ClassTree node) {
            return node.getModifiers().getAnnotations().stream()
                    .map(annotation -> annotation.getAnnotationType().toString())
                    .toList();
        }

        private int lineNumber(long position) {
            return (int) unit.getLineMap().getLineNumber(position);
        }

        private String currentClassName() {
            String simpleName = String.join("$", enclosingClassNames);
            if (packageName.isEmpty()) {
                return simpleName;
            }
            return packageName + "." + simpleName;
        }

        private List<String> currentClassAnnotations() {
            if (enclosingClassAnnotations.isEmpty()) {
                return List.of();
            }
            return enclosingClassAnnotations.get(enclosingClassAnnotations.size() - 1);
        }
    }

    private static final class ComplexityCounter extends TreeScanner<Void, Void> {
        private int complexity = 1;

        static int count(MethodTree method) {
            ComplexityCounter counter = new ComplexityCounter();
            counter.scan(method.getBody(), null);
            return counter.complexity;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            return null;
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
            return null;
        }

        @Override
        public Void visitIf(IfTree node, Void unused) {
            complexity++;
            return super.visitIf(node, null);
        }

        @Override
        public Void visitForLoop(ForLoopTree node, Void unused) {
            complexity++;
            return super.visitForLoop(node, null);
        }

        @Override
        public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void unused) {
            complexity++;
            return super.visitEnhancedForLoop(node, null);
        }

        @Override
        public Void visitWhileLoop(WhileLoopTree node, Void unused) {
            complexity++;
            return super.visitWhileLoop(node, null);
        }

        @Override
        public Void visitDoWhileLoop(DoWhileLoopTree node, Void unused) {
            complexity++;
            return super.visitDoWhileLoop(node, null);
        }

        @Override
        public Void visitCatch(CatchTree node, Void unused) {
            complexity++;
            return super.visitCatch(node, null);
        }

        @Override
        public Void visitConditionalExpression(ConditionalExpressionTree node, Void unused) {
            complexity++;
            return super.visitConditionalExpression(node, null);
        }

        @Override
        public Void visitCase(CaseTree node, Void unused) {
            complexity++;
            return super.visitCase(node, null);
        }

        @Override
        public Void visitBinary(BinaryTree node, Void unused) {
            if (node.getKind() == Tree.Kind.CONDITIONAL_AND || node.getKind() == Tree.Kind.CONDITIONAL_OR) {
                complexity++;
            }
            return super.visitBinary(node, null);
        }
    }

    static final class JavaMethodParseException extends IllegalArgumentException {
        private JavaMethodParseException(String sourcePath, List<String> diagnostics) {
            super("Unable to parse Java source " + sourcePath + ":\n" + String.join("\n", diagnostics));
        }
    }

    private static final class SourceFileObject extends SimpleJavaFileObject {
        private final String source;

        private SourceFileObject(String sourceName, String source) {
            super(uriFor(sourceName), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

        private static URI uriFor(String sourceName) {
            return sourceUri(sourceName);
        }
    }
}

