package media.barney.crapjava.gradle;

import org.gradle.api.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

final class GradleLoggingPrintStreams {

    private GradleLoggingPrintStreams() {
    }

    static PrintStream standardOut(Logger logger) {
        return printStream(logger::lifecycle);
    }

    static PrintStream standardErr(Logger logger) {
        return printStream(logger::warn);
    }

    private static PrintStream printStream(Consumer<String> sink) {
        return new PrintStream(new LineLoggingOutputStream(sink), true, StandardCharsets.UTF_8);
    }

    private static final class LineLoggingOutputStream extends OutputStream {
        private final Consumer<String> sink;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private LineLoggingOutputStream(Consumer<String> sink) {
            this.sink = sink;
        }

        @Override
        public void write(int value) {
            if (value == '\n') {
                flushBuffer();
                return;
            }
            if (value != '\r') {
                buffer.write(value);
            }
        }

        @Override
        public void flush() {
            flushBuffer();
        }

        @Override
        public void close() {
            flushBuffer();
        }

        private void flushBuffer() {
            if (buffer.size() == 0) {
                return;
            }
            sink.accept(buffer.toString(StandardCharsets.UTF_8));
            buffer.reset();
        }
    }
}
