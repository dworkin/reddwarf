
/*
 * LobbyListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	 9:05:11 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.apps.hack.share.CharacterStats;

import java.util.Collection;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface LobbyListener
{

    /**
     *
     */
    public void gameAdded(String game);

    /**
     *
     */
    public void gameRemoved(String game);

    /**
     *
     */
    public void playerCountUpdated(int count);

    /**
     *
     */
    public void playerCountUpdated(String game, int count);

    /**
     *
     */
    public void setCharacters(Collection<CharacterStats> characters);

}
