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
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

final class JavaMethodParser {

    private JavaMethodParser() {
    }

    static List<MethodDescriptor> parse(String className, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler is available");
        }

        try {
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    null,
                    null,
                    List.of("-proc:none"),
                    null,
                    List.of(new SourceFileObject(className, source))
            );
            Iterable<? extends CompilationUnitTree> units = task.parse();
            return collectMethods(task, units);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static String sourcePath(String className) {
        String normalized = className.endsWith(".java")
                ? className.substring(0, className.length() - ".java".length())
                : className;
        return normalized.replace('.', '/') + ".java";
    }

    static URI sourceUri(String className) {
        return URI.create("string:///" + sourcePath(className));
    }

    private static List<MethodDescriptor> collectMethods(JavacTask task,
                                                         Iterable<? extends CompilationUnitTree> units) {
        Trees trees = Trees.instance(task);
        List<MethodDescriptor> methods = new ArrayList<>();
        for (CompilationUnitTree unit : units) {
            SourcePositions positions = trees.getSourcePositions();
            new MethodScanner(unit, positions, methods).scan(unit, null);
        }
        return methods;
    }

    private static final class MethodScanner extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final String packageName;
        private final SourcePositions positions;
        private final List<MethodDescriptor> methods;
        private final List<String> enclosingClassNames = new ArrayList<>();

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
            try {
                return super.visitClass(node, null);
            } finally {
                enclosingClassNames.remove(enclosingClassNames.size() - 1);
            }
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            if (node.getBody() == null || node.getReturnType() == null) {
                return null;
            }

            long start = positions.getStartPosition(unit, node);
            long bodyEndExclusive = positions.getEndPosition(unit, node.getBody());
            int startLine = lineNumber(start);
            int endLine = lineNumber(Math.decrementExact((int) bodyEndExclusive));
            int complexity = ComplexityCounter.count(node);
            methods.add(new MethodDescriptor(currentClassName(), node.getName().toString(), startLine, endLine, complexity));
            return null;
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

    private static final class SourceFileObject extends SimpleJavaFileObject {
        private final String source;

        private SourceFileObject(String className, String source) {
            super(uriFor(className), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

        private static URI uriFor(String className) {
            return sourceUri(className);
        }
    }
}

