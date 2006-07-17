
/*
 * ChannelService.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Jul 13, 2006	 7:44:56 PM
 * Desc: 
 *
 */

package com.sun.sgs.service;

import com.sun.sgs.Channel;
import com.sun.sgs.ConnectionListener;
import com.sun.sgs.ManagedReference;
import com.sun.sgs.Quality;
import com.sun.sgs.User;
import com.sun.sgs.UserListener;

import java.nio.ByteBuffer;


/**
 * This type of <code>Service</code> manages all channels.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface ChannelService extends Service
{

    /**
     * Creates a channel with the given default properties. If the given
     * name is already in use, or if there are other problems creating the
     * channel, then null is returned.
     *
     * @param txn the <code>Transaction</code> state
     * @param channelName the name of this channel
     * @param quality the default quality of service properties
     *
     * @return a new channel, or null
     */
    public Channel createChannel(Transaction txn, String channelName,
                                 Quality quality);

    /**
     * Find a channel based on its name.
     *
     * @param txn the <code>Transaction</code> state
     * @param channelName the name of this channel
     */
    public Channel findChannel(Transaction txn, String channelName);

    /**
     * Destroys the given channel.
     *
     * @param txn the <code>Transaction</code> state
     * @param channel the channel to destory
     */
    public void destoryChannel(Transaction txn, Channel channel);

    /**
     * Adds a user to a channel.
     *
     * @param txn the <code>Transaction</code> state
     * @param user the user joining the channel
     * @param channel the channel
     */
    public void join(Transaction txn, User user, Channel channel);

    /**
     * Removes a user from a channel.
     *
     * @param txn the <code>Transaction</code> state
     * @param user the user leaving the channel
     * @param channel the channel
     */
    public void leave(Transaction txn, User user, Channel channel);

    /**
     * Sends a message to a specific user. The user must be a member of
     * the channel.
     *
     * @param txn the <code>Transaction</code> state
     * @param channel the channel to send on
     * @param to the user to receive the message
     * @param data the content of the message
     * @param quality the preferred quality of service paramaters
     */
    public void send(Transaction txn, Channel channel, User to,
                     ByteBuffer data, Quality quality);

    /**
     * Sends a message to the specific sub-group of users. The users must
     * all be members of the channel.
     *
     * @param txn the <code>Transaction</code> state
     * @param channel the channel to send on
     * @param to the users to receive the message
     * @param data the content of the message
     * @param quality the preferred quality of service paramaters
     */
    public void multicast(Transaction txn, Channel channel, User [] to,
                          ByteBuffer data, Quality quality);

    /**
     * Sends a message to all users on this channel. This uses the default
     * quality of service for this channel to send the message.
     *
     * @param txn the <code>Transaction</code> state
     * @param channel the channel to send on
     * @param data the content of the message
     * @param quality the preferred quality of service paramaters
     */
    public void broadcast(Transaction txn, Channel channel,
                          ByteBuffer data, Quality quality);

    /**
     * Returns a <code>ByteBuffer</code> that can be used for future
     * messages. Using this method is optional but encouraged, since it
     * will better optimize access to buffers.
     * <p>
     * FIXME: what are the parameters?
     *
     * @param txn the <code>Transaction</code> state
     *
     * @return a <code>ByteBuffer</code> to use when send messages
     */
    public ByteBuffer getBuffer(Transaction txn);

    /**
     * Registers the given listener to listen for messages associated
     * with the given user.
     *
     * @param txn the <code>Transaction</code> state
     * @param user the <code>User</code> who's events we're listening for
     * @param listenerReference the listener
     */
    public void registerUserListener(Transaction txn, User user,
            ManagedReference<? extends UserListener> listenerReference);

    /**
     * Registers the given listen to listen for messages associated with
     * any connecting or disconnecting clients.
     *
     * @param txn the <code>Transaction</code> state
     * @param listenerReference the listener
     */
    public void registerConnectionListener(Transaction txn,
            ManagedReference<? extends ConnectionListener> listenerReference);

}
