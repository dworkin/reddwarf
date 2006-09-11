
package com.sun.sgs.manager.impl;

import com.sun.sgs.Channel;
import com.sun.sgs.ManagedReference;
import com.sun.sgs.Quality;
import com.sun.sgs.User;

import com.sun.sgs.manager.ChannelManager;

import com.sun.sgs.manager.listen.ConnectionListener;
import com.sun.sgs.manager.listen.UserListener;

import com.sun.sgs.service.ChannelService;

import java.nio.ByteBuffer;


/**
 * This is a simple implementation of <code>ChannelManager</code> that is the
 * default used.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleChannelManager extends ChannelManager
{

    // the backing channel service
    private ChannelService channelService;

    /**
     * Creates an instance of <code>SimpleChannelManager</code>.
     *
     * @param channelService the backing service
     */
    public SimpleChannelManager(ChannelService channelService) {
        super();

        this.channelService = channelService;
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
    public Channel createChannel(String channelName, Quality quality) {
        return channelService.createChannel(channelName, quality);
    }

    /**
     * Find a channel based on its name.
     *
     * @param channelName the name of this channel
     */
    public Channel findChannel(String channelName) {
        return channelService.findChannel(channelName);
    }

    /**
     * Returns a <code>ByteBuffer</code> that can be used for future
     * messages. Using this method is optional but encouraged, since it
     * will better optimize access to buffers.
     * <p>
     * FIXME: what are the parameters?
     *
     * @return a <code>ByteBuffer</code> to use when send messages
     */
    public ByteBuffer getBuffer() {
        return channelService.getBuffer();
    }

    /**
     * Registers the given listener to listen for messages associated
     * with the given user.
     *
     * @param user the <code>User</code> whose events we're listening for
     * @param listenerReference the listener
     */
    public void registerUserListener(User user,
            ManagedReference<? extends UserListener> listenerReference) {
        channelService.registerUserListener(user, listenerReference);
    }

    /**
     * Registers the given listener to listen for messages associated with
     * any connecting or disconnecting clients.
     *
     * @param listenerReference the listener
     */
    public void registerConnectionListener(
            ManagedReference<? extends ConnectionListener> listenerReference) {
        channelService.registerConnectionListener(listenerReference);
    }

}
