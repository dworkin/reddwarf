/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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
import java.util.Random;


public class AILobbyListener implements LobbyListener {

    private final LobbyManager lobbyManager;
    private final String name;

    private ArrayList<String> games;

    private final Random random;

    public AILobbyListener(LobbyManager lobbyManager, String name) {
        this.lobbyManager = lobbyManager;
        this.name = name;

        games = new ArrayList<String>();
        random = new Random();
    }

    public void enteredLobby() {
        AIClient.runDelayed(new Runnable() {
                public void run() {
                    int whichGame = random.nextInt(games.size());
                    lobbyManager.joinGame(games.get(whichGame), name);
                }
            }, random.nextInt(4000));
    }

    public void gameAdded(String game) {
        games.add(game);
    }
    public void gameRemoved(String game) {
        games.remove(game);
    }
    public void playerCountUpdated(int count) {}
    public void playerCountUpdated(String game, int count) {}
    public void setCharacters(Collection<CharacterStats> characters) {}

}
