/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
