/**
 * The I/O package provides the high level abstraction for using I/O services.
 * Clients should use these interfaces, regardless of the implementation that
 * backs them.
 * <p>
 * To use this framework, typically a client would obtain an
 * <code>IOConnector</code> implementation through a 
 * <code>ConnectorFactory</code>, or an <code>IOAcceptor</code> implementation 
 * through an <code>AcceptorFactory</code>.
 * <p>
 * Note that <code>java.nio.ByteBuffer</code>s are used throughout.  At some 
 * point they may want to be replaced by a custom ByteBuffer implementation, 
 * and/or a pooled buffer solution.
 *
 */
package com.sun.sgs.io;