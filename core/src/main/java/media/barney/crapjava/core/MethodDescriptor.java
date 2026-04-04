package media.barney.crapjava.core;

record MethodDescriptor(
        String className,
        String name,
        int startLine,
        int endLine,
        int complexity
) {
}
