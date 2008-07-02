/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;

import java.io.Serializable;


public class MoveGameTask implements Task, Serializable {

    private static final long serialVersionUID = 1;

    private ManagedReference<Player> playerRef;
    private ManagedReference<Game> gameRef = null;

    public MoveGameTask(Player player, Game game) {
        DataManager dataManager = AppContext.getDataManager();
        playerRef = dataManager.createReference(player);
        if (game != null)
            gameRef = dataManager.createReference(game);
    }

    public void run() throws Exception {
        Game game = (gameRef != null) ? gameRef.get() : null;
        playerRef.get().moveToGame(game);
    }

}
