
package com.sun.sgs.manager;

import com.sun.sgs.Channel;
import com.sun.sgs.ManagedReference;
import com.sun.sgs.Quality;
import com.sun.sgs.User;

import com.sun.sgs.kernel.TaskThread;

import com.sun.sgs.manager.listen.ConnectionListener;
import com.sun.sgs.manager.listen.UserListener;

import java.nio.ByteBuffer;


/**
 * This manager provides access to the channel-related routines.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public abstract class ChannelManager
{

    /**
     * Creates an instance of <code>ChannelManager</code>.
     */
    protected ChannelManager() {

    }

    /**
     * Returns the application's instance of <code>ChannelManager</code>.
     *
     * @return the application's instance of <code>ChannelManager</code>
     */
    public static ChannelManager getInstance() {
        return ((TaskThread)(Thread.currentThread())).getTask().
            getAppContext().getChannelManager();
    }

    /**
     * Creates a channel with the given default properties. If the given
     * name is already in use, or if there are other problems creating the
     * channel, then null is returned.
     *
     * @param channelName the name of this channel
     * @param quality the default quality of service properties
     *
     * @return a new channel, or null
     */
    public abstract Channel createChannel(String channelName, Quality quality);

    /**
     * Find a channel based on its name.
     *
     * @param channelName the name of this channel
     */
    public abstract Channel findChannel(String channelName);

    /**
     * Returns a <code>ByteBuffer</code> that can be used for future
     * messages. Using this method is optional but encouraged, since it
     * will better optimize access to buffers.
     * <p>
     * FIXME: what are the parameters?
     *
     * @return a <code>ByteBuffer</code> to use when send messages
     */
    public abstract ByteBuffer getBuffer();

    /**
     * Registers the given listener to listen for messages associated
     * with the given user.
     *
     * @param user the <code>User</code> whose events we're listening for
     * @param listenerReference the listener
     */
    public abstract void registerUserListener(User user,
            ManagedReference<? extends UserListener> listenerReference);

    /**
     * Registers the given listen to listen for messages associated with
     * any connecting or disconnecting clients.
     *
     * @param listenerReference the listener
     */
    public abstract void registerConnectionListener(
            ManagedReference<? extends ConnectionListener> listenerReference);

}
