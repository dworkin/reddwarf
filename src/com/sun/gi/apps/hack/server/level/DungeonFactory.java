/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.Game;
import com.sun.gi.apps.hack.server.GameConnector;

import com.sun.gi.apps.hack.server.ai.AICharacterManager;
import com.sun.gi.apps.hack.server.ai.MonsterFactory;
import com.sun.gi.apps.hack.server.ai.NPCharacter;

import java.io.IOException;
import java.io.StreamTokenizer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


/**
 * This is a fairly simple static factory class that handles loading the
 * contents of a single <code>Dungeon</code>. All AI, levels, etc. are
 * correctly registered as part of the process.
 * <p>
 * Note that in a richer app this would be an interface and provide a
 * way to define multiple factories. These would be identified by some naming
 * scheme, and then multiple file formats could be used. This would also
 * make it easy to support different underlying representation of the levels
 * (as opposed to the exclusive use of <code>SimpleBoard</code>).
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class DungeonFactory
{

    /**
     * This method takes a <code>StreamTokenizer</code> that is setup at
     * the start of a single dungeon file, and loads all the data, creating
     * the AIs, stitching together all connectors between levels, etc. At
     * the end a single <code>Connector</code> is provded as an entry
     * point to the dungeon.
     * <p>
     * FIXME: we should make the aggregator real, or remove it and go
     * back to use of the PDTimer directly.
     *
     * @param stok the stream to tokenize
     * @param gameName the name of the dungeon this is being loaded into
     * @param impassableSprites the set of identifiers that are impassable
     * @param lobbyRef a reference to the lobby
     * @param eventAg an aggreagator for all AI tasks
     *
     * @return a reference to a <code>GameConnector</code> that is the
     *         connection between the dungeon and the lobby
     *
     * @throws IOException if the stream isn't formatted correctly
     */
    public static GLOReference<GameConnector>
        loadDungeon(StreamTokenizer stok, String gameName,
                    Set<Integer> impassableSprites,
                    GLOReference<? extends Game> lobbyRef,
                    EventAggregator eventAg)
        throws IOException
    {
        SimTask task = SimTask.getCurrent();

        // the prefix for all level names
        String levelPrefix = gameName + ":" + SimpleLevel.NAME_PREFIX;

        // details about where we enter the dungeon
        String entryLevel = null;
        int entryX = 0;
        int entryY = 0;

        // the collection of boards and levels
        HashMap<String,SimpleBoard> boards = new HashMap<String,SimpleBoard>();
        HashMap<String,GLOReference<SimpleLevel>> levelRefs =
            new HashMap<String,GLOReference<SimpleLevel>>();

        // the various kinds of connectors
        HashSet<ConnectionData> connections = new HashSet<ConnectionData>();
        HashSet<ConnectionData> oneWays = new HashSet<ConnectionData>();
        HashSet<ConnectionData> playerConnectors =
            new HashSet<ConnectionData>();

        // the collection of Monster (AI) and NPC characters
        HashMap<String,HashSet<GLOReference<AICharacterManager>>> npcMap =
            new HashMap<String,HashSet<GLOReference<AICharacterManager>>>();
        HashMap<String,HashSet<GLOReference<AICharacterManager>>> aiMap =
            new HashMap<String,HashSet<GLOReference<AICharacterManager>>>();

        // first, parse the data file itself
        while (stok.nextToken() != StreamTokenizer.TT_EOF) {
            if (stok.sval.equals("EntryPoint")) {
                // an Entry is LEVEL X_POS Y_POS
                stok.nextToken();
                entryLevel = levelPrefix + stok.sval;
                stok.nextToken();
                entryX = (int)(stok.nval);
                stok.nextToken();
                entryY = (int)(stok.nval);
            } else if (stok.sval.equals("DefineLevel")) {
                // levels are handled separately by SimpleLevel
                stok.nextToken();
                String levelName = levelPrefix + stok.sval;
                boards.put(levelName, new SimpleBoard(stok,
                                                      impassableSprites));
            } else if (stok.sval.equals("Connection")) {
                connections.add(readConnection(stok, levelPrefix));
            } else if (stok.sval.equals("OneWayConnection")) {
                oneWays.add(readConnection(stok, levelPrefix));
            } else if (stok.sval.equals("PlayerConnection")) {
                playerConnectors.add(readConnection(stok, levelPrefix));
            } else if (stok.sval.equals("NPC")) {
                // an NPC is LEVEL NAME ID MESSAGE_1 [ ... MESSAGE_N ]
                stok.nextToken();
                String levelName = levelPrefix + stok.sval;
                stok.nextToken();
                String npcName = stok.sval;
                stok.nextToken();
                int id = (int)(stok.nval);
                stok.nextToken();
                int count = (int)(stok.nval);
                String [] messages = new String[count];
                for (int i = 0; i < count; i++) {
                    stok.nextToken();
                    messages[i] = stok.sval;
                }

                // create the manager for the NPC and the NPC itself
                GLOReference<AICharacterManager> aiCMR =
                    AICharacterManager.newInstance();
                NPCharacter npc =
                    new NPCharacter(id, npcName, messages, aiCMR);
                aiCMR.get(task).setCharacter(npc);

                // put it into a bucket for the given level, creating the
                // bucket if it doesn't already exist
                HashSet<GLOReference<AICharacterManager>> set =
                    npcMap.get(levelName);
                if (set == null) {
                    set = new HashSet<GLOReference<AICharacterManager>>();
                    npcMap.put(levelName, set);
                }
                set.add(aiCMR);
            } else if (stok.sval.equals("Monster")) {
                // a Monster is LEVEL TYPE ID
                stok.nextToken();
                String levelName = levelPrefix + stok.sval;
                stok.nextToken();
                String type = stok.sval;
                stok.nextToken();
                int id = (int)(stok.nval);

                // create the manager and get the right instance
                GLOReference<AICharacterManager> aiCMR =
                    MonsterFactory.getMonster(id, type);

                // put the monster into a bucket for the given level, creating
                // the bucket if it doesn't already exist
                HashSet<GLOReference<AICharacterManager>> set =
                    aiMap.get(levelName);
                if (set == null) {
                    set = new HashSet<GLOReference<AICharacterManager>>();
                    aiMap.put(levelName, set);
                }
                set.add(aiCMR);
            } else {
                throw new IOException("Unknown type: " + stok.sval +
                                      " on line " + stok.lineno());
            }
        }

        // next, create a GLO for each of the levels
        for (String levelName : boards.keySet()) {
            SimpleLevel level = new SimpleLevel(levelName, gameName);
            String gloName = Game.NAME_PREFIX + levelName;
            levelRefs.put(levelName, task.createGLO(level, gloName));
        }

        // with the levels in place, we can generate the connectors and
        // assign them to their board spaces
        for (ConnectionData data : connections) {
            // get the two levels
            GLOReference<SimpleLevel> level1Ref = levelRefs.get(data.level1);
            GLOReference<SimpleLevel> level2Ref = levelRefs.get(data.level2);

            // create a connector and register it
            SimpleConnector connector =
                new SimpleConnector(level1Ref, data.level1X, data.level1Y,
                                    level2Ref, data.level2X, data.level2Y);
            GLOReference<SimpleConnector> connectorRef =
                task.createGLO(connector);

            // notify both boards of the connector
            boards.get(data.level1).setAsConnector(data.level1X, data.level1Y,
                                                   connectorRef);
            boards.get(data.level2).setAsConnector(data.level2X, data.level2Y,
                                                   connectorRef);
        }

        // we also get the player connectors
        for (ConnectionData data : playerConnectors) {
            // get the two levels
            GLOReference<SimpleLevel> level1Ref = levelRefs.get(data.level1);
            GLOReference<SimpleLevel> level2Ref = levelRefs.get(data.level2);

            // create a connector and register it
            PlayerConnector connector =
                new PlayerConnector(level1Ref, data.level1X, data.level1Y,
                                    level2Ref, data.level2X, data.level2Y);
            GLOReference<PlayerConnector> connectorRef =
                task.createGLO(connector);

            // notify both boards of the connector
            boards.get(data.level1).setAsConnector(data.level1X, data.level1Y,
                                                   connectorRef);
            boards.get(data.level2).setAsConnector(data.level2X, data.level2Y,
                                                   connectorRef);
        }

        // same for the one-ways, except that we only set one side
        for (ConnectionData data : oneWays) {
            // get the two levels
            GLOReference<SimpleLevel> level1Ref = levelRefs.get(data.level1);
            GLOReference<SimpleLevel> level2Ref = levelRefs.get(data.level2);

            // create the connector and register it
            OneWayConnector connector =
                new OneWayConnector(level2Ref, data.level2X, data.level2Y);
            GLOReference<OneWayConnector> connectorRef =
                task.createGLO(connector);

            // notify the source board of the connector
            boards.get(data.level1).setAsConnector(data.level1X, data.level1Y,
                                                   connectorRef);
        }

        // also generate the entry connector, register it, and set it for
        // the entry board
        GameConnector gameConnector =
            new GameConnector(lobbyRef, levelRefs.get(entryLevel),
                              entryX, entryY);
        GLOReference<GameConnector> gcRef = task.createGLO(gameConnector);
        boards.get(entryLevel).setAsConnector(entryX, entryY, gcRef);

        // with all the connectors in place, notify the levels
        for (GLOReference<SimpleLevel> levelRef : levelRefs.values()) {
            SimpleLevel level = levelRef.get(task);
            level.setBoard(boards.get(level.getName()));
        }

        // now that the levels are all set, add the NPC characters to the
        // levels and the timer
        for (String levelName : npcMap.keySet()) {
            Level level = levelRefs.get(levelName).get(task);
            for (GLOReference<AICharacterManager> mgr :
                     npcMap.get(levelName)) {
                eventAg.addCharacterMgr(mgr);
                level.addCharacter(mgr);
            }
        }

        // add the Monsters too
        for (String levelName : aiMap.keySet()) {
            Level level = levelRefs.get(levelName).get(task);
            for (GLOReference<AICharacterManager> mgr :
                     aiMap.get(levelName)) {
                eventAg.addCharacterMgr(mgr);
                level.addCharacter(mgr);
            }
        }
        
        // finally add, the items
        // FIXME: support items in file format

        // return the game connector, which is all the Dungeon needs to
        // interact with everything we've setup here
        return gcRef;
    }
    
    /**
     * Private helper method that reads the data for one Connector.
     */
    private static ConnectionData readConnection(StreamTokenizer stok,
                                                 String namePrefix)
        throws IOException
    {
        ConnectionData data = new ConnectionData();

        stok.nextToken(); data.level1 = namePrefix + stok.sval;
        stok.nextToken(); data.level1X = (int)(stok.nval);
        stok.nextToken(); data.level1Y = (int)(stok.nval);

        stok.nextToken(); data.level2 = namePrefix + stok.sval;
        stok.nextToken(); data.level2X = (int)(stok.nval);
        stok.nextToken(); data.level2Y = (int)(stok.nval);

        System.out.println("read connection: " + data.level1 + "@" +
                           data.level1X + "," + data.level1Y);
        System.out.println("it connects to: " + data.level2 + "@" +
                           data.level2X + "," + data.level2Y);

        return data;
    }

    /**
     * Inner utility class that keeps the two points associated with a
     * Connector.
     */
    static class ConnectionData {
        public String level1, level2;
        public int level1X, level1Y, level2X, level2Y;
    }

    /**
     * FIXME: just for testing (or it should be made into a real class)
     */
    public static class EventAggregator implements com.sun.gi.logic.SimTimerListener { //implements com.sun.gi.logic.GLO {
        HashSet<GLOReference<AICharacterManager>> mgrs =
            new HashSet<GLOReference<AICharacterManager>>();
        public void addCharacterMgr(GLOReference<AICharacterManager> mgrRef) {
            mgrs.add(mgrRef);
        }
        public void run() {
            SimTask task = SimTask.getCurrent();
            for (GLOReference<AICharacterManager> mgrRef : mgrs)
                mgrRef.get(task).run();
        }
        public void timerEvent(long eventID) {
            run();
        }
    }

}
