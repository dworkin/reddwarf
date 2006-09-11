
package com.sun.sgs.app.listen;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.User;

import java.nio.ByteBuffer;


/**
 * This is a callback interface used to listen for network events
 * associated with specific users. It is called when a user joins or
 * leaves a channel, or when data is received from some user.
 * Implementations of this interface register through the
 * <code>ChannelManager</code>.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface UserListener extends ManagedObject
{

    /**
     * Called when a user joins some channel.
     *
     * @param user the user who joined a channel
     * @param channel the channel that the user joined
     */
    public void joined(User user, Channel channel);

    /**
     * Called when a user leaves some channel.
     *
     * @param user the user who left a channel
     * @param channel the channel that the user left
     */
    public void left(User user, Channel channel);

    /**
     * Called when data is received from some user.
     *
     * @param from the user who sent the data
     * @param data the data that was sent
     */
    public void dataReceived(User from, ByteBuffer data);

}
