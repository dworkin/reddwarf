/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/*
 * ChannelListChangeListener.java
 */
package com.sun.multicast.reliable.channel;

/**
 * An object that is notified when a ChannelManager's channel list changes.
 * A ChannelListChangeListener may be registered with a ChannelManager using
 * ChannelManager.addChannelListChangeListener. After this, whenever a channel
 * is added to or removed from the ChannelManager's channel list, a
 * ChannelAddEvent or ChannelRemoveEvent will be created and sent to the
 * ChannelListChangeListener's channelAdd or channelRemove method.
 * @see                         Channel
 * @see                         ChannelManager
 * @see                         ChannelAddEvent
 * @see                         ChannelRemoveEvent
 */
interface ChannelListChangeListener extends java.util.EventListener {

    /**
     * Notifies the listener that a channel has been added.
     * @param event a description of the channel addition
     */
    void channelAdd(ChannelAddEvent event);

    /**
     * Notifies the listener that a channel has been removed.
     * @param event a description of the channel removal
     */
    void channelRemove(ChannelRemoveEvent event);
}

