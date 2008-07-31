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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class AILobbyListener implements LobbyListener {

    private final LobbyManager lobbyManager;
    private final String name;

    private Set<String> games = new HashSet<String>();

    private final Random random = new Random();

    private final Runnable gameJoiner;

    public AILobbyListener(LobbyManager lobbyManager, String name) {
        this.lobbyManager = lobbyManager;
        this.name = name;
	
	gameJoiner = new Runnable() {
                public void run() {
		    synchronized (games) {
			if (!games.isEmpty()) {
			    List<String> gameList = 
				new ArrayList<String>(games);
			    int whichGame = random.nextInt(gameList.size());
			    AILobbyListener.this.lobbyManager.
				joinGame(gameList.get(whichGame), 
					 AILobbyListener.this.name);
			}
			else {
			    // retry later when maybe a game has been added
			    AIClient.runDelayed(gameJoiner, 
						random.nextInt(4000));
			}
		    }
                }
	    };
    }

    

    public void enteredLobby() {
	// connect at a random time, but ensure at least some time
	// elapses to give us enough space to join the lobby channel
	// first.
        AIClient.runDelayed(gameJoiner, random.nextInt(4000) + 200);
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
