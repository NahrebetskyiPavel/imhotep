package com.indeed.imhotep.connection;

import javax.annotation.WillNotClose;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author xweng
 */
class NonClosingOutputStream extends OutputStream {
    private boolean closed;
    private final OutputStream out;

    public NonClosingOutputStream(@WillNotClose final OutputStream out) {
        this.out = out;
        this.closed = false;
    }

    @Override
    public void write(final int b) throws IOException {
        ensureOpen();
        out.write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        ensureOpen();
        out.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        ensureOpen();
        out.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        out.flush();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        out.flush();
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("The stream has been closed, no longer allowed to write to the underlying stream");
        }
    }
}
