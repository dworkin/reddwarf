
/*
 * GameChangeListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Mon Feb 27, 2006	 5:18:59 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLO;

import com.sun.gi.apps.hack.share.GameMembershipDetail;

import java.util.Collection;


/**
 * This interface represents anything that listens for updates to changing
 * details of games. While in this app only the <code>Lobby</code> listens
 * for these updates, in practice anyone could be a consumer of these
 * messages, and this pattern provides a nice way notify multiple parties
 * of these updates.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface GameChangeListener extends GLO
{

    /**
     * Notifies the listener that games were added to the app.
     *
     * @string names the names of the added <code>Game</code>s
     */
    public void gameAdded(Collection<String> name);

    /**
     * Notifies the listener that games were removed from the app.
     *
     * @string names the names of the removed <code>Game</code>s
     */
    public void gameRemoved(Collection<String> name);

    /**
     * Notifies the listener that some game membership information has
     * changed.
     *
     * @param details information about the membership changes
     */
    public void membershipChanged(Collection<GameMembershipDetail> details);

}
