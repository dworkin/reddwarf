/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.example.hack.client.ai;

import com.sun.sgs.example.hack.client.LobbyListener;
import com.sun.sgs.example.hack.client.LobbyManager;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;


public class AILobbyListener implements LobbyListener {

    private final LobbyManager lobbyManager;
    private final String name;

    private List<String> games = new ArrayList<String>();

    private final Random random = new Random();

    public AILobbyListener(LobbyManager lobbyManager, String name) {
        this.lobbyManager = lobbyManager;
        this.name = name;
    }

    public void enteredLobby() {
        AIClient.runDelayed(new Runnable() {
                public void run() {
		    synchronized (games) {
			if (!games.isEmpty()) {
			    int whichGame = random.nextInt(games.size());
			    lobbyManager.joinGame(games.get(whichGame), name);
			}
		    }
                }
            }, random.nextInt(4000));
    }

    public void gameAdded(String game) {
	synchronized (games) {
	    games.add(game);
	}
    }
    public void gameRemoved(String game) {
	synchronized (games) {
	    games.remove(game);
	}
    }
    public void playerCountUpdated(int count) {}
    public void playerCountUpdated(String game, int count) {}
    public void setCharacters(Collection<CharacterStats> characters) {}

}
