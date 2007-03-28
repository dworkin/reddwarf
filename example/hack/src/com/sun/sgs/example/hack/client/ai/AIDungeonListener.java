/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.client.ai;

import com.sun.sgs.example.hack.client.BoardListener;
import com.sun.sgs.example.hack.client.GameManager;
import com.sun.sgs.example.hack.client.PlayerListener;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.CharacterStats;

import java.awt.Image;

import java.awt.event.KeyEvent;

import java.util.Map;
import java.util.Random;


public class AIDungeonListener implements BoardListener, PlayerListener {

    private final GameManager dungeonManager;

    private final String name;

    private volatile boolean inDungeon = false;

    private static final int [] keys = { KeyEvent.VK_J, KeyEvent.VK_K,
                                         KeyEvent.VK_L, KeyEvent.VK_I };

    private final Random random;

    private static final int MAX_WAIT = 1500;

    public AIDungeonListener(GameManager dungeonManager, String name) {
        this.dungeonManager = dungeonManager;
        this.name = name;
        random = new Random();
    }

    public void enteredDungeon() {
        inDungeon = true;
        AIClient.runDelayed(new MoveTask(), random.nextInt(MAX_WAIT));
    }
    public void leftDungeon() {
        inDungeon = false;
    }

    private class MoveTask implements Runnable {
        public void run() {
            if (inDungeon) {
                dungeonManager.action(keys[random.nextInt(keys.length)]);
                AIClient.runDelayed(this, random.nextInt(MAX_WAIT));
            }
        }
    }

    public void setSpriteMap(int spriteSize, Map<Integer,Image> spriteMap) {}
    public void changeBoard(Board board) {}
    public void updateSpaces(BoardSpace [] spaces) {}
    public void hearMessage(String message) {}

    public void setCharacter(int id, CharacterStats stats) {}
    public void updateCharacter() {}

}
