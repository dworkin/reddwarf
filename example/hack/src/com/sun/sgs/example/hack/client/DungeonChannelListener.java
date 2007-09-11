/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.example.hack.client;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.SessionId;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.CharacterStats;

import java.awt.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;


/**
 * This class listens for all nessages from a dungeon.
 */
public class DungeonChannelListener extends GameChannelListener
{

    // the listener that gets notified on incoming board-related messages
    private BoardListener blistener;

    // the listener that gets notified on incoming player-related messages
    private PlayerListener plistener;

    /**
     * Creates an instance of <code>DungeonChannelListener</code>.
     *
     * @param boardListener the listener for all board messages
     * @param chatListener the listener for all chat messages
     * @param playerListener the listener for all player messages
     */
    public DungeonChannelListener(BoardListener boardListener,
                                  ChatListener chatListener,
                                  PlayerListener playerListener) {
        super(chatListener);

        this.blistener = boardListener;
        this.plistener = playerListener;
    }

    /**
     * Notifies this listener that some data has arrived from a given
     * player. This should only be called with messages that pertain to
     * a dungeon.
     *
     * @param from the ID of the sending player.
     * @param data the packet data
     * @param reliable true if this packet was sent reliably
     */
    //public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
    public void receivedMessage(ClientChannel channel, SessionId sender,
                                byte [] message) {
        ByteBuffer data = ByteBuffer.allocate(message.length);
        data.put(message);
        data.rewind();
        
        if (sender == null) {
            // if this is a message from the server, then it's some
            // command that we need to process, so get the command code
            int command = (int)(data.get());

            // FIXME: this should really be an enumeration
            try {
                switch (command) {
                case 0:
                    // we got some uid to player name mapping
                    addUidMappings(data);
                    break;
                case 1:
                    // we were sent game membership updates
                    int spriteSize = data.getInt();
                    @SuppressWarnings("unchecked")
                    Map<Integer,byte[]> spriteMap =
                        (Map<Integer,byte[]>)(getObject(data));
                    blistener.setSpriteMap(spriteSize, convertMap(spriteMap));
                break;
                case 2:
                    // we got a complete board update
                    Board board = (Board)(getObject(data));
                    blistener.changeBoard(board);
                break;
                case 3:
                    // we got some selective space updates
                    @SuppressWarnings("unchecked")
                    Collection<BoardSpace> spaces =
                        (Collection<BoardSpace>)(getObject(data));
                    BoardSpace [] s = new BoardSpace[spaces.size()];
                    blistener.updateSpaces(spaces.toArray(s));
                break;
                case 4:
                    // we heard some message from the server
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    String msg = new String(bytes);
                    blistener.hearMessage(msg);
                    break;
                case 8:
                    notifyJoinOrLeave(data, true);
                    break;
                case 9:
                    notifyJoinOrLeave(data, true);
                    break;
                case 64:
                    // we were sent updated character statistics
                    int id = data.getInt();
                    CharacterStats stats = (CharacterStats)(getObject(data));
                    plistener.setCharacter(id, stats);
                    break;
                default:
                    // FIXME: we should handle this more gracefully
                    System.out.println("Unexpected dungeon message: "
                                       + command);
                }
            } catch (IOException ioe) {
                // FIXME: this should probably handle the error a little more
                // gracefully, but it's unclear what the right approach is
                System.out.println("Failed to handle incoming Dungeon object");
                ioe.printStackTrace();
            }
        } else {
            // this isn't a message from the server, so it came from some
            // other player on our channel...in this game, that can only
            // mean that we got a chat message
            notifyChatMessage(sender, data);
        }
    }

    /**
     * A private helper that converts the map from the server (that maps
     * integers to byte arrays) into the form needed on the clie (that
     * maps integers to images). The server sends the byte array form
     * because images aren't serializable.
     */
    private Map<Integer,Image> convertMap(Map<Integer,byte[]> map) {
        Map<Integer,Image> newMap = new HashMap<Integer,Image>();

        // for each of the identified sprites, try to load the bytes
        // as a recognizable image format and store in the new map
        for (int identifier : map.keySet()) {
            try {
                ByteArrayInputStream in =
                    new ByteArrayInputStream(map.get(identifier));
                newMap.put(identifier, ImageIO.read(in));
            } catch (IOException ioe) {
                System.out.println("Failed to convert image: " + identifier);
                ioe.printStackTrace();
            }
        }

        return newMap;
    }

}
