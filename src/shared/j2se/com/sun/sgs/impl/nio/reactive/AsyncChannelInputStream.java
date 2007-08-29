/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio.reactive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import com.sun.sgs.nio.channels.AsynchronousByteChannel;

/*
 * Copied from java.nio.Channels -JM
 */

public class AsyncChannelInputStream extends InputStream {

    protected final AsynchronousByteChannel ch;

    private ByteBuffer bb = null;
    private byte[] bs = null;       // Invoker's previous array
    private byte[] b1 = null;

    public AsyncChannelInputStream(AsynchronousByteChannel ch) {
        this.ch = ch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized int read() throws IOException {
       if (b1 == null)
            b1 = new byte[1];
       if (this.read(b1) == -1)
           return -1;
       return b1[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {

        // This is *write()* code, but it is a start...

    /*
        if ((off < 0) || (off > bs.length) || (len < 0) ||
                ((off + len) > bs.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        ByteBuffer bb = ((this.bs == bs)
                         ? this.bb
                         : ByteBuffer.wrap(bs));
        bb.limit(Math.min(off + len, bb.capacity()));
        bb.position(off);
        this.bb = bb;
        this.bs = bs;
        try {
            // TODO completion handler that writes whole buffer
            ch.write(bb, null).get();
        } catch (InterruptedException e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw (IOException) new IOException(e.getMessage()).initCause(e);
            }
        }
        */
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        ch.close();
    }
}
