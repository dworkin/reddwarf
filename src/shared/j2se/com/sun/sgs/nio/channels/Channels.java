package com.sun.sgs.nio.channels;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Collections;
import java.util.List;

public final class Channels {

    private Channels() { }              // No instantiation

    /** Constructs a stream that reads bytes from the given channel. */
    public static InputStream newInputStream(AsynchronousByteChannel ch) {
        // TODO
        throw new UnsupportedOperationException();
    }

    /** Constructs a stream that writes bytes to the given channel. */
    public static OutputStream newOutputStream(final AsynchronousByteChannel ch)
    {
        // TODO
        throw new UnsupportedOperationException();
    }

    /** Returns a list of the ChannelPoolMXBean objects in the Java virtual machine. */
    public static List<ChannelPoolMXBean> getChannelPoolMXBeans() {
        // TODO
        return Collections.emptyList();
    }

    public static ReadableByteChannel newChannel(InputStream in) {
        return java.nio.channels.Channels.newChannel(in);
    }

    public static WritableByteChannel newChannel(OutputStream out) {
        return java.nio.channels.Channels.newChannel(out);
    }

    public static InputStream newInputStream(ReadableByteChannel ch) {
        return java.nio.channels.Channels.newInputStream(ch);
    }

    public static OutputStream newOutputStream(WritableByteChannel ch) {
        return java.nio.channels.Channels.newOutputStream(ch);
    }
 
    public static Reader newReader(ReadableByteChannel ch, CharsetDecoder dec, int minBufferCap) {
        return java.nio.channels.Channels.newReader(ch, dec, minBufferCap);
    }

    public static Reader newReader(ReadableByteChannel ch, String csName) {
        return java.nio.channels.Channels.newReader(ch, csName);
    }
 
    public static Writer newWriter(WritableByteChannel ch, CharsetEncoder enc, int minBufferCap) {
        return java.nio.channels.Channels.newWriter(ch, enc, minBufferCap);
    }

    public static Writer newWriter(WritableByteChannel ch, String csName) {
        return java.nio.channels.Channels.newWriter(ch, csName);
    }
}
