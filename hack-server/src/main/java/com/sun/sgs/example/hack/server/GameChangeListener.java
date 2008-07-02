/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.example.hack.share.GameMembershipDetail;

import java.util.Collection;


/**
 * This interface represents anything that listens for updates to changing
 * details of games. While in this app only the <code>Lobby</code> listens
 * for these updates, in practice anyone could be a consumer of these
 * messages, and this pattern provides a nice way notify multiple parties
 * of these updates.
 */
public interface GameChangeListener extends ManagedObject {

    /**
     * Notifies the listener that games were added to the app.
     *
     * @param names the names of the added <code>Game</code>s
     */
    public void gameAdded(Collection<String> names);

    /**
     * Notifies the listener that games were removed from the app.
     *
     * @param names the names of the removed <code>Game</code>s
     */
    public void gameRemoved(Collection<String> names);

    /**
     * Notifies the listener that some game membership information has
     * changed.
     *
     * @param details information about the membership changes
     */
    public void membershipChanged(Collection<GameMembershipDetail> details);

}
