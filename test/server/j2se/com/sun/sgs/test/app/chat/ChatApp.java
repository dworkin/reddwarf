package com.sun.sgs.test.app.chat;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Properties;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ShutdownListener;

public class ChatApp
	implements ManagedObject, Serializable, AppListener, ChannelListener
{
    private static final long serialVersionUID = 1L;

    public void startingUp(Properties props) {
        System.err.format("ChatApp: Starting up\n");

        AppContext.getChannelManager().createChannel(
        	"echo", this, Delivery.ORDERED_UNRELIABLE);
    }

    public void loggedIn(final ClientSession session) {
        System.err.format("ChatApp: ClientSession [%s] joined, named \"%s\"\n",
        	session.toString(), session.getName());
        session.setListener(new ChatClientSessionListener(this, session));
    }

    public void receivedMessage(Channel channel, ClientSession sender, ByteBuffer message) {
	byte[] messageBytes = new byte[message.remaining()];
	message.get(messageBytes);
	String messageString = new String(messageBytes);
	System.err.format("ChatApp: Echoing to \"%s\": [%s]\n",
		sender.getName(), messageString);
        channel.send(sender, message);
    }

    public void shuttingDown(ShutdownListener listener, boolean force) {
	System.err.format("ChatApp: Shutting down, force = %b\n", force);
	listener.shutdownComplete();
    }

    static class ChatClientSessionListener
    	    implements ClientSessionListener, Serializable
    {
	private static final long serialVersionUID = 1L;
	private final ChatApp app;
	private final ClientSession session;

	public ChatClientSessionListener (ChatApp app, ClientSession session) {
	    this.app = app;
	    this.session = session;
	}

	public void disconnected(boolean graceful) {
	    System.err.format("ChatApp: ClientSession [%s] disconnected, " +
		    "graceful = %b\n", session.toString(), graceful);
	}

	public void receivedMessage(ByteBuffer message) {
	    byte[] messageBytes = new byte[message.remaining()];
	    message.get(messageBytes);
	    String command = new String(messageBytes);
	    System.err.format("ChatApp: Command from \"%s\": [%s]\n",
		    session.getName(), command);

	    ChannelManager channelMgr = AppContext.getChannelManager();
	    if (command.startsWith("/join ")) {
		String channelName = command.substring(6);
		Channel channel;
		try {
		    channel = channelMgr.createChannel(channelName, app, Delivery.RELIABLE);
		} catch (NameNotBoundException nnbe) {
		    channel = channelMgr.getChannel(channelName);
		}
		System.err.format("ChatApp: Joining \"%s\" to channel %s\n",
			session.getName(), channel.getName());
		channel.join(session);
	    } else if (command.startsWith("/leave ")) {
		String channelName = command.substring(7);
		Channel channel = channelMgr.getChannel(channelName);
		System.err.format("ChatApp: Removing \"%s\" from channel %s\n",
			session.getName(), channel.getName());
		channel.leave(session);
	    } else if (command.equals("/exit")) {
		System.err.format("ChatApp: \"%s\" requests exit\n", session.getName());
		session.disconnect();
	    } else if (command.equals("/shutdown")) {
		System.err.format("ChatApp: \"%s\" requests shutdown\n", session.getName());
		// TODO: app.halt();
	    } else {
		System.err.format("ChatApp: Error; \"%s\" sent unknown command [%s]\n",
			session.getName(), command);
	    }
	}
    }
}
