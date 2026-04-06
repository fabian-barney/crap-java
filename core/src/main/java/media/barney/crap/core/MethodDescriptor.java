package media.barney.crap.core;

record MethodDescriptor(
        String className,
        String name,
        int startLine,
        int endLine,
        int complexity
) {
}

