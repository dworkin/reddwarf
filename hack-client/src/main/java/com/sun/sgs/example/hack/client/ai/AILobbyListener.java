/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
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
