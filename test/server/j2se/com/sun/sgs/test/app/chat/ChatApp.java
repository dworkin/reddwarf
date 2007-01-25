package com.sun.sgs.test.app.chat;

import java.io.Serializable;
import java.util.Properties;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

/**
 * A simple example IRC-like server application.
 */
public class ChatApp
	implements ManagedObject, Serializable, AppListener, ChannelListener
{
    private static final long serialVersionUID = 1L;

    // TODO: move these to a common location for app and client
    static final byte OP_JOINED  = 0x4A;
    static final byte OP_LEFT    = 0x4C;
    static final byte OP_MESSAGE = 0x4D;
 
    /** {@inheritDoc} */
    public void initialize(Properties props) {
        System.err.format("ChatApp: Starting up\n");

        ChannelManager channelMgr = AppContext.getChannelManager();
        channelMgr.createChannel("_Global_", this, Delivery.RELIABLE);
    }

    /** {@inheritDoc} */
    public ClientSessionListener loggedIn(ClientSession session) {
        System.err.format("ChatApp: ClientSession [%s] joined, named \"%s\"\n",
        	session.toString(), session.getName());
        return new ChatClientSessionListener(this, session);
    }

    /** {@inheritDoc} */
    public void receivedMessage(Channel channel, ClientSession sender,
            byte[] message)
    {
	String messageString = new String(message);
	System.err.format("ChatApp: Echoing to \"%s\": [%s]\n",
		sender.getName(), messageString);
        channel.send(sender, message);
    }

    static void addToChannel(Channel channel, ClientSession session) {
        // Send the membership change first, so the new session doesn't
        // get it.
        byte[] message = getSessionMessage(OP_JOINED, session);
        channel.send(message);
        
        // Now add the new session and tell it about all the members.
        channel.join(session, null);
        sendMemberList(channel, session);
    }

    static void removeFromChannel(Channel channel, ClientSession session) {
        // Remove the member first, so it doesn't get the membership
        // change message.
        channel.leave(session);

        // Tell the rest of the channel about the removal.
        byte[] message = getSessionMessage(OP_LEFT, session);
        channel.send(message);
    }

    static void sendMemberList(Channel channel, ClientSession recipient) {
        for (ClientSession member : channel.getSessions()) {
            byte[] message = getSessionMessage(OP_JOINED, member);
            channel.send(recipient, message);
        }
    }

    static byte[] getSessionMessage(byte opcode, ClientSession session) {
        return getMessage(opcode, session.getSessionId());
    }

    static byte[] getMessage(byte opcode, byte[] payload) {
        byte[] message = new byte[1 + payload.length];
        message[0] = opcode;
        System.arraycopy(payload, 0, message, 1, payload.length);
        return message;
    }

    /**
     * Listener for events from a particular {@link ClientSession}
     * logged into a {@code ChatApp} application.
     * <p>
     * Note that it does not need to implement {@link ManagedObject},
     * since it has no mutable state.
     */
    static final class ChatClientSessionListener
    	    implements ClientSessionListener, Serializable
    {
	private static final long serialVersionUID = 1L;
        
        /** A reference to the {@code ChatApp} for this session. */
	private final ManagedReference appRef;
        
        /** The {@link ClientSession} this listener receives events for. */
	private final ClientSession session;

	/**
         * Creates a new listener for the given {@code ClientSession}.
         *
	 * @param app the {@code ChatApp} for this session
	 * @param session this listener's {@code ClientSession}
	 */
	public ChatClientSessionListener (ChatApp app, ClientSession session) {
	    this.appRef = AppContext.getDataManager().createReference(app);
	    this.session = session;

            ChannelManager channelMgr = AppContext.getChannelManager();
            Channel channel = channelMgr.getChannel("_Global_");
            addToChannel(channel, session);
	}

        /**
         * Returns the {@code ChatApp} for this session.
         * 
         * @return the {@code ChatApp} for this session
         */
	private ChatApp getApp() {
	    return appRef.get(ChatApp.class);
	}
            
	/** {@inheritDoc} */
	public void disconnected(boolean graceful) {
	    System.err.format("ChatApp: ClientSession [%s] disconnected, " +
		    "graceful = %b\n", session.toString(), graceful);

            ChannelManager channelMgr = AppContext.getChannelManager();
            Channel channel = channelMgr.getChannel("_Global_");
            removeFromChannel(channel, session);
	}

	/** {@inheritDoc} */
	public void receivedMessage(byte[] message) {
	    String command = new String(message);
	    System.err.format("ChatApp: Command from \"%s\": [%s]\n",
		    session.getName(), command);

	    ChannelManager channelMgr = AppContext.getChannelManager();
	    if (command.startsWith("/join ")) {
		String channelName = command.substring(6);
		Channel channel;
		try {
		    channel = channelMgr.createChannel(
                            channelName, getApp(), Delivery.RELIABLE);
		} catch (NameExistsException e) {
		    channel = channelMgr.getChannel(channelName);
		}
		System.err.format("ChatApp: Joining \"%s\" to channel %s\n",
			session.getName(), channel.getName());
                addToChannel(channel, session);
	    } else if (command.startsWith("/leave ")) {
		String channelName = command.substring(7);
		Channel channel = channelMgr.getChannel(channelName);
		System.err.format("ChatApp: Removing \"%s\" from channel %s\n",
			session.getName(), channel.getName());
                removeFromChannel(channel, session);
            } else if (command.startsWith("/echo ")) {
                String contents = command.substring(6);
                System.err.format("ChatApp: \"%s\" wants us to echo \"%s\"\n",
                        session.getName(), contents);
                byte[] contentBytes = contents.getBytes();
                byte[] replyBytes = new byte[1 + contentBytes.length];
                replyBytes[0] = OP_MESSAGE;
                System.arraycopy(contentBytes, 0, replyBytes, 1,
                        contentBytes.length);
                session.send(replyBytes);
	    } else if (command.equals("/exit")) {
		System.err.format("ChatApp: \"%s\" requests exit\n",
                        session.getName());
		session.disconnect();
	    } else if (command.equals("/shutdown")) {
		System.err.format("ChatApp: \"%s\" requests shutdown\n",
                        session.getName());
		System.exit(0);
	    } else {
		System.err.format(
                        "ChatApp: Error; \"%s\" sent unknown command [%s]\n",
			session.getName(), command);
	    }
	}
    }
}
