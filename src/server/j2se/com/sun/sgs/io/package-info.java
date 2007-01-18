/**
 * Provides a high-level abstraction for SGS I/O functionality.
 * System components should use these interfaces to decouple them from
 * the backing implementation.
 * <p>
 * To use this framework, typically a client would obtain an
 * {@link com.sun.sgs.io.IOConnector} or {@link com.sun.sgs.io.IOAcceptor}
 * implementation through an {@link com.sun.sgs.io.Endpoint}.
 * <p>
 * Note that {@code byte} arrays are used throughout.  At some 
 * point they may be replaced by a custom {@code ByteBuffer} implementation.
 */
package com.sun.sgs.io;