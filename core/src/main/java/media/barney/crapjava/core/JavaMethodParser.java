package media.barney.crapjava.core;

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
        private final SourcePositions positions;
        private final List<MethodDescriptor> methods;

        private MethodScanner(CompilationUnitTree unit,
                              SourcePositions positions,
                              List<MethodDescriptor> methods) {
            this.unit = unit;
            this.positions = positions;
            this.methods = methods;
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
            methods.add(new MethodDescriptor(node.getName().toString(), startLine, endLine, complexity));
            return null;
        }

        private int lineNumber(long position) {
            return (int) unit.getLineMap().getLineNumber(position);
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

/* mutate4java-manifest
version=1
moduleHash=d914c00de7c89d6f6fdc7231333611dae24dbb66a5341bf5bbd9053801057b78
scope.0.id=Y2xhc3M6SmF2YU1ldGhvZFBhcnNlciNKYXZhTWV0aG9kUGFyc2VyOjMx
scope.0.kind=class
scope.0.startLine=31
scope.0.endLine=201
scope.0.semanticHash=aab451eb5965cb96028bcea6cc71e6c9596b2534dc4a28d6f63bca30b525b57e
scope.1.id=Y2xhc3M6SmF2YU1ldGhvZFBhcnNlci5Db21wbGV4aXR5Q291bnRlciNDb21wbGV4aXR5Q291bnRlcjoxMTM
scope.1.kind=class
scope.1.startLine=113
scope.1.endLine=182
scope.1.semanticHash=b94e9cb6b24f6756c9a69b23de6ce9ba22b3c24398fc8ae71814f85082c05d4b
scope.2.id=Y2xhc3M6SmF2YU1ldGhvZFBhcnNlci5NZXRob2RTY2FubmVyI01ldGhvZFNjYW5uZXI6ODA
scope.2.kind=class
scope.2.startLine=80
scope.2.endLine=111
scope.2.semanticHash=c7d07a66d1c0c3df561c1dcf379f2aaad37363ac8d50597bc9540f545428efc4
scope.3.id=Y2xhc3M6SmF2YU1ldGhvZFBhcnNlci5Tb3VyY2VGaWxlT2JqZWN0I1NvdXJjZUZpbGVPYmplY3Q6MTg0
scope.3.kind=class
scope.3.startLine=184
scope.3.endLine=200
scope.3.semanticHash=dadb510103c340be278fe60e5c0d4f7e8e209054e008db13c17016156c268a5d
scope.4.id=ZmllbGQ6SmF2YU1ldGhvZFBhcnNlci5Db21wbGV4aXR5Q291bnRlciNjb21wbGV4aXR5OjExNA
scope.4.kind=field
scope.4.startLine=114
scope.4.endLine=114
scope.4.semanticHash=18ca06eabf338ade3ab97a617c98059ad90e5386133273b1581d6b783e62b7ec
scope.5.id=ZmllbGQ6SmF2YU1ldGhvZFBhcnNlci5NZXRob2RTY2FubmVyI21ldGhvZHM6ODM
scope.5.kind=field
scope.5.startLine=83
scope.5.endLine=83
scope.5.semanticHash=a8852d9d0a2c75bc2767eea7bff8aa5314f9ea024d0126bb78f6366c0ab881e6
scope.6.id=ZmllbGQ6SmF2YU1ldGhvZFBhcnNlci5NZXRob2RTY2FubmVyI3Bvc2l0aW9uczo4Mg
scope.6.kind=field
scope.6.startLine=82
scope.6.endLine=82
scope.6.semanticHash=11d24a035a0249c15f1eba3cc289abe3b810f802f070c94bbbf22647f2926958
scope.7.id=ZmllbGQ6SmF2YU1ldGhvZFBhcnNlci5NZXRob2RTY2FubmVyI3VuaXQ6ODE
scope.7.kind=field
scope.7.startLine=81
scope.7.endLine=81
scope.7.semanticHash=cdd8f46db86b41140edba9a893fe20cf64ab033feea2435ffd078df74d8abc74
scope.8.id=ZmllbGQ6SmF2YU1ldGhvZFBhcnNlci5Tb3VyY2VGaWxlT2JqZWN0I3NvdXJjZToxODU
scope.8.kind=field
scope.8.startLine=185
scope.8.endLine=185
scope.8.semanticHash=97d0b5d76eb96c0b49baebabd2d8302b3f93dd5e9b34c3bf876e0156e47dafb5
scope.9.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIjY29sbGVjdE1ldGhvZHMoMik6Njk
scope.9.kind=method
scope.9.startLine=69
scope.9.endLine=78
scope.9.semanticHash=b64ff25353d8b2c5581c7104a09dae1b94d580e5996449e4536e5864b4eb7b94
scope.10.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIjY3RvcigwKTozMw
scope.10.kind=method
scope.10.startLine=33
scope.10.endLine=34
scope.10.semanticHash=0658575a3eeef68782d16a62516566aaa3fe3c90b33f73e13d5f86a3ffc0bbf9
scope.11.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIjcGFyc2UoMik6MzY
scope.11.kind=method
scope.11.startLine=36
scope.11.endLine=56
scope.11.semanticHash=3e8cff6d9e86583140b4e9fafa5dc3dfea882c61926047e0d3304ac1060bfa4e
scope.12.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIjc291cmNlUGF0aCgxKTo1OA
scope.12.kind=method
scope.12.startLine=58
scope.12.endLine=63
scope.12.semanticHash=f5db6a2f4bc5d497203bb6f49102cf10f92e7c950f5ab57c094e41dd18a2cc63
scope.13.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIjc291cmNlVXJpKDEpOjY1
scope.13.kind=method
scope.13.startLine=65
scope.13.endLine=67
scope.13.semanticHash=d7be384ad2cce9c59973c1a0bea112d18230fa5bb6d7fffd1f4afd99ff68cbd0
scope.14.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjY291bnQoMSk6MTE2
scope.14.kind=method
scope.14.startLine=116
scope.14.endLine=120
scope.14.semanticHash=82dbabb06f257ec9984e442129571183b9bbe4dbfb902688e5d12e9a2fd9f848
scope.15.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjY3RvcigwKToxMTM
scope.15.kind=method
scope.15.startLine=1
scope.15.endLine=201
scope.15.semanticHash=7a7890b718377b500410628980221377ef77c74aacdddd4967b80a395c8dc5ea
scope.16.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjdmlzaXRCaW5hcnkoMik6MTc1
scope.16.kind=method
scope.16.startLine=175
scope.16.endLine=181
scope.16.semanticHash=3aa334a63397ffc2b4180dc69672f8c290da006aebcfc4fae250887e1a245e01
scope.17.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjdmlzaXRDYXNlKDIpOjE2OQ
scope.17.kind=method
scope.17.startLine=169
scope.17.endLine=173
scope.17.semanticHash=80a0ce054be49df2164d08fa6b401cac5396c5b8d43048243da313bc3841db6a
scope.18.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjdmlzaXRDYXRjaCgyKToxNTc
scope.18.kind=method
scope.18.startLine=157
scope.18.endLine=161
scope.18.semanticHash=d4ce681a7e334a2579c066458ee3670ff733e9a9793b1faff063cb83e9f06999
scope.19.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjdmlzaXRDbGFzcygyKToxMjI
scope.19.kind=method
scope.19.startLine=122
scope.19.endLine=125
scope.19.semanticHash=1cb70adaf6b3db790116f4b7f70ac84b972ca1771cb616ff4dcc64c0d05cd62c
scope.20.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjdmlzaXRDb25kaXRpb25hbEV4cHJlc3Npb24oMik6MTYz
scope.20.kind=method
scope.20.startLine=163
scope.20.endLine=167
scope.20.semanticHash=b66e0e50dd8edb682d855aadbafe227d28fd2b8f68b518649767366a1fbba6e1
scope.21.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjdmlzaXREb1doaWxlTG9vcCgyKToxNTE
scope.21.kind=method
scope.21.startLine=151
scope.21.endLine=155
scope.21.semanticHash=4bd673c7f67707366449b4d6051f42a660a36b662f092105fca5436c0b81ccfc
scope.22.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjdmlzaXRFbmhhbmNlZEZvckxvb3AoMik6MTM5
scope.22.kind=method
scope.22.startLine=139
scope.22.endLine=143
scope.22.semanticHash=f2dc7fc12e39c4b263dfe692754bcd0f3c036845635d39996342b57d168cf37b
scope.23.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjdmlzaXRGb3JMb29wKDIpOjEzMw
scope.23.kind=method
scope.23.startLine=133
scope.23.endLine=137
scope.23.semanticHash=c7779d692a4137fc764022e4d537434b6bd8893a34a52a4a5a07324655689ccc
scope.24.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjdmlzaXRJZigyKToxMjc
scope.24.kind=method
scope.24.startLine=127
scope.24.endLine=131
scope.24.semanticHash=3254fbf20e8e5d2e41f473321ab49459d4fab4a74e392f3d30f787632d65db73
scope.25.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuQ29tcGxleGl0eUNvdW50ZXIjdmlzaXRXaGlsZUxvb3AoMik6MTQ1
scope.25.kind=method
scope.25.startLine=145
scope.25.endLine=149
scope.25.semanticHash=07c7422d7f0c9967680800641b836698b74c397f62c6eae08b009ba074d6d764
scope.26.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuTWV0aG9kU2Nhbm5lciNjdG9yKDMpOjg1
scope.26.kind=method
scope.26.startLine=85
scope.26.endLine=91
scope.26.semanticHash=db5b8cdacc76177a9ae583fdf6036ba254a258d6a79ce33179d6cfe48afc5906
scope.27.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuTWV0aG9kU2Nhbm5lciNsaW5lTnVtYmVyKDEpOjEwOA
scope.27.kind=method
scope.27.startLine=108
scope.27.endLine=110
scope.27.semanticHash=f30ccd8ff3ff335fad27d2f85d07fb0bf1ee5ede4ee4164d2c0e8f7231249ee0
scope.28.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuTWV0aG9kU2Nhbm5lciN2aXNpdE1ldGhvZCgyKTo5Mw
scope.28.kind=method
scope.28.startLine=93
scope.28.endLine=106
scope.28.semanticHash=bbe49d37a7c8c06d62b347168fb6ad979edc2ca4a5c4816ec1721a4f8e3f7c7b
scope.29.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuU291cmNlRmlsZU9iamVjdCNjdG9yKDIpOjE4Nw
scope.29.kind=method
scope.29.startLine=187
scope.29.endLine=190
scope.29.semanticHash=c8f775e9f1bb24f841231efef7e215250d9337f8d4e5985a682fedb3830d6f50
scope.30.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuU291cmNlRmlsZU9iamVjdCNnZXRDaGFyQ29udGVudCgxKToxOTI
scope.30.kind=method
scope.30.startLine=192
scope.30.endLine=195
scope.30.semanticHash=d568c345f5828ec3f1f3bbb9b0dec63e452559441e187fac047170903e271f69
scope.31.id=bWV0aG9kOkphdmFNZXRob2RQYXJzZXIuU291cmNlRmlsZU9iamVjdCN1cmlGb3IoMSk6MTk3
scope.31.kind=method
scope.31.startLine=197
scope.31.endLine=199
scope.31.semanticHash=db07fb454026dcad04b66420dd4817c60bf6b74e63f2b77521d7be08281c128d
*/
