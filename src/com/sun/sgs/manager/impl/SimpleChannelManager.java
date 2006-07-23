
package com.sun.sgs.manager.impl;

import com.sun.sgs.Channel;
import com.sun.sgs.ConnectionListener;
import com.sun.sgs.ManagedReference;
import com.sun.sgs.Quality;
import com.sun.sgs.User;
import com.sun.sgs.UserListener;

import com.sun.sgs.kernel.TransactionProxy;

import com.sun.sgs.manager.ChannelManager;

import com.sun.sgs.service.Transaction;

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

    // the proxy used to access transaction state
    private TransactionProxy transactionProxy;

    /**
     * Creates an instance of <code>SimpleChannelManager</code>.
     *
     * @param transactionProxy the proxy used to access transaction state
     */
    public SimpleChannelManager(TransactionProxy transactionProxy) {
        super();

        this.transactionProxy = transactionProxy;
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
        Transaction txn = transactionProxy.getCurrentTransaction();
        return txn.getChannelService().createChannel(txn, channelName,
                                                     quality);
    }

    /**
     * Find a channel based on its name.
     *
     * @param channelName the name of this channel
     */
    public Channel findChannel(String channelName) {
        Transaction txn = transactionProxy.getCurrentTransaction();
        return txn.getChannelService().findChannel(txn, channelName);
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
        Transaction txn = transactionProxy.getCurrentTransaction();
        return txn.getChannelService().getBuffer(txn);
    }

    /**
     * Registers the given listener to listen for messages associated
     * with the given user.
     *
     * @param user the <code>User</code> who's events we're listening for
     * @param listenerReference the listener
     */
    public void registerUserListener(User user,
            ManagedReference<? extends UserListener> listenerReference) {
        Transaction txn = transactionProxy.getCurrentTransaction();
        txn.getChannelService().
            registerUserListener(txn, user, listenerReference);
    }

    /**
     * Registers the given listen to listen for messages associated with
     * any connecting or disconnecting clients.
     *
     * @param listenerReference the listener
     */
    public void registerConnectionListener(
            ManagedReference<? extends ConnectionListener> listenerReference) {
        Transaction txn = transactionProxy.getCurrentTransaction();
        txn.getChannelService().
            registerConnectionListener(txn, listenerReference);
    }

}
