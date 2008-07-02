/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;


/**
 * This class is a prototype example of a pluggable object creator; so
 * you can easily introduce new kinds of monsters and reference them,
 * but for now just making sure that we use a factory gives us that
 * flexability in the future.
 *
 * Note that the implementation of this class is rudamentary and is
 * provided as an example of where to start in future implementations
 * or revisions.
 */
public class MonsterFactory {

    /**
     * Creates an instance of {@code AICharacterManager} and returns a
     * reference to the new instance
     *
     * @param id the character's identifier
     * @param type the chatacter's name
     *
     * @throws IllegalArgumentException if {@code type} is not a
     *         recognized character type.
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
