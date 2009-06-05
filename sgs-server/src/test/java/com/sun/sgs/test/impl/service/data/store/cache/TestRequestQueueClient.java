/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.test.impl.service.data.store.cache;

import com.sun.sgs.impl.service.data.store.cache.RequestQueueClient;
import static com.sun.sgs.impl.service.data.store.cache.RequestQueueClient.
    MAX_RETRY_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.RequestQueueClient.
    RETRY_WAIT_PROPERTY;
import com.sun.sgs.impl.service.data.store.cache.RequestQueueClient.
    BasicSocketFactory;
import com.sun.sgs.impl.service.data.store.cache.RequestQueueClient.
    SocketFactory;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link RequestQueueClient}. */
@RunWith(FilteredNameRunner.class)
public class TestRequestQueueClient extends BasicRequestQueueTest {

    /** The server socket port. */
    private static final int PORT = 30002;

    /** The shorter maximum retry to use for tests. */
    private static final long MAX_RETRY = 100;

    /** The shorter retry wait to use for tests. */
    private static final long RETRY_WAIT = 10;

    /** Properties specifying the shorter maximum retry and retry waits. */
    private static final Properties props = new Properties();
    static {
	props.setProperty(MAX_RETRY_PROPERTY, String.valueOf(MAX_RETRY));
	props.setProperty(RETRY_WAIT_PROPERTY, String.valueOf(RETRY_WAIT));
    }

    /** The request queue listener. */
    private static SimpleRequestQueueListener listener;

    /** A basic socket factory for connecting to the server. */
    private static final SocketFactory socketFactory =
	new BasicSocketFactory("localhost", PORT);

    /** The request queue client or {@code null}. */
    private RequestQueueClient client;

    @Before
    public void beforeTest() throws IOException {
	if (listener == null) {
	    listener = new SimpleRequestQueueListener(
		new ServerSocket(PORT), noopRunnable, emptyProperties);
	}
    }

    @After
    public void afterTest() {
	if (client != null) {
	    client.shutdown();
	    client = null;
	}
    }

    @AfterClass
    public static void afterClass() {
	if (listener != null) {
	    listener.shutdown();
	}
    }

    /* -- Tests -- */

    /* Test constructor */

    @Test(expected=NullPointerException.class)
    public void testConstructorNullSocketFactory() {
	new RequestQueueClient(1, null, noopRunnable, emptyProperties);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullFailureHandler() {
	new RequestQueueClient(1, socketFactory, null, emptyProperties);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullProperties() {
	new RequestQueueClient(1, socketFactory, noopRunnable, null);
    }

    @Test
    public void testConstructorCannotConnect() throws Exception {
	listener.shutdown();
	listener = null;
	NoteRun failureHandler = new NoteRun();
	client = new RequestQueueClient(
	    1, socketFactory, failureHandler, props);
	client.start();
	failureHandler.checkRun(MAX_RETRY);	
    }

    /* Test addRequest */

    @Test
    public void testAddRequestNullRequest() {
	client = new RequestQueueClient(
	    1, socketFactory, noopRunnable, emptyProperties);
	try {
	    client.addRequest(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    @Test
    public void testAddRequestShutdown() {
	client = new RequestQueueClient(
	    1, socketFactory, noopRunnable, emptyProperties);
	client.shutdown();
	try {
	    client.addRequest(null);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }
}
