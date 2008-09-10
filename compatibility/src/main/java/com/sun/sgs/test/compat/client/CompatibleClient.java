package com.sun.sgs.test.compat.client;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.Properties;

/**
 * A client for {@code CompatibleApp} to use for checking compatibility across
 * releases.
 */
public class CompatibleClient
    implements SimpleClientListener, ClientChannelListener
{
    /** The application host. */
    private static final String host = System.getProperty("host", "localhost");

    /** The application port. */
    private static final int port = Integer.getInteger("port", 12321);

    /** The client. */
    private final SimpleClient client;

    /** Whether the server's checks are completed. */
    private boolean checksCompleted;

    /** Whether the server's checks passed. */
    private boolean checksPassed;

    /** Whether disconnected has been called. */
    private boolean disconnected;

    /** Runs the client twice, logging out the first time. */
    public static void main(String[] args) throws IOException {
	CompatibleClient client = new CompatibleClient();
	client.awaitChecks();
	if (!client.checksPassed) {
	    System.exit(1);
	}
	client.logout(true);
	client.awaitDisconnected();
	client = new CompatibleClient();
	client.awaitChecks();
	if (!client.checksPassed) {
	    System.exit(1);
	}
    }

    /**
     * Creates a client that logs out after receiving messages, if
     * requested.
     */
    public CompatibleClient() throws IOException {
	Properties props = new Properties();
	props.setProperty("host", host);
	props.setProperty("port", String.valueOf(port));
	client = new SimpleClient(this);
	client.login(props);
    }

    /** Logs out. */
    void logout(boolean graceful) {
	client.logout(graceful);
    }

    /** Returns after disconnected has been called. */
    synchronized void awaitDisconnected() {
	while (!disconnected) {
	    try {
		wait();
	    } catch (InterruptedException e) {
	    }
	}
    }

    /** Returns after receiving session message that checks are completed. */
    synchronized void awaitChecks() {
	while (!checksCompleted) {
	    try {
		wait();
	    } catch (InterruptedException e) {
	    }
	}
    }

    /** Converts a byte buffer into a string using UTF-8 encoding. */
    static String bufferToString(ByteBuffer buffer) {
	byte[] bytes = new byte[buffer.remaining()];
	buffer.get(bytes);
	try {
	    return new String(bytes, "UTF-8");
	} catch (UnsupportedEncodingException e) {
	    throw new AssertionError(e);
	}
    }

    /** Converts a string into a byte buffer using UTF-8 encoding. */
    static ByteBuffer stringToBuffer(String string) {
	try {
	    return ByteBuffer.wrap(string.getBytes("UTF-8"));
	} catch (UnsupportedEncodingException e) {
	    throw new AssertionError(e);
	}
    }

    /* -- Implement SimpleClientListener -- */

    public PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication("User1", new char[0]);
    }

    public void loggedIn() {
	System.out.println("Connected: " + client.isConnected());
	try {
	    client.send(stringToBuffer("Run checks"));
	} catch (IOException e) {
	    System.err.println("Sending session message failed: " + e);
	}
    }

    public void loginFailed(String reason) {
        System.out.println("Login failed: " + reason);
    }

    /* -- Implement ServerSessionListener -- */

    public void receivedMessage(ByteBuffer message) {
	String messageString = bufferToString(message);
	System.out.println("Received session message: " + messageString);
	String completedMessage = "Checks completed, failures: ";
	if (messageString.startsWith(completedMessage)) {
	    int failures = Integer.parseInt(
		messageString.substring(completedMessage.length()));
	    synchronized (this) {
		checksCompleted = true;
		checksPassed = (failures == 0);
		notifyAll();
	    }
	}
    }

    public void reconnecting() {
	System.out.println("Reconnecting");
    }

    public void reconnected() {
	System.out.println("Reconnected");
    }

    /** {@inheritDoc} */
    public synchronized void disconnected(boolean graceful, String reason) {
	System.out.println("Disconnected graceful:" + graceful +
			   ", reason:" + reason);
	disconnected = true;
	notifyAll();
    }

    /** {@inheritDoc} */
    public ClientChannelListener joinedChannel(ClientChannel channel) {
	System.out.println("Joined channel " + channel.getName());
	try {
	    channel.send(
		stringToBuffer(
		    "Client message on channel " + channel.getName()));
	} catch (IOException e) {
	    System.err.println("Sending channel message failed: " + e);
	}
        return this;
    }
    
    /* -- Implement ClientChannelListener -- */
    
    /** {@inheritDoc} */
    public void leftChannel(ClientChannel channel) {
	System.out.println("Left channel " + channel.getName());
    }
    
    /** {@inheritDoc} */
    public void receivedMessage(ClientChannel channel, ByteBuffer message) {
	System.out.println("Received message on channel " +
			   channel.getName() + ": " + bufferToString(message));
    }
}
