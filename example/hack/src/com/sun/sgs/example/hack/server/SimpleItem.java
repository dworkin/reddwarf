/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import java.io.Serializable;


/**
 * This is a simple implementation of <code>Item</code> that provides
 * non-interactive items.
 */
public class SimpleItem implements Item, Serializable {

    private static final long serialVersionUID = 1;

    // the identifier of this item
    private int id;

    /**
     * Creates an instance of <code>SimpleItem</code>.
     *
     * @param id the item's identifier
     */
    public SimpleItem(int id) {
        this.id = id;
    }

    /**
     * Returns the item's identifier.
     *
     * @return the identifier
     */
    public int getID() {
        return id;
    }

    /**
     * Called when this <code>Item</code> is being given to the character.
     * This method does nothing, since we're only supporting non-interactive
     * items in this class.
     */
    public ActionResult giveTo(CharacterManager characterManager) {
        return ActionResult.SUCCESS;
    }

}
