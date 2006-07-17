
/*
 * Channel.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Jul 13, 2006	 7:08:58 PM
 * Desc: 
 *
 */

package com.sun.sgs;

import java.nio.ByteBuffer;


/**
 * Channels are used to communicate with clients. A channel may have any
 * number of users joined to it. This base interface provides the core
 * operations on channels. Specific sub-classes may offer more detailed
 * functionality (like access to the channel membership).
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface Channel extends ManagedReference
{

    /**
     * Returns the default quality of server parameters used when this
     * channel was created.
     *
     * @return the default quality of service for this channel
     */
    public Quality getQuality();

    /**
     * Adds a user to this channel.
     *
     * @param user the user joining the channel
     */
    public void join(User user);

    /**
     * Removes a user from this channel.
     *
     * @param user the user leaving the channel
     */
    public void leave(User user);

    /**
     * Sends a message to a specific user. The user must be a member of
     * this channel. This uses the default quality of service for this
     * channel to send the message.
     *
     * @param to the user to receive the message
     * @param data the content of the message
     */
    public void send(User to, ByteBuffer data);

    /**
     * Sends a message to a specific user. The user must be a member of
     * this channel. The given quality of service parameters are used
     * override the default parameters, where allowed by the system.
     *
     * @param to the user to receive the message
     * @param data the content of the message
     * @param quality the preferred quality of service parameters
     */
    public void send(User to, ByteBuffer data, Quality quality);

    /**
     * Sends a message to the specific sub-group of users. The users must
     * all be members of the channel. This uses the default quality of
     * service for this channel to send the message.
     *
     * @param to the user to receive the message
     * @param data the content of the message
     */
    public void multicast(User [] to, ByteBuffer data);

    /**
     * Sends a message to the specific sub-group of users. The users must
     * all be members of the channel. The given quality of service
     * parameters are used to override the default parameters, where
     * allowed by the system.
     *
     * @param to the user to receive the message
     * @param data the content of the message
     */
    public void multicast(User [] to, ByteBuffer data, Quality quality);

    /**
     * Sends a message to all users on this channel. This uses the default
     * quality of service for this channel to send the message.
     *
     * @param data the content of the message
     */
    public void broadcast(ByteBuffer data);

    /**
     * Sends a message to all users on this channel. The given quality of
     * service parameters are used override the default parameters, where
     * allowed by the system.
     *
     * @param data the content of the message
     * @param quality the preferred quality of service parameters
     */
    public void broadcast(ByteBuffer data, Quality quality);

    /**
     * Returns a buffer for use in sending messages. While use of this method
     * is optional (as opposed to directly allocating a buffer), it will
     * provide more effecient operation.
     * <p>
     * FIXME: what are the parameters?
     */
    public ByteBuffer getBuffer();

    /**
     * Destroys this channel.
     */
    public void destory();

}
