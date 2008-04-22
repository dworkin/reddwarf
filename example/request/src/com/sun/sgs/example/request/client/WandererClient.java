/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.example.request.client;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Defines a client that uses the {@code RequestApp} application to simulate a
 * game with wandering players. <p>
 *
 * Each {@code WandererClient} steps randomly around a board, sending messages
 * to the channel associated with its local neighborhood, and reading and
 * writing values associated with its current location.  The resulting
 * application serves as a stress test for the channel and data services,
 * performing operations that each include a channel send, and twice as many
 * data service reads as writes. <p>
 *
 * Clients can handle disconnects, and the {@link #main main} method arranges
 * to run multiple clients. <p>
 *
 * This class supports the following properties:
 * <ul>
 * <li> {@code com.sun.sgs.example.request.client.wanderer.host} - The
 *	application host name, defaults to {@code localhost}
 * <li> {@code com.sun.sgs.example.request.client.wanderer.port} - The
 *	application port; defaults to {@code 11469}, the default port for the
 *	{@code RequestApp} application
 * <li> {@code com.sun.sgs.example.request.client.wanderer.clients} - The
 *	number of clients run by {@code main}, defaults to {@code 10}
 * <li> {@code com.sun.sgs.example.request.client.wanderer.backlog} - The
 *	maximum message backlog per client before the client throttles, or
 *	{@code -1} to do no throttling.
 * <li> {@code com.sun.sgs.example.request.client.wanderer.sleep} - The number
 *	of milliseconds to wait between steps, defaults to {@code 200}
 * <li> {@code com.sun.sgs.example.request.client.wanderer.size} - The width
 *	and height of the board, defaults to {@code 1000}
 * <li> {@code com.sun.sgs.example.request.client.wanderer.sector} - The size
 *	of the neighborhood to assign to a single channel for movement
 *	notifications, defaults to {@code 100}
 * <li> {@code com.sun.sgs.example.request.client.wanderer.report} - The number
 *	of seconds between logging performance data, defaults to {@code 20}
 * </ul> <p>
 *
 * This class uses the {@link Logger} named {@code
 * com.sun.sgs.example.request.client.wanderer} to log at the following levels:
 * <ul>
 * <li> {@link Level#INFO Level.INFO} - Initialization, performance data
 * <li> {@link Level#FINE Level.FINE} - Login, disconnect, reconnection
 * <li> {@link Level#FINER Level.FINER} - Send and receive messages
 * <li> {@link Level#FINEST Level.FINEST} - Exceptions
 * </ul>
 */
public class WandererClient 
        implements Runnable, SimpleClientListener, ClientChannelListener
{

    /** The prefix for properties. */
    private static final String PREFIX =
	"com.sun.sgs.example.request.client.wanderer";

    /** The application host name. */
    private static final String HOST =
	System.getProperty(PREFIX + ".host", "localhost");

    /** The application port. */
    private static final int PORT =
	Integer.getInteger(PREFIX + ".port", 11469);

    /** The number of clients run by main. */
    private static final int CLIENTS =
	Integer.getInteger(PREFIX + ".clients", 10);

    /**
     * The maximum message backlog per client before the client throttles, or
     * -1 to do no throttling.
     */
    private static final int BACKLOG =
	Integer.getInteger(PREFIX + ".backlog", 10);

    /** The number of milliseconds to sleep between steps. */
    private static final int SLEEP =
	Integer.getInteger(PREFIX + ".sleep", 200);

    /** The number of milliseconds to perturb waits, to avoid storms. */
    private static final int RANDOM =
	Integer.getInteger(PREFIX + ".random", 50);

    /** The width and height of the board. */
    private static final int SIZE =
	Integer.getInteger(PREFIX + ".size", 1000);

    /**
     * The size of the neighborhood to assign to a single channel for movement
     * notifications.
     */
    private static final int SECTOR =
	Integer.getInteger(PREFIX + ".sector", 100);

    /** The number of seconds between logging performance data. */
    private static final int REPORT =
	Integer.getInteger(PREFIX + ".report", 20);

    /**
     * The minimum number of milliseconds to wait for a login attempt to
     * succeed.
     */
    private static final long LOGIN_MIN_RETRY =
	Long.getLong(PREFIX + ".login.min.retry", 5000);

    /**
     * The maximum number of milliseconds to wait for a login attempt to
     * succeed.
     */
    private static final long LOGIN_MAX_RETRY =
	Long.getLong(PREFIX + ".login.max.retry", 30000);

    /** The logger for this class. */
    private static final Logger logger = Logger.getLogger(PREFIX);

    /** A random number generator used to random behavior. */
    private static final Random random = new Random();

    /** The client used to communicate with the server. */
    private final SimpleClient simpleClient;

    /** The login properties. */
    private final Properties props;

    /** The name of the user. */
    private final String user = "User-" + random.nextInt(Integer.MAX_VALUE);

    /** The current X location. */
    private int x = random.nextInt(SIZE);

    /** The current Y location. */
    private int y = random.nextInt(SIZE);

    /** The number of messages sent. */
    private int sent = 0;

    /** The number of messages received. */
    private int received = 0;

    /**
     * True if this client is logged in and sending messages without getting
     * exceptions.
     */
    private boolean active = false;

    /** True if this client is logged in but is getting exceptions. */
    private boolean failing = false;

    /** True if this client is not logged in. */
    private boolean disconnected = true;

    /** True if this client logged in. */
    private boolean login = false;

    /**
     * The number of GetItem messages that have been sent and for which replies
     * have not been received.
     */
    private int backlog = 0;

    /**
     * The number of times that the client has throttled itself because the
     * backlog has gotten too large.
     */
    private int throttled = 0;

    /**
     * The name of the current sector or null if not a member of a sector
     * channel yet.
     */
    private String sector = null;

    /**
     * Starts up clients.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
	if (logger.isLoggable(Level.INFO)) {
	    logger.log(Level.INFO,
		       "Creating WandererClients:" +
		       "\n  host: " + HOST +
		       "\n  port: " + PORT +
		       "\n  clients: " + CLIENTS +
		       "\n  backlog: " + BACKLOG +
		       "\n  sleep: " + (SLEEP - RANDOM) + "-" +
		       (SLEEP + RANDOM) + " ms" +
		       "\n  size: " + SIZE +
		       "\n  sector: " + SECTOR +
		       "\n  report interval: " + REPORT + " sec");
	}
	WandererClient[] clients = new WandererClient[CLIENTS];
	for (int i = 0; i < CLIENTS; i++) {
	    clients[i] = new WandererClient();
	}
	long until = System.currentTimeMillis() + (REPORT * 1000);
	/* Round to a multiple of the report interval */
	until -= until % (REPORT * 1000);
	while (true) {
	    long now = System.currentTimeMillis();
	    if (now < until) {
		try {
		    Thread.sleep(until - now);
		} catch (InterruptedException e) {
		}
		continue;
	    }
	    Stats stats = new Stats();
	    for (WandererClient client : clients) {
		client.tally(stats);
	    }
	    if (logger.isLoggable(Level.INFO)) {
		logger.log(Level.INFO, stats.report());
	    }
	    until += (REPORT * 1000);
	}
    }

    /**
     * Creates an instance, and starts a thread to login and perform actions.
     */
    public WandererClient() {
        props = new Properties();
        props.setProperty("host", HOST);
        props.setProperty("port", String.valueOf(PORT));
        simpleClient = new SimpleClient(this);
	new Thread(this, "WandererClient[" + user + "]").start();
    }

    /* -- Implement Runnable -- */

    /** Performs client actions. */
    public void run() {
	for (int i = 0; true; i++) {
	    if (getDisconnected()) {
		login();
	    }
	    try {
		boolean storePosition = (i % 5 == 0);
		if (!storePosition) {
		    synchronized (this) {
			/* Note GetItem requests */
			backlog++;
		    }
		}
		send(move() + "\n" +
		     (storePosition ? storePosition() : getPosition()));
		sleep();
	    } catch (Exception e) {
		noteFailing();
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST, "Exception thrown", e);
		}
	    }
	}
    }
    
    /* -- Implement SimpleClientListener -- */

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns password authentication for the current
     * user.
     */
    public PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(user, new char[0]);
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation notifies the client thread that the client is
     * active.
     */
    public void loggedIn() {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, user + ": Logged in");
        }
        noteActive();
    }

    /**
     * {@inheritDoc}
     */
    public void loginFailed(String reason) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                       user + ": Login failed: " + reason);
        }
	noteFailing();
    }

    /* -- Implement ServerSessionListener -- */

    /** {@inheritDoc} */
    public void receivedMessage(ByteBuffer message) {
        String string = bufferToString(message);
	if (string.startsWith("GetItem ")) {
	    synchronized (this) {
		backlog--;
		if (backlog < (BACKLOG / 2)) {
		    notifyAll();
		}
	    }
	}
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, user + ": Received: " + string);
        }
        noteReceived();
    }

    /** {@inheritDoc} */
    public void reconnecting() {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, user + ": Reconnecting");
        }
    }

    /** {@inheritDoc} */
    public void reconnected() {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, user + ": Reconnected");
        }
    }

    /** {@inheritDoc} */
    public void disconnected(boolean graceful, String reason) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                       user + ": Disconnected graceful:" + graceful +
                       ", reason:" + reason);
        }
        noteDisconnected();
    }

    /** {@inheritDoc} */
    public ClientChannelListener joinedChannel(ClientChannel channel) {
        return this;
    }
    
    /* -- Implement ClientChannelListener -- */
    
    /** {@inheritDoc} */
    public void leftChannel(ClientChannel channel) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, user + ": left channel " + 
                                   channel.getName());
        }
    }
    
    /** {@inheritDoc} */
    public void receivedMessage(ClientChannel channel, ByteBuffer message) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, user + ": Received: " + 
                                    bufferToString(message));
        }
        noteReceived();
    }
    
    
    /* -- Other methods -- */

    /** Performs a login to the current host, waiting as needed. */
    private void login() {
        long retry = LOGIN_MIN_RETRY;
        while (true) {    
            /* Wait randomly, to avoid login storms. */
            try {
                Thread.sleep(random.nextInt(RANDOM));
            } catch (InterruptedException e) {
            }
            
            long start = System.currentTimeMillis();
            
            try {
                simpleClient.login(props);
                long next = start + retry;
                long wait = next - System.currentTimeMillis();
                while (wait > 0 &&
                       getDisconnected())
                {
                    try {
                        synchronized (this) {
                            wait(wait);
                        }
                    } catch (InterruptedException e) {
                    }
                    wait = next - System.currentTimeMillis();
                }
                if (!getDisconnected()) {
                    return;
                }
            } catch (Exception e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, user + ": Login failed: " + e);
                }
            }
            /* Back off by doubling the wait time, up to the maximum. */
            retry = Math.min(retry * 2, LOGIN_MAX_RETRY);
        }

    }

    /**
     * Moves to the next location, and returns a request for sending the
     * location to the correct channel.
     */
    private String move() throws IOException {
	/*
	 * Pick a random number between 1 and 8.  This number will either be
	 * non-zero mod 3, or, when divided by 3, will be non-zero,
	 * representing a change in X, Y, or both, but not leaving both X and Y
	 * the same.
	 */
	int n = 1 + random.nextInt(7);
	int xinc = threeValue(n);
	int yinc = threeValue(n / 3);
	x = (x + xinc) % SIZE;
	y = (y + yinc) % SIZE;
	String request = "";
	String newSector = sectorName();
	if (!newSector.equals(sector)) {
	    if (sector != null) {
		request = "LeaveChannel " + sector + "\n";
	    }
	    request += "JoinChannel " + newSector + "\n";
	    sector = newSector;
	}
	request += "SendChannel " + sector + " " + positionName();
	return request;
    }

    /** Converts n as follows:  0 mod 3 => 0, 1 mod 3 => 1, 2 mod 3 => -1. */
    private static int threeValue(int n) {
	n = n % 3;
	return (n == 2) ? -1 : n;
    }

    /**
     * Returns a request to store this user's name under it's current position,
     * and store the current position under this user's name.
     */
    private String storePosition() throws IOException {
	return "SetItem " + positionName() + " " + user +
	    "\nSetItem " + user + " " + positionName();
    }

    /**
     * Returns a request for obtaining the user name stored at this user's
     * current position.
     */
    private String getPosition() throws IOException, InterruptedException {
	return "GetItem " + positionName();
    }

    /** Returns the name for the current position. */
    private String positionName() {
	return "position-" + x + "-" + y;
    }

    /** Returns the sector name for the current position. */
    private String sectorName() {
	return "sector-" + (x / SECTOR) + "-" + (y / SECTOR);
    }

    /** Sleeps for a random amount of time between moves. */
    private void sleep() throws InterruptedException {
	int n = SLEEP - RANDOM + random.nextInt(2 * RANDOM);
	Thread.sleep(n);
	if (BACKLOG >= 0) {
	    synchronized (this) {
		if (backlog > BACKLOG) {
		    throttled++;
		    do {
			wait();
		    } while (backlog > BACKLOG / 2);
		}
	    }
	}
    }

    /** Sends a message to the server. */
    private void send(String message) throws IOException {
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, user + ": Send: " + message);
	}
	simpleClient.send(stringToBuffer(message));
	noteSent();
    }

    /** Records that a message has been sent. */
    synchronized void noteSent() {
	sent++;
    }

    /** Records that a message has been received. */
    synchronized void noteReceived() {
	received++;
    }

    /** Records that the client is active, and notifies waiters. */
    synchronized void noteActive() {
	active = true;
	failing = false;
	disconnected = false;
	login = true;
	notifyAll();
    }

    /** Records that the client is failing. */
    synchronized void noteFailing() {
	active = false;
	failing = true;
    }

    /** Records that the client is disconnected. */
    synchronized void noteDisconnected() {
	active = false;
	failing = false;
	disconnected = true;
    }

    /** Returns whether the client is disconnected. */
    synchronized boolean getDisconnected() {
	return disconnected;
    }

    /**
     * Updates the argument with statistics from this client, and resets this
     * client's statistics.
     */
    synchronized void tally(Stats stats) {
	stats.sent += sent;
	sent = 0;
	stats.received += received;
	received = 0;
	if (active) { stats.active++; }
	if (failing) { stats.failing++; }
	failing = false;
	if (disconnected) { stats.disconnected++; }
	if (login) { stats.logins++; }
	login = false;
	stats.backlog += backlog;
	stats.throttled += throttled;
	throttled = 0;
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

    /* -- Nested classes -- */

    /** Records client statistics. */
    private static class Stats {

	/** Messages sent. */
	private int sent = 0;

	/** Messages received. */
	private int received = 0;

	/** Clients active. */
	private int active = 0;

	/** Clients receiving exceptions. */
	private int failing = 0;

	/** Clients disconnected and in the processing of connecting. */
	private int disconnected = 0;

	/** Clients that logged in. */
	private int logins = 0;

	/** Number of GetItem messages that have not been acknowledged. */
	private int backlog = 0;

	/** Number of times clients have throttled. */
	private int throttled = 0;

	/** Creates an empty instance. */
	Stats() { }

	/** Returns a string that describes the client statistics. */
	String report() {
	    return "sent/sec=" + (sent / REPORT) +
		" rcv/sec=" + (received / REPORT) +
		" active=" + active +
		(failing > 0 ? " failing=" + failing : "") +
		(disconnected > 0 ? " disconnected=" + disconnected : "") +
		(logins > 0 ? " login=" + logins : "") +
		(backlog > 0 ? " backlog=" + backlog : "") +
		(throttled > 0 ? " throttle=" + throttled : "");
	}
    }
}
