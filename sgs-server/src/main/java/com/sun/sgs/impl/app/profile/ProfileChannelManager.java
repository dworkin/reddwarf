/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;

/**
 * This is implementation of {@code ChannelManager} simply calls its 
 * backing manager for each manager method. 
 */
public class ProfileChannelManager implements ChannelManager {

    // the channel manager that this manager calls through to
    private final ChannelManager backingManager;

    /**
     * Creates an instance of <code>ProfileChannelManager</code>.
     *
     * @param backingManager the <code>ChannelManager</code> to call through to
     */
    public ProfileChannelManager(ChannelManager backingManager) {
        this.backingManager = backingManager;
    }

    /**
     * {@inheritDoc}
     */
    public Channel createChannel(String name,
				 ChannelListener listener,
				 Delivery delivery) 
    {
        return backingManager.createChannel(name, listener, delivery);
    }

    /**
     * {@inheritDoc}
     */
    public Channel getChannel(String name) {
	return backingManager.getChannel(name);
    }
     
}
