
/*
 * Item.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Mar  9, 2006	 2:29:41 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLO;

import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;


/**
 * This is the interface for all items in the game.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface Item extends GLO
{

    /**
     * Returns the item's identifier.
     *
     * @return the identifier
     */
    public int getID();

    /**
     * Called when this <code>Item</code> is being given to the character.
     * This is useful if you want interactive items (eg, cursing the user
     * as soon as they pickup a talisman).
     */
    public ActionResult giveTo(CharacterManager characterManager);

}
