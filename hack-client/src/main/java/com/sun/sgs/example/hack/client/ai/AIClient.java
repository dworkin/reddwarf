/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client.ai;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;

import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

import com.sun.sgs.example.hack.client.ChatManager;
import com.sun.sgs.example.hack.client.CreatorChannelListener;
import com.sun.sgs.example.hack.client.CreatorListener;
import com.sun.sgs.example.hack.client.CreatorManager;
import com.sun.sgs.example.hack.client.DungeonChannelListener;
import com.sun.sgs.example.hack.client.GameManager;
import com.sun.sgs.example.hack.client.LobbyChannelListener;
import com.sun.sgs.example.hack.client.LobbyManager;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;

import java.util.Timer;
import java.util.TimerTask;

/**
 * A barebones reference implementation of an AI client.  Developers
 * may extend this class as necessary to add additional features.
 */
public class AIClient implements SimpleClientListener {

    private final String name;

    private final ChatManager chatManager;

    private final LobbyChannelListener lobbyChannelListener;
    private final LobbyManager lobbyManager;
    private final AILobbyListener aiLobbyListener;

    private final CreatorChannelListener creatorChannelListener;
    private final CreatorManager creatorManager;

    private final DungeonChannelListener dungeonChannelListener;
    private final GameManager dungeonManager;
    private final AIDungeonListener aiDungeonListener;

    private final SimpleClient simpleClient;

    private static final Timer timer = new Timer();

    private final CreatorListener creatorListener =
        new CreatorListener() {
            public void changeStatistics(int id, CharacterStats stats) {
                runDelayed(new Runnable() {
                        public void run() {
                            creatorManager.createCurrentCharacter(name);
                        }
                    }, 500);
            }
        };

    public AIClient(String name) {
        this.name = name;

        chatManager = new ChatManager();
        chatManager.addChatListener(new AIChatListener(chatManager, name));

        lobbyManager = new LobbyManager();
        lobbyChannelListener =
            new LobbyChannelListener(lobbyManager, chatManager);
        aiLobbyListener = new AILobbyListener(lobbyManager, name);
        lobbyManager.addLobbyListener(aiLobbyListener);

        creatorManager = new CreatorManager();
        creatorChannelListener =
            new CreatorChannelListener(creatorManager, chatManager);
        creatorManager.addCreatorListener(creatorListener);

        dungeonManager = new GameManager();
        dungeonChannelListener =
            new DungeonChannelListener(dungeonManager, chatManager,
                                       dungeonManager);
        aiDungeonListener = new AIDungeonListener(dungeonManager, name);
        dungeonManager.addBoardListener(aiDungeonListener);
        dungeonManager.addPlayerListener(aiDungeonListener);

        simpleClient = new SimpleClient(this);
        lobbyManager.setClient(simpleClient);
        creatorManager.setClient(simpleClient);
        dungeonManager.setClient(simpleClient);
    }

    public PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(name, "".toCharArray());
    }

    public ClientChannelListener joinedChannel(ClientChannel channel) {
        chatManager.setChannel(channel);
        if (channel.getName().equals("game:lobby")) {
            aiDungeonListener.leftDungeon();
            aiLobbyListener.enteredLobby();
            return lobbyChannelListener;
        } else if (channel.getName().equals("game:creator")) {
            runDelayed(new Runnable() {
                    public void run() {
                        creatorManager.rollForStats(42);
                    }
                }, 750);
            return creatorChannelListener;
        } else {
            aiDungeonListener.enteredDungeon();
            return dungeonChannelListener;
        }
    }

    static void runDelayed(Runnable task, int delay) {
        timer.schedule(new DelayedTask(task), (long)delay);
    }

    private static class DelayedTask extends TimerTask {
        private final Runnable task;
        DelayedTask(Runnable task) {
            this.task = task;
        }
        public void run() {
            task.run();
        }
    }

    public void loggedIn() {}
    public void loginFailed(String reason) {}
    public void disconnected(boolean graceful, String reason) {}
    public void reconnecting() {}
    public void reconnected() {}
    public void receivedMessage(ByteBuffer message) {}

    public static void main(String [] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Please specify the number of clients...");
            System.exit(0);
        }

        for (int i = 0; i < Integer.valueOf(args[0]); i++) {
            AIClient client = new AIClient(String.valueOf(Math.random()));
            client.simpleClient.login(System.getProperties());
        }
    }

}
