
/*
 * PlayerListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	10:19:03 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.apps.hack.share.CharacterStats;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface PlayerListener
{

    /**
     *
     */
    public void setCharacter(int id, CharacterStats stats);

    /**
     *
     */
    public void updateCharacter(/*FIXME: define this type*/);

    /**
     * FIXME: we also need some inventory methods
     */

}
