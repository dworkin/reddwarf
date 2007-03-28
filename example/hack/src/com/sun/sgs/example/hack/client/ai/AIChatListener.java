/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.client.ai;

import com.sun.sgs.client.SessionId;

import com.sun.sgs.example.hack.client.ChatListener;
import com.sun.sgs.example.hack.client.ChatManager;

import java.util.Map;
import java.util.Random;


public class AIChatListener implements ChatListener {

    private final ChatManager chatManager;

    private final String name;

    private final Random random;

    private static final int MAX_WAIT = 3000;

    public AIChatListener(ChatManager chatManager, String name) {
        this.chatManager = chatManager;
        this.name = name;

        random = new Random();

        AIClient.runDelayed(new ChatTask(), random.nextInt(MAX_WAIT));
    }

    private class ChatTask implements Runnable {
        public void run() {
            byte [] bytes = new byte[random.nextInt(128) + 1];
            random.nextBytes(bytes);
            chatManager.sendMessage(new String(bytes));
            AIClient.runDelayed(this, random.nextInt(MAX_WAIT));
        }
    }

    public void playerJoined(SessionId uid) {}
    public void playerLeft(SessionId uid) {}
    public void messageArrived(SessionId sender, String message) {}
    public void addUidMappings(Map<SessionId,String> uidMap) {}

}
