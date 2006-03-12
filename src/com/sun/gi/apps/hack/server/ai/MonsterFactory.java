
/*
 * MonsterFactory.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Mar  9, 2006	10:44:56 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.ai;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;


/**
 * NOTE: This should be some kind of pluggable thing, so you can introduce
 * new kinds of monsters and reference them, but for now just making sure
 * that we use a factory gives us that flexability in the future.
 * FIME: this is a work in progress
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class MonsterFactory
{

    /**
     * Creates an instance of <code>AICharacterManager</code< and returns
     * a reference to the new instance
     *
     * @parma id the character's identifier
     * @pasrm string the chatacter's name
     */
    public static GLOReference<AICharacterManager> getMonster(int id,
                                                              String type) {
        // create a manager
        MonsterCharacter character = null;
        GLOReference<AICharacterManager> charMgrRef =
            AICharacterManager.newInstance();

        // figute out what kind of monster we're creating
        if (type.equals("Demon")) {
            character = new DemonMonster(id, charMgrRef);
        } else if (type.equals("Rodent")) {
            character = new RodentMonster(id, charMgrRef);
        } else if (type.equals("Collect")) {
            character = new CollectMonster(id, charMgrRef);
        } else {
            charMgrRef.delete(SimTask.getCurrent());
            throw new IllegalArgumentException("Unknown monster type: " +
                                               type);
        }

        // if we're still here then connecto the character and manager, and
        // return the manager
        charMgrRef.get(SimTask.getCurrent()).setCharacter(character);
        return charMgrRef;
    }

}
