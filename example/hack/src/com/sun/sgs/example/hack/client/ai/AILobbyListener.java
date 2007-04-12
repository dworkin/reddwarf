/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
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
