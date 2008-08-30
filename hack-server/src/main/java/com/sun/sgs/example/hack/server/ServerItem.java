/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.ManagedObject;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.share.ItemInfo.Item;

/**
 * This is the interface for server-side representation all items in
 * the game.
 */
public interface ServerItem extends ManagedObject, Item {

    /**
     * Called when this {@code Item} is being given to the character.
     * This is useful if you want interactive items (eg, cursing the
     * user as soon as they pickup a talisman).
     *
     * @param characterManager the character to whom this {@code Item}
     *                         will be given.
     *
     * @return the resulting status of the {@code giveTo} action
     */
    public ActionResult giveTo(CharacterManager characterManager);

}
