/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
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

        // AIClient.runDelayed(new ChatTask(), random.nextInt(MAX_WAIT));
    }

//     private class ChatTask implements Runnable {
//         public void run() {
//             byte [] bytes = new byte[random.nextInt(128) + 1];
//             random.nextBytes(bytes);
//             chatManager.sendMessage(new String(bytes));
//             AIClient.runDelayed(this, random.nextInt(MAX_WAIT));
//         }
//     }

    public void playerJoined(BigInteger uid) {}
    public void playerLeft(BigInteger uid) {}
    public void messageArrived(String message) {}
    public void addPlayerIdMappings(Map<BigInteger,String> uidMap) {}
    public void addPlayerIdMapping(BigInteger id, String name) {}

}
