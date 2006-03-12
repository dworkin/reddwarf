
/*
 * DungeonChannelListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	12:44:45 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.routing.UserID;

import com.sun.gi.comm.users.client.ClientChannelListener;

import com.sun.gi.apps.hack.share.BoardSpace;
import com.sun.gi.apps.hack.share.CharacterStats;

import java.awt.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class DungeonChannelListener implements ClientChannelListener
{

    //
    private BoardListener blistener;

    //
    private ChatListener clistener;

    //
    private PlayerListener plistener;

    /**
     *
     */
    public DungeonChannelListener(BoardListener blistener,
                                  ChatListener clistener,
                                  PlayerListener plistener) {
        this.blistener = blistener;
        this.clistener = clistener;
        this.plistener = plistener;
    }

    /**
     * A new player has joined the channel this listener is registered on.
     *
     * @param playerID The ID of the joining player.
     */
    public void playerJoined(byte[] playerID) {
        try {
            clistener.playerJoined(new UserID(playerID));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * A player has left the channel this listener is registered on.
     *
     * @param playerID The ID of the leaving player.
     */
    public void playerLeft(byte[] playerID) {
        try {
            clistener.playerLeft(new UserID(playerID));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * A packet has arrived for this listener on this channel.
     *
     * @param from     the ID of the sending player.
     * @param data     the packet data
     * @param reliable true if this packet was sent reliably
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
        // FIXME: what we should do at this point is figure out if it's
        // from the server or another client, and act on this...
        // ...for now, assume it's just chat data

        if (Arrays.equals(from, Client.SERVER_UID)) {
            int command = (int)(data.get());
            switch (command) {
            case 0: {
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    Map<UserID,String> uidMap =
                        (Map<UserID,String>)(ois.readObject());
                    clistener.addUidMappings(uidMap);
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break;
            }
            case 1: {
                int spriteSize = data.getInt();
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    Map<Integer,byte[]> spriteMap =
                        (Map<Integer,byte[]>)(ois.readObject());
                    blistener.setSpriteMap(spriteSize, convertMap(spriteMap));
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break;
            }
            case 2:
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    com.sun.gi.apps.hack.share.Board board =
                        (com.sun.gi.apps.hack.share.Board)(ois.readObject());
                    blistener.changeBoard(board);
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break;
            case 3:
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    Collection<BoardSpace> spaces =
                        (Collection<BoardSpace>)(ois.readObject());
                    BoardSpace [] s = new BoardSpace[spaces.size()];
                    blistener.updateSpaces(spaces.toArray(s));
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break;
            case 4: {
                byte [] bytes = new byte[data.remaining()];
                data.get(bytes);
                String message = new String(bytes);
                blistener.hearMessage(message);
                break;
            }
            case 64:
                System.out.println("got character data");
                int id = data.getInt();
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    CharacterStats stats =
                        (CharacterStats)(ois.readObject());
                    plistener.setCharacter(id, stats);
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break;
            default:
                System.out.println("Unexpected command!");
            }
        } else {
            try {
                // for now, we assume this is the client
                byte [] bytes = new byte[data.remaining()];
                data.get(bytes);
                clistener.messageArrived(new UserID(from), new String(bytes));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     */
    private Map<Integer,Image> convertMap(Map<Integer,byte[]> map) {
        Map<Integer,Image> newMap = new HashMap<Integer,Image>();

        try {
            for (int identifier : map.keySet()) {
                ByteArrayInputStream in =
                    new ByteArrayInputStream(map.get(identifier));
                newMap.put(identifier, ImageIO.read(in));
            }
        } catch (IOException ioe) {
            System.out.println("Failed to convert images");
            ioe.printStackTrace();
        }

        return newMap;
    }

    /**
     * Notifies this listener that the channel has been closed.
     */
    public void channelClosed() {
        
    }

}
