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
