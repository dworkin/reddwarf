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

import com.sun.sgs.example.hack.client.ChatListener;
import com.sun.sgs.example.hack.client.ChatManager;

import java.math.BigInteger;
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

    public void playerJoined(BigInteger uid) {}
    public void playerLeft(BigInteger uid) {}
    public void messageArrived(BigInteger sender, String message) {}
    public void addUidMappings(Map<BigInteger,String> uidMap) {}

}
