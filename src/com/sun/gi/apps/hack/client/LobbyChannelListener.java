
/*
 * LobbyChannelListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	 3:42:17 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.routing.UserID;

import com.sun.gi.apps.hack.share.CharacterStats;
import com.sun.gi.apps.hack.share.GameMembershipDetail;

import java.nio.ByteBuffer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import com.sun.gi.comm.users.client.ClientChannelListener;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class LobbyChannelListener implements ClientChannelListener
{

    //
    private LobbyListener llistener;

    //
    private ChatListener clistener;

    /**
     *
     */
    public LobbyChannelListener(LobbyListener llistener,
                                ChatListener clistener) {
        this.llistener = llistener;
        this.clistener = clistener;
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
        if (Arrays.equals(from, Client.SERVER_UID)) {
            int command = (int)(data.get());
            System.out.println("Lobby Command = " + command);
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
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    Collection<GameMembershipDetail> details =
                        (Collection<GameMembershipDetail>)(ois.readObject());
                    for (GameMembershipDetail detail : details) {
                        if (! detail.getGame().equals("game:lobby")) {
                            llistener.gameAdded(detail.getGame());
                            llistener.playerCountUpdated(detail.getGame(),
                                                         detail.getCount());
                        } else {
                            llistener.playerCountUpdated(detail.getCount());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break; }
            case 2: {
                int count = data.getInt();
                byte [] bytes = new byte[data.remaining()];
                data.get(bytes);
                String name = new String(bytes);

                if (name.equals("game:lobby"))
                    llistener.playerCountUpdated(count);
                else
                    llistener.playerCountUpdated(name, count);
                break; }
            case 3: {
                byte [] bytes = new byte[data.remaining()];
                data.get(bytes);
                llistener.gameAdded(new String(bytes));
                break; }
            case 4: {
                byte [] bytes = new byte[data.remaining()];
                data.get(bytes);
                llistener.gameRemoved(new String(bytes));
                break; }
            case 5: {
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    Collection<CharacterStats> characters =
                        (Collection<CharacterStats>)(ois.readObject());
                    llistener.setCharacters(characters);
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break; }
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
     * Notifies this listener that the channel has been closed.
     */
    public void channelClosed() {
        
    }

}
