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

package com.sun.gi.comm.users.server.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;
import com.sun.gi.comm.users.server.UserManager;
import com.sun.gi.comm.users.validation.UserValidatorFactory;
import com.sun.gi.utils.nio.NIOConnection;
import com.sun.gi.utils.nio.NIOConnectionListener;
import com.sun.gi.utils.nio.NIOSocketManager;
import com.sun.gi.utils.nio.NIOSocketManagerListener;

public class TCPIPUserManager implements NIOSocketManagerListener, UserManager
{
    // @@ Make 'log' package-visible due to inner class access, below.
    static Logger log = Logger.getLogger("com.sun.gi.comm.users");

    Router router;
    long gameID;
    UserValidatorFactory validatorFactory;
    private String host = "localhost";
    private int port = 1139;
    private NIOSocketManager socketMgr;

    public TCPIPUserManager(Router router, Map params)
            throws InstantiationException {
        this.router = router;
        String p = (String) params.get("host");
        if (p != null) {
            host = p;
        }
        p = (String) params.get("port");
        if (p != null) {
            port = Integer.parseInt(p);
        }
        init();
    }

    private void init() throws InstantiationException {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        log.info("Starting TCPIP User Manager on " + addr);

        try {
            socketMgr = new NIOSocketManager();
            socketMgr.addListener(this);
            socketMgr.acceptConnectionsOn(addr);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new InstantiationException(
                    "TCPIPUserManager failed to initialize");
        }
    }

    // UserManager methods

    public String getClientClassname() {
        return "com.sun.gi.comm.users.client.impl.TCPIPUserManagerClient";
    }

    public Map<String, String> getClientParams() {
        Map<String, String> params = new HashMap<String, String>();
        params.put("host", host);
        params.put("port", Integer.toString(port));
        return params;
    }

    public void setUserValidatorFactory(UserValidatorFactory factory) {
        validatorFactory = factory;
    }

    // NIOSocketManagerListener methods

    public void newConnection(final NIOConnection connection) {
        log.finer("New connection received by server");

        final SGSUserImpl user = new SGSUserImpl(router,
                new TransportProtocolTransmitter() {

                    public void sendBuffers(ByteBuffer[] buffs,
                            boolean reliable)
                    {
                        try {
                            //log.finer("Sending opcode: " + buffs[0].get(0));
                            connection.send(buffs, reliable);
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }

                    public void closeConnection() {
                        log.fine("Server disconnecting user");
                        connection.disconnect();
                    }
                }, validatorFactory.newValidators());

        connection.addListener(new NIOConnectionListener() {
            public void packetReceived(NIOConnection conn,
                    ByteBuffer inputBuffer)
            {
                //log.finer("Server received opcode: " + inputBuffer.get(0));
                user.packetReceived(inputBuffer);
            }

            public void disconnected(NIOConnection conn) {
                log.fine("Server sees socket disconnection");
                user.disconnected();
            }
        });
    }

    /**
     * Not implemented, because a UserManager never initiates a
     * connection.
     * 
     * @throws UnsupportedOperationException
     * @see com.sun.gi.utils.nio.NIOSocketManagerListener#connected
     */
    public void connected(NIOConnection connection) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not implemented, because a UserManager never initiates a
     * connection.
     * 
     * @throws UnsupportedOperationException
     * @see com.sun.gi.utils.nio.NIOSocketManagerListener#connectionFailed
     */
    public void connectionFailed(NIOConnection connection) {
        throw new UnsupportedOperationException();
    }
}
