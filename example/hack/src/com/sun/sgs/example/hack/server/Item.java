/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;


/**
 * This is the interface for all items in the game.
 */
public interface Item extends ManagedObject {

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
