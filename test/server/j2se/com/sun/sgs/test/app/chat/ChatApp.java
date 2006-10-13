package com.sun.sgs.test.app.chat;

import java.io.Serializable;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.Session;
import com.sun.sgs.app.SessionListener;
import com.sun.sgs.app.ShutdownListener;

public class ChatApp
	implements ManagedObject, Serializable, AppListener, ChannelListener
{
    private static final long serialVersionUID = 1L;

    public void startingUp() {
        System.err.format("ChatApp: Starting up\n");

        AppContext.getChannelManager().createChannel(
        	"echo", this, Delivery.ORDERED_UNRELIABLE);
    }

    public void loggedIn(final Session session) {
        System.err.format("ChatApp: Session [%s] joined, named \"%s\"\n",
        	session.toString(), session.getName());
        session.setListener(new ChatSessionListener(this, session));
    }

    public void receivedMessage(Channel channel, Session sender, byte[] message) {
	String messageString = new String(message);
	System.err.format("ChatApp: Echoing to \"%s\": [%s]\n",
		sender.getName(), messageString);
        channel.send(sender, message);
    }

    public void shuttingDown(ShutdownListener listener, boolean force) {
	System.err.format("ChatApp: Shutting down, force = %b\n", force);
	listener.shutdownComplete();
    }

    static class ChatSessionListener
    	    implements SessionListener, Serializable
    {
	private static final long serialVersionUID = 1L;
	private final ChatApp app;
	private final Session session;

	public ChatSessionListener (ChatApp app, Session session) {
	    this.app = app;
	    this.session = session;
	}

	public void disconnected(boolean graceful) {
	    System.err.format("ChatApp: Session [%s] disconnected, " +
		    "graceful = %b\n", session.toString(), graceful);
	}

	public void receivedMessage(byte[] message) {
	    String command = new String(message);
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
