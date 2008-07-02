/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
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
