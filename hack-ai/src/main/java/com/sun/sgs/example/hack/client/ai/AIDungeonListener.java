/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
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
	// add delay to the initial movement command in order to wait
	// for us to join the dungeon channel officially
        AIClient.runDelayed(new MoveTask(), random.nextInt(MAX_WAIT) + 500);
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

    public void changeBoard(Board board) {}
    public void updateSpaces(BoardSpace [] spaces) {}
    public void hearMessage(String message) {}

    public void setCharacter(int id, CharacterStats stats) {}
    public void updateCharacter() {}

}
