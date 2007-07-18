/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;

import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.kernel.ProfileRegistrar;


/**
 * This is an implementation of <code>ChannelManager</code> used to support
 * profiling. It simply calls its backing manager for each manager method. If
 * no <code>ProfileRegistrar</code> is provided via
 * <code>setProfileRegistrar</code> then this manager does no reporting, and
 * only calls through to the backing manager. If the backing manager is also
 * an instance of <code>ProfileProducer</code> then it too will be supplied
 * with the <code>ProfileRegistrar</code> as described in
 * <code>setProfileRegistrar</code>.
 * <p>
 * Note that at present no operations are directly profiled by this class.
 */
public class ProfileChannelManager implements ChannelManager, ProfileProducer {

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
     * <p>
     * Note that if the backing manager supplied to the constructor is also
     * an instance of <code>ProfileProducer</code> then its
     * <code>setProfileRegistrar</code> will be invoked when this method
     * is called.
     */
    public void setProfileRegistrar(ProfileRegistrar profileRegistrar) {
        if (backingManager instanceof ProfileProducer)
            ((ProfileProducer)backingManager).
                setProfileRegistrar(profileRegistrar);
    }

    /**
     * {@inheritDoc}
     */
    public Channel createChannel(String name, ChannelListener listener,
                                 Delivery delivery) {
        return backingManager.createChannel(name, listener, delivery);
    }

    /**
     * {@inheritDoc}
     */
    public Channel getChannel(String name) {
        return backingManager.getChannel(name);
    }

}
