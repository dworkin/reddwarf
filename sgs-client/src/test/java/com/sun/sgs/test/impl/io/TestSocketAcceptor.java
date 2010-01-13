/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * --
 */

package com.sun.sgs.test.impl.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.sun.sgs.impl.io.ServerSocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.io.Acceptor;

/**
 * JUnit test class for the SocketAcceptor. 
 * 
 */
public class TestSocketAcceptor
    extends TestCase
{
    private final static int BIND_PORT = 0;
    
    Acceptor<SocketAddress> acceptor;

    @Override
    public void setUp() {
        acceptor = new ServerSocketEndpoint(
                new InetSocketAddress(BIND_PORT),
               TransportType.RELIABLE).createAcceptor();
    }
    
    @Override
    public void tearDown() {
        if (acceptor != null) {
            acceptor.shutdown();
            acceptor = null;
        }
    }
    
    /**
     * Test listening on one port.
     *
     * @throws IOException if an unexpected IO problem occurs
     */
    public void testListen() throws IOException {
        acceptor.listen(null);
    }
    
    /**
     * Test that verifies the Acceptor won't listen on any port after 
     * shutdown.
     *
     * @throws IOException if an unexpected IO problem occurs
     */
    public void testListenAfterShutdown() throws IOException {
        acceptor.listen(null);
        acceptor.shutdown();
        try {
            acceptor.listen(null);
        } catch (IllegalStateException expected) {
            // passed
            return;
        }
        Assert.fail("Expected IllegalStateException");
    }

}
