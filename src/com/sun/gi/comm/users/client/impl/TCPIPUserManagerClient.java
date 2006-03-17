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

package com.sun.gi.comm.users.client.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;

import com.sun.gi.comm.discovery.DiscoveredUserManager;
import com.sun.gi.comm.users.client.UserManagerClient;
import com.sun.gi.comm.users.client.UserManagerClientListener;
import com.sun.gi.comm.users.protocol.TransportProtocol;
import com.sun.gi.comm.users.protocol.TransportProtocolClient;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;
import com.sun.gi.comm.users.protocol.impl.BinaryPktProtocol;
import com.sun.gi.utils.nio.NIOConnection;
import com.sun.gi.utils.nio.NIOConnectionListener;
import com.sun.gi.utils.nio.NIOSocketManager;
import com.sun.gi.utils.nio.NIOSocketManagerListener;

/**
 * TCPIPUserManagerClient implements a simple TCP/IP based UserManager.
 * It is intended to serve both as a basic UserManager and as an example
 * for the creation of other UserManagers
 * 
 * @author Jeffrey P. Kesselman
 * @author James Megquier
 * 
 * @version 1.0
 */
public class TCPIPUserManagerClient
        implements UserManagerClient, NIOSocketManagerListener,
        TransportProtocolClient
{
    private static Logger log = Logger.getLogger("com.sun.gi.comm.users");

    NIOSocketManager mgr;
    UserManagerClientListener listener;
    TransportProtocol protocol;

    /**
     * Default constructor
     * 
     * @throws InstantiationException
     */
    public TCPIPUserManagerClient() throws InstantiationException {
        try {
            mgr = new NIOSocketManager();
            mgr.addListener(this);
            protocol = new BinaryPktProtocol();
            protocol.setClient(this);
        } catch (IOException ex) {
            throw new InstantiationException(ex.getMessage());
        }
    }

    public boolean connect(SocketAddress addr,
            UserManagerClientListener clientListener)
    {
        this.listener = clientListener;

        log.fine("Attempting to connect to a TCPIP User Manager on " + addr);

        return (mgr.makeConnectionTo(addr) != null);
    }

    // NIOSocketManagerListener methods

    /*
     * Not implemented because this class does not accept incoming TCP
     * connections
     */
    public void newConnection(NIOConnection connection) {
        throw new UnsupportedOperationException();
    }

    public void connected(final NIOConnection connection) {
        protocol.setTransmitter(new TransportProtocolTransmitter() {

            public void sendBuffers(ByteBuffer[] buffs, boolean reliable) {
                try {
                    connection.send(buffs, reliable);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            public void closeConnection() {
                connection.disconnect();
            }
        });

        connection.addListener(new NIOConnectionListener() {
            public void packetReceived(NIOConnection conn,
                    ByteBuffer inputBuffer) {
                protocol.packetReceived(inputBuffer);
            }

            public void disconnected(NIOConnection conn) {
                connectionDropped();
            }
        });

        listener.connected();
    }

    void connectionDropped() {
        listener.disconnected();
    }

    public void connectionFailed(NIOConnection connection) {
        listener.disconnected();
    }

    // TransportProtocolClient methods

    public void rcvUnicastMsg(boolean reliable, byte[] chanID, byte[] from,
            byte[] to, ByteBuffer databuff) {
        listener.recvdData(chanID, from, databuff, reliable);
    }

    public void rcvMulticastMsg(boolean reliable, byte[] chanID, byte[] from,
            byte[][] tolist, ByteBuffer databuff) {
        listener.recvdData(chanID, from, databuff, reliable);
    }

    public void rcvBroadcastMsg(boolean reliable, byte[] chanID, byte[] from,
            ByteBuffer databuff) {
        listener.recvdData(chanID, from, databuff, reliable);
    }

    public void rcvValidationReq(Callback[] cbs) {
        listener.validationDataRequest(cbs);
    }

    public void rcvUserAccepted(byte[] user) {
        listener.loginAccepted(user);
    }

    public void rcvUserRejected(String message) {
        listener.loginRejected(message);
    }

    public void rcvUserJoined(byte[] user) {
        listener.userAdded(user);
    }

    public void rcvUserLeft(byte[] user) {
        listener.userDropped(user);
    }

    public void rcvUserJoinedChan(byte[] chanID, byte[] user) {
        listener.userJoinedChannel(chanID, user);
    }

    public void rcvUserLeftChan(byte[] chanID, byte[] user) {
        listener.userLeftChannel(chanID, user);
    }

    public void rcvReconnectKey(byte[] user, byte[] key, long ttl) {
        listener.newConnectionKeyIssued(key, ttl);
    }

    public void rcvJoinedChan(String name, byte[] chanID) {
        listener.joinedChannel(name, chanID);
    }

    public void rcvLeftChan(byte[] chanID) {
        listener.leftChannel(chanID);
    }

    public void rcvUserDisconnected(byte[] chanID) {
        listener.disconnected();
    }

    // UserManagerClient methods

    public boolean connect(DiscoveredUserManager choice,
            UserManagerClientListener mgrListener) {

        String host = choice.getParameter("host");
        int port = Integer.parseInt(choice.getParameter("port"));

        return connect(new InetSocketAddress(host, port), mgrListener);
    }

    public boolean connect(Map<String, String> params,
            UserManagerClientListener mgrListener) {

        String host = params.get("host");
        int port = Integer.parseInt(params.get("port"));

        return connect(new InetSocketAddress(host, port), mgrListener);
    }

    public void login() {
        try {
            protocol.sendLoginRequest();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void validationDataResponse(Callback[] cbs) {
        try {
            protocol.sendValidationResponse(cbs);
        } catch (UnsupportedCallbackException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void logout() {
        try {
            protocol.sendLogoutRequest();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void joinChannel(String channelName) {
        try {
            protocol.sendJoinChannelReq(channelName);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void sendToServer(ByteBuffer buff, boolean reliable) {
        try {
            protocol.sendServerMsg(reliable, buff);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void reconnectLogin(byte[] userID, byte[] reconnectionKey) {
        try {
            protocol.sendReconnectRequest(userID, reconnectionKey);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void sendUnicastMsg(byte[] chanID, byte[] to, ByteBuffer data,
            boolean reliable) {
        try {
            protocol.sendUnicastMsg(chanID, to, reliable, data);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void sendMulticastMsg(byte[] chanID, byte[][] to, ByteBuffer data,
            boolean reliable) {
        try {
            protocol.sendMulticastMsg(chanID, to, reliable, data);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void sendBroadcastMsg(byte[] chanID, ByteBuffer data,
            boolean reliable) {
        try {
            protocol.sendBroadcastMsg(chanID, reliable, data);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void recvServerID(byte[] user) {
        listener.recvServerID(user);
    }

    public void leaveChannel(byte[] channelID) {
        try {
            protocol.sendLeaveChannelReq(channelID);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void rcvChannelLocked(String channelName, byte[] user) {
        listener.channelLocked(channelName, user);
    }
}
