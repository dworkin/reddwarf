package com.sun.gi.apps.battleboard.client;

import com.sun.gi.apps.battleboard.BattleBoard;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import javax.security.auth.callback.*;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import com.sun.gi.utils.types.BYTEARRAY;
import com.sun.gi.utils.types.StringUtils;


public class BattleBoardClient implements ClientConnectionManagerListener {

    private static Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.client");

    protected BattleBoard board;

    protected ClientConnectionManager mgr;
    protected ClientChannel gameChannel;

    protected BufferedReader reader;
    protected Callback[] validationCallbacks = null;

    public BattleBoardClient() {
	reader = new BufferedReader(new InputStreamReader(System.in));
    }

    public void run() {
	try {
	    mgr = new ClientConnectionManagerImpl("BattleBoard",
		      new URLDiscoverer(
			  new File("FakeDiscovery.xml").toURI() .toURL()));
	    mgr.setListener(this);

	    String[] classNames = mgr.getUserManagerClassNames();

	    mgr.connect(classNames[0]);

	} catch (Exception e) {
	    e.printStackTrace();
	    return;
	}
    }

/*
    protected void doServerMessage(String message) {
	ByteBuffer out = ByteBuffer.allocate(message.length());
	out.put(message.getBytes());
	mgr.sendToServer(out,true);

    }

    protected void doMultiDCCMessage(byte[][] targetList, String message) {
	ByteBuffer out = ByteBuffer.allocate(message.length());
	out.put(message.getBytes());
	dccChannel.sendMulticastData(targetList, out, true);

    }

    protected void doDCCMessage(byte[] target, String message) {
	ByteBuffer out = ByteBuffer.allocate(message.length());
	out.put(message.getBytes());
	dccChannel.sendUnicastData(target, out, true);

    }
*/

    protected void doPrompt(String prompt) {
	System.out.print(prompt + ": ");
	System.out.flush();
    }
 
    public void visitNameCallback(NameCallback cb) {
	log.finer("visitNameCallback");
	try {
	    doPrompt(cb.getPrompt());
	    String line = reader.readLine();
	    if ((line == null || line.isEmpty())) {
		return;
	    }
	    cb.setName(line);
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public void visitPasswordCallback(PasswordCallback cb) {
	log.finer("visitPasswordCallback");
	try {
	    doPrompt(cb.getPrompt());
	    String line = reader.readLine();
	    if ((line == null || line.isEmpty())) {
		return;
	    }
	    cb.setPassword(line.toCharArray());
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    // ClientConnectionManagerListener methods

    public void validationRequest(Callback[] callbacks) {
	log.info("validationRequest");

	for (Callback cb : callbacks) {
	    try {
		if (cb instanceof NameCallback) {
		    visitNameCallback((NameCallback) cb);
		} else if (cb instanceof PasswordCallback) {
		    visitPasswordCallback((PasswordCallback) cb);
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    } 
	}

	mgr.sendValidationResponse(callbacks);
    }

    public void connected(byte[] myID) {
	log.info("connected");
    }

    public void connectionRefused(String message) {
	log.info("connectionRefused");
    }

    public void disconnected() {
	log.info("disconnected");
    }

    public void userJoined(byte[] userID) {
	log.info("userJoined");
    }

    public void userLeft(byte[] userID) {
	log.info("userLeft");
    }

    public void joinedChannel(final ClientChannel channel) {
	log.info("joinedChannel " + channel.getName());

	channel.setListener(new ClientChannelListener() {

	    public void playerJoined(byte[] playerID) {
		log.info("playerJoined on " + channel.getName());
	    }

	    public void playerLeft(byte[] playerID) {
		log.info("playerJoined on " + channel.getName());
	    }

	    public void dataArrived(byte[] uid, ByteBuffer data,
		    boolean reliable) {
		log.info("dataArrived on " + channel.getName());

		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);
	    }

	    public void channelClosed() {
		log.info("channel " + channel.getName() + " closed");
	    }
	});
    }

    public void failOverInProgress() {
	log.info("failOverInProgress");
    }

    public void reconnected() {
	log.info("reconnected");
    }

    public void channelLocked(String chan, byte[] userID) {
	log.warning("Channel `" + chan + "' is locked");
    }

    // main()

    public static void main(String[] args) {
	new BattleBoardClient().run();
    }

}
