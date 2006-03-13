/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */


/*
 * DungeonDataLoader.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Mar  3, 2006	12:12:04 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.level.DungeonFactory;

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

import javax.imageio.ImageIO;


/**
 * This utility class provides static interfaces for loading the simple
 * dungeon and sprite file formats used to setup an app.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class DungeonDataLoader
{

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
    public static void setupDungeons(String filename,
                                     GLOReference<Lobby> lobbyRef,
                                     GLOReference<GameChangeManager> gcmRef)
        throws IOException
    {
        StreamTokenizer stok = getTokenizer(filename);

        HashMap<Integer,Set<Integer>> impMap =
            new HashMap<Integer,Set<Integer>>();
        HashSet<Dungeon> dungeons = new HashSet<Dungeon>();

        SimTask task = SimTask.getCurrent();

        // start out by creating the PDTimer that we'll use (for now) for
        // all the AI creatures
        /*com.sun.gi.gloutils.pdtimer.PDTimer timer =
            new com.sun.gi.gloutils.pdtimer.PDTimer(task);
        GLOReference<com.sun.gi.gloutils.pdtimer.PDTimer> timerRef =
        task.createGLO(timer);*/

        // FIXME: just for testing
        com.sun.gi.apps.hack.server.level.DungeonFactory.EventAggregator
            ag = new com.sun.gi.apps.hack.server.level.DungeonFactory.EventAggregator();

        // load the sprite maps and dungeons
        while (stok.nextToken() != StreamTokenizer.TT_EOF) {
            if (stok.sval.equals("SpriteMap")) {
                System.out.println("loading sprite map");
                stok.nextToken();
                int mapId = (int)(stok.nval);
                stok.nextToken();
                int spriteSize = (int)(stok.nval);
                stok.nextToken();
                String smFile = stok.sval;
                task.createGLO(loadSpriteMap(smFile, spriteSize),
                               SpriteMap.NAME_PREFIX + mapId);
                impMap.put(mapId, getImpassableSet(smFile + ".imp"));
            } else if (stok.sval.equals("Dungeon")) {
                System.out.println("loading dungeon");
                stok.nextToken();
                String name = stok.sval;
                stok.nextToken();
                int spriteMapId = (int)(stok.nval);
                stok.nextToken();
                String dFile = stok.sval;
                StreamTokenizer dungeonStream = getTokenizer(dFile);
                Set<Integer> impSet = impMap.get(spriteMapId);
                GLOReference<GameConnector> gcRef =
                    DungeonFactory.loadDungeon(dungeonStream, name, impSet,
                                               lobbyRef, ag/*timerRef*/);
                dungeons.add(new Dungeon(task, name, spriteMapId, gcRef));
            } else {
                System.out.println("Unknown type: " + stok.sval + " on line "
                                   + stok.lineno());
            }
        }

        // register each dungeon
        // FIXME: do this in a getInstance on dungeon
        GameChangeManager gcm = gcmRef.get(task);
        for (Dungeon dungeon : dungeons) {
            System.out.println("Registering dungeon: " + dungeon.getName());
            GLOReference<Dungeon> dungeonRef =
                task.createGLO(dungeon, Game.NAME_PREFIX + dungeon.getName());
            gcm.notifyGameAdded(dungeon.getName());
        }

        // FIXME: just for testing
        GLOReference<com.sun.gi.apps.hack.server.level.DungeonFactory.EventAggregator> agRef =
            task.createGLO(ag);
        task.registerTimerEvent(1000, true, agRef);

        /*
        timerRef.get(task).addTimerEvent(task, SimTask.ACCESS_TYPE.GET, 500,
                                         true, agRef, "run", new Object [] {});

        // finally, start the AI creatures running
        try {
            timerRef.get(task).start(task, 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        */

        System.out.println("finished loading files");
    }

    

    /**
     * Loads a sprite map file.
     *
     * @param filename the graphics file containing the sprites
     * @param spriteSize the size of each sprite
     *
     * @return a container for the sprites
     */
    public static SpriteMap loadSpriteMap(String filename, int spriteSize)
        throws IOException
    {
        System.out.println("Trying to read images from: " + filename);

        // open the file into a single image
        BufferedImage image = ImageIO.read(new File(filename));

        // create the map
        HashMap<Integer,byte[]> spriteMap = new HashMap<Integer,byte[]>();

        // now split up the image into its component sprites
        // FIXME: this should check that the images are the right dimension
        int identifier = 1;
        int totalSize = 0;
        for (int y = 0; y < image.getHeight() / spriteSize; y++) {
            for (int x = 0; x < image.getWidth() / spriteSize; x++) {
                BufferedImage sprite =
                    image.getSubimage(x * spriteSize, y * spriteSize,
                                      spriteSize, spriteSize);
                byte [] bytes = imageToBytes(sprite);
                spriteMap.put(identifier++, bytes);
                totalSize += bytes.length;
            }
        }

        System.out.println("image bytes read: " + totalSize);

        return new SpriteMap(spriteSize, spriteMap);
    }

    /**
     * Private helper used to turn each image into bytes.
     */
    private static byte [] imageToBytes(BufferedImage image)
        throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (! ImageIO.write(image, "png", out))
            System.out.println("FAILED TO GET BYTES!!");
        return out.toByteArray();
    }

    /**
     *
     */
    public static Set<Integer> getImpassableSet(String filename)
        throws IOException
    {
        StreamTokenizer stok = getTokenizer(filename);
        HashSet<Integer> impassableSet = new HashSet<Integer>();

        while (stok.nextToken() != StreamTokenizer.TT_EOF)
            impassableSet.add((int)(stok.nval));

        return impassableSet;
    }

}
