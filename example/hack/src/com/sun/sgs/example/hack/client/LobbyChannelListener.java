/*
 * Copyright 2008 Sun Microsystems, Inc.
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

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.GameMembershipDetail;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.Map;


/**
 * This class listens for all messages from the lobby.
 */
public class LobbyChannelListener extends GameChannelListener
{

    // the listener that gets notified on incoming messages
    private LobbyListener llistener;

    /**
     * Creates an instance of <code>LobbyListener</code>.
     *
     * @param lobbyListener the listener for all lobby messages
     * @param chatListener the listener for all chat messages
     */
    public LobbyChannelListener(LobbyListener lobbyListener,
                                ChatListener chatListener) {
        super(chatListener);

        this.llistener = lobbyListener;
    }

    /**
     * Notifies this listener that some data has arrived from a given
     * player. This should only be called with messages that pertain to
     * the lobby.
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
                    @SuppressWarnings("unchecked")
                    Collection<GameMembershipDetail> details =
                        (Collection<GameMembershipDetail>)(getObject(data));
                    for (GameMembershipDetail detail : details) {
                        // for each update, see if it's about the lobby
                        // or some specific dungeon
                        if (! detail.getGame().equals("game:lobby")) {
                            // it's a specific dungeon, so add the game and
                            // set the initial count
                            llistener.gameAdded(detail.getGame());
                            llistener.playerCountUpdated(detail.getGame(),
                                                         detail.getCount());
                        } else {
                            // it's the lobby, so update the count
                            llistener.playerCountUpdated(detail.getCount());
                        }
                    }
                    break;
                case 2: {
                    // we got a membership count update for some game
                    int count = data.getInt();
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    String name = new String(bytes);
                    
                    // see if it's the lobby or some specific dungeon, and
                    // update the count appropriately
                    if (name.equals("game:lobby"))
                        llistener.playerCountUpdated(count);
                    else
                        llistener.playerCountUpdated(name, count);
                    break; }
                case 3: {
                    // we heard about a new game
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    llistener.gameAdded(new String(bytes));
                    break; }
                case 4: {
                    // we heard that a game was removed
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    llistener.gameRemoved(new String(bytes));
                    break; }
                case 5: {
                    // we got updated with some character statistics...these
                    // are characters that the client is allowed to play
                    @SuppressWarnings("unchecked")
                    Collection<CharacterStats> characters =
                        (Collection<CharacterStats>)(getObject(data));
                    llistener.setCharacters(characters);
                    break; }
                case 8:
                    notifyJoinOrLeave(data, true);
                    break;
                case 9:
                    notifyJoinOrLeave(data, true);
                    break;
                default:
                    // FIXME: we should handle this more gracefully
                    System.out.println("Unexpected lobby message: " + command);
                }
            } catch (IOException ioe) {
                // FIXME: this should probably handle the error a little more
                // gracefully, but it's unclear what the right approach is
                System.out.println("Failed to handle incoming Lobby object");
                ioe.printStackTrace();
            }
        } else {
            // this isn't a message from the server, so it came from some
            // other player on our channel...in this game, that can only
            // mean that we got a chat message
            notifyChatMessage(sender, data);
        }
    }

}
