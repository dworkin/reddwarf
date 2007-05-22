/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;

import java.io.Serializable;


public class MoveGameTask implements Task, Serializable {

    private static final long serialVersionUID = 1;

    private ManagedReference playerRef;
    private ManagedReference gameRef = null;

    public MoveGameTask(Player player, Game game) {
        DataManager dataManager = AppContext.getDataManager();
        playerRef = dataManager.createReference(player);
        if (game != null)
            gameRef = dataManager.createReference(game);
    }

    public void run() throws Exception {
        Game game = (gameRef != null) ? gameRef.get(Game.class) : null;
        playerRef.get(Player.class).moveToGame(game);
    }

}
