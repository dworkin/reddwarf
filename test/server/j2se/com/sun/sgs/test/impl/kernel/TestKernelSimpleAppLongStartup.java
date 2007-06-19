/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.TransactionTimeoutException;
import static com.sun.sgs.impl.kernel.StandardProperties.CHANNEL_SERVICE;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Properties;

/**
 * Uses a simple end-to-end test of the server run by calling the Kernel to
 * test that unbounded transaction timeouts are applied to service
 * configuration and work properly for the channel service, which uses the data
 * service.
 */
public class TestKernelSimpleAppLongStartup extends TestKernelSimpleApp {

    /** The number of milliseconds to let the kernel run */
    private static final long RUN_PROCESS_MILLIS = 10000;

    /** How long to delay the channel service configure method. */
    private static final long CONFIGURE_DELAY = 2000;

    /** Status values for calls to the channel service configure method. */
    enum Status { NOT_DONE, RETURN, TIMEOUT };

    /** The status of the call to the channel service configure method. */
    private Status delayedConfigureStatus = Status.NOT_DONE;

    /** Run a simple application with long enough configure timeout. */
    public void testRunSimpleApp() throws Exception {
	assertTrue(runWithUnboundedTimeout(3000));
    }

    /** Run a simple application with a short configure timeout. */
    public void testRunSimpleAppShortTimeout() throws Exception {
	assertFalse(runWithUnboundedTimeout(1000));
    }

    /**
     * Run a simple application with the specified unbounded task timeout,
     * returning true if it succeeds, and false if the configure method times
     * out.  Uses the DelayedChannelService as the channel service.
     */
    private boolean runWithUnboundedTimeout(long timeout) throws Exception {
	/* Create the application directory */
	File dir = File.createTempFile("SimpleApp", "dir");
	dir.delete();
	dir.mkdir();
	/* Create the DataStore subdirectory */
	new File(dir, "dsdb").mkdir();
	/* Create the application properties file */
	File props = File.createTempFile("SimpleApp", "properties");
	Writer out = new FileWriter(props);
	out.write(
	    "com.sun.sgs.app.listener =" +
	    " com.sun.sgs.test.impl.kernel.SimpleApp\n" +
	    "com.sun.sgs.app.name = SimpleApp\n" +
	    "com.sun.sgs.app.port = 33334\n" +
	    "com.sun.sgs.app.root = " + dir.toURI().toURL().getPath() + "\n" +
	    CHANNEL_SERVICE + " = " + DelayedChannelService.class.getName());
	out.close();
	/* Create a logging file at WARNING or higher */
	File logging = File.createTempFile("SimpleApp", "logging");
	out = new FileWriter(logging);
	out.write(".level = WARNING\n" +
		  "handlers = java.util.logging.ConsoleHandler\n" +
		  "java.util.logging.ConsoleHandler.formatter =" +
		  " java.util.logging.SimpleFormatter\n" +
		  "java.util.logging.ConsoleHandler.level = WARNING");
	out.close();
	/* Run the Kernel */
	ProcessBuilder pb = new ProcessBuilder(
            System.getProperty("java.home") + "/bin/java",
	    "-cp", System.getProperty("java.class.path"),
	    "-Djava.util.logging.config.file=" + logging,
	    "-Djava.library.path=" + System.getProperty("java.library.path"),
	    "-Dcom.sun.sgs.txn.timeout.unbounded=" + timeout,
	    "com.sun.sgs.impl.kernel.Kernel",
	    props.toURI().toURL().getPath());
	new RunProcess(pb, RUN_PROCESS_MILLIS) {
	    void handleInput(String line) {
		System.out.println("stdout: " + line);
		if (line.equals("DelayedChannelService.configure returned")) {
		    delayedConfigureStatus = Status.RETURN;
		} else if (line.equals(
			       "DelayedChannelService.configure timed out"))
		{
		    delayedConfigureStatus = Status.TIMEOUT;
		    done();
		} else if (line.equals("count=3")) {
		    done();
		}
	    }
	    void handleError(String line) {
		System.err.println("stderr: " + line);
		failed(
		    new RuntimeException(
			"Unexpected error input: " + line));
	    }
	}.run();
	switch (delayedConfigureStatus) {
	case RETURN:
	    return true;
	case TIMEOUT:
	    return false;
	default:
	    fail("The configure method did not return or timeout");
	    return false;
	}
    }

    /**
     * Define a channel service that delegates to the standard one, but delays
     * configuration and prints information about whether configure method
     * returned or timed out.
     */
    public static class DelayedChannelService
	implements ChannelManager, Service
    {
	private final ChannelServiceImpl service;
	public DelayedChannelService(
	    Properties properties, ComponentRegistry systemRegistry)
	{
	    service = new ChannelServiceImpl(properties, systemRegistry);
	}
	public String getName() {
	    return service.getName();
	}
	public void configure(
	    ComponentRegistry registry, TransactionProxy proxy)
	{
	    try {
		Thread.sleep(CONFIGURE_DELAY);
		service.configure(registry, proxy);
		System.out.println("DelayedChannelService.configure returned");
	    } catch (TransactionTimeoutException e) {
		System.out.println(
		    "DelayedChannelService.configure timed out");
		throw e;
	    } catch (InterruptedException e) {
		System.err.println(e);
	    }
	}
	public boolean shutdown() {
	    return service.shutdown();
	}
	public Channel createChannel(
	    String name, ChannelListener listener, Delivery delivery)
	{
	    return service.createChannel(name, listener, delivery);
	}
	public Channel getChannel(String name) {
	    return service.getChannel(name);
	}
    }
}
