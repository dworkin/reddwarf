
/*
 * SimpleItem.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Mar  9, 2006	 2:30:55 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;


/**
 * This is a simple implementation of <code>Item</code> that provides
 * non-interactive items.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class SimpleItem implements Item
{

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
