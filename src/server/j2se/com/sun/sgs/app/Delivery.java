package com.sun.sgs.app;

/**
 * Representation for message delivery requirements.  A channel is
 * created with a delivery requirement.  See the {@link
 * ChannelManager#createChannel ChannelManager.createChannel} method
 * for details.
 *
 * <p>Message delivery requirements are specified as follows.  Unless
 * otherwise specified, messages will be delivered <i>at most
 * once</i>.<ul>
 *
 * <li> <b>UNRELIABLE</b>: Message delivery is not guaranteed. No
 * message order is preserved.
 *
 * <li> <b>ORDERED_UNRELIABLE</b>: Message delivery is not guaranteed.
 * Messages that are delivered preserve the sender's order.
 *
 * <li> <b>MOSTLY_RELIABLE</b>: Message delivery is guaranteed unless
 * there is a node or network failure.  Messages that are delivered
 * preserve the sender's order.
 *
 * <li> <b>RELIABLE</b>: Message delivery is guaranteed.  Messages are
 * delivered preserving the sender's order.  A given message will be
 * delivered <i>exactly once</i>.
 */
public enum Delivery {

    UNRELIABLE,
    ORDERED_UNRELIABLE,
    MOSTLY_RELIABLE,
    RELIABLE;
}
