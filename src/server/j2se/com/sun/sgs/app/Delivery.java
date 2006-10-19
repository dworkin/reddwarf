package com.sun.sgs.app;

/**
 * Representation for message delivery requirements.  A channel is
 * created with a delivery requirement.  See the {@link
 * ChannelManager#createChannel ChannelManager.createChannel} method
 * for details.
 *
 * <p>Unless otherwise specified by a delivery requirement, messages
 * will be delivered <i>at most once</i>.
 */
public enum Delivery {

    /**
     * Unreliable delivery: Message delivery is not guaranteed. No
     * message order is preserved
     */
    UNRELIABLE,

    /**
     * Ordered unreliable delivery: Message delivery is not
     * guaranteed.  Messages that are delivered preserve the sender's
     * order.
     */
    ORDERED_UNRELIABLE,
	
    /**
     * Mostly reliable delivery: Message delivery is guaranteed unless
     * there is a node or network failure.  Messages that are
     * delivered preserve the sender's order.
     */
    MOSTLY_RELIABLE,
	
    /**
     * Reliable delivery: Message delivery is guaranteed.  Messages
     * are delivered preserving the sender's order.  A given message
     * will be delivered <i>exactly once</i>.
     */
    RELIABLE;
}
