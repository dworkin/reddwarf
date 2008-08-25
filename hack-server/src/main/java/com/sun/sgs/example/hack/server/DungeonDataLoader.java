/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.level.DungeonFactory;

import java.awt.Image;

import java.awt.image.BufferedImage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.StreamTokenizer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import java.util.logging.Logger;

/**
 * This utility class provides static interfaces for loading the simple
 * dungeon and sprite file formats used to setup an app.
 */
public class DungeonDataLoader
{

    private static final Logger logger = 
	Logger.getLogger(DungeonDataLoader.class.getName());

    /**
     * Private helper that sets up a StreamTokenizer from a file.
     */
    private static StreamTokenizer getTokenizer(String filename)
        throws IOException
    {
        InputStreamReader input =
            new InputStreamReader(new FileInputStream(filename));
        StreamTokenizer stok = new StreamTokenizer(input);

        stok.commentChar('#');
        stok.quoteChar('\"');
        stok.eolIsSignificant(false);

        return stok;
    }

    /**
     * This will load the given file, and use its contents to load all
     * the dungeons and sprite maps, handling all registration and task
     * installation required.
     *
     * @param filename the file to start loading
     * @param lobbyRef a reference to the lobby
     * @param gcmRef a reference to the game change manager
     */
    public static void setupDungeons(String root, String filename, Lobby lobby,
                                     GameChangeManager gcm)
        throws IOException
    {
        StreamTokenizer stok = getTokenizer(root + filename);

        HashMap<Integer,Set<Integer>> impMap =
            new HashMap<Integer,Set<Integer>>();
        HashSet<Dungeon> dungeons = new HashSet<Dungeon>();

        // load the dungeons
        while (stok.nextToken() != StreamTokenizer.TT_EOF) {
	    if (stok.sval.equals("Dungeon")) {
                logger.fine("loading dungeon");
                stok.nextToken();
                String name = stok.sval;
                stok.nextToken();
                int spriteMapId = (int)(stok.nval);
                stok.nextToken();
                String dFile = root + stok.sval;
                StreamTokenizer dungeonStream = getTokenizer(dFile);
                Set<Integer> impSet = impMap.get(spriteMapId);
                GameConnector gc =
                    DungeonFactory.loadDungeon(dungeonStream, name,
                                               lobby/*,ag*/);
                dungeons.add(new Dungeon(name, spriteMapId, gc));
            } else {
                logger.warning("Unknown type: " + stok.sval + " on line "
                                   + stok.lineno());
            }
        }

        // register each dungeon
        DataManager dataManager = AppContext.getDataManager();
        for (Dungeon dungeon : dungeons) {
            logger.fine("Registering dungeon: " + dungeon.getName());
            dataManager.setBinding(Game.NAME_PREFIX + dungeon.getName(),
                                   dungeon);
            gcm.notifyGameAdded(dungeon.getName());
        }

        logger.info("finished loading dungeon data files");
    }    

}
