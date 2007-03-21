/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;


/**
 * NOTE: This should be some kind of pluggable thing, so you can introduce
 * new kinds of monsters and reference them, but for now just making sure
 * that we use a factory gives us that flexability in the future.
 * FIME: this is a work in progress
 */
public class MonsterFactory {

    /**
     * Creates an instance of <code>AICharacterManager</code< and returns
     * a reference to the new instance
     *
     * @param id the character's identifier
     * @param type the chatacter's name
     */
    public static AICharacterManager getMonster(int id, String type) {
        // create a manager
        MonsterCharacter character = null;
        AICharacterManager charMgr =
            AICharacterManager.newInstance();

        // figute out what kind of monster we're creating
        if (type.equals("Demon")) {
            character = new DemonMonster(id, charMgr);
        } else if (type.equals("Rodent")) {
            character = new RodentMonster(id, charMgr);
        } else if (type.equals("Collect")) {
            character = new CollectMonster(id, charMgr);
        } else {
            DataManager dataManager = AppContext.getDataManager();
            dataManager.removeBinding(charMgr.toString());
            dataManager.removeObject(charMgr);
            throw new IllegalArgumentException("Unknown monster type: " +
                                               type);
        }

        // if we're still here then connect the character and manager, and
        // return the manager
        charMgr.setCharacter(character);
        return charMgr;
    }

}
