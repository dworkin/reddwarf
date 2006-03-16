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

package com.sun.gi.client.dirc;

import java.io.File;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
//import java.util.regex.Pattern;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.swing.JFrame;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import com.sun.gi.utils.types.StringUtils;

public class DIRC implements ClientConnectionManagerListener, ChatManager {

    private static Logger log = Logger.getLogger("com.sun.gi.client.dirc");
    //private static final Pattern wsRegexp = Pattern.compile("\\s");

    private String appName;
    private URL discoveryURL;
    //private boolean autologin = true;

    Map<String, ClientChannel> channels;
    ClientChannel currentChannel = null;
    ChatPanel chatPanel;

    ClientConnectionManager clientManager;
    private NameCallback loginNameCB = null;
    private PasswordCallback loginPassCB = null;

    public DIRC(String app, URL discovery) {
        appName = app;
        discoveryURL = discovery;
        channels = new HashMap<String, ClientChannel>();

        chatPanel = new ChatPanel(this);

        JFrame frame = new JFrame("DIRC");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // XXX
        frame.getContentPane().add(chatPanel);
        frame.pack();
        frame.setVisible(true);
    }

    public void run() {
        try {
            clientManager = new ClientConnectionManagerImpl(appName,
                    new URLDiscoverer(discoveryURL));
            clientManager.setListener(this);
            String[] classNames = clientManager.getUserManagerClassNames();
            clientManager.connect(classNames[0]);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    // Chat input

    public void handleChatInput(String text) {
        boolean handled = false;
        if (text.startsWith("/")) {
            handled = handleChatCommand(text);
        }
        if (handled) {
            return;
        }

        ByteBuffer buf = ByteBuffer.wrap(text.getBytes());
        buf.position(buf.limit());

        if (currentChannel == null) {
            chatPanel.info("-> SERVER: " + text);
            clientManager.sendToServer(buf, true);
        } else {
            chatPanel.info("-> [" + currentChannel.getName() + "]: " + text);
            currentChannel.sendBroadcastData(buf, true);
        }
    }

    public boolean handleChatCommand(String text) {
        if (text.startsWith("/user ")) {
            if (loginNameCB != null) {
                loginNameCB.setName(text.substring(6));
            }
            chatPanel.info("[Validator]: " + loginPassCB.getPrompt());
            return true;
        }

        if (text.startsWith("/pass ")) {
            if (loginPassCB != null) { // XXX check that name went
                                        // first
                loginPassCB.setPassword(text.substring(6).toCharArray());
            }
            sendValidationResponse();
            return true;
        }

        if (text.startsWith("/chan")) {
            String newChanName = text.substring(6);
            if (newChanName.length() == 0) {
                currentChannel = null;
                chatPanel.info("Current channel is direct-to-server");
                return true;
            }

            ClientChannel newChan = channels.get(newChanName);
            if (newChan == null) {
                chatPanel.info("No such channel `" + newChanName + "'");
                return true;
            }

            if (!(newChan.equals(currentChannel))) {
                currentChannel = newChan;
            }
            chatPanel.info("Current channel is `" + newChanName + "'");
            return true;
        }

        if (text.startsWith("/quit")) {
            clientManager.disconnect();
            return true;
        }

        return false;
    }

    // ClientConnectionManagerListener methods

    public void validationRequest(Callback[] callbacks) {
        log.finer("validationRequest");

        for (Callback cb : callbacks) {
            try {
                if (cb instanceof NameCallback) {
                    loginNameCB = (NameCallback) cb;
                } else if (cb instanceof PasswordCallback) {
                    loginPassCB = (PasswordCallback) cb;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        chatPanel.info("[Validator]: " + loginNameCB.getPrompt());
    }

    protected void sendValidationResponse() {
        Callback[] callbacks = new Callback[2];
        callbacks[0] = loginNameCB;
        callbacks[1] = loginPassCB;
        loginNameCB = null;
        loginPassCB = null;
        clientManager.sendValidationResponse(callbacks);
    }

    public void connected(byte[] myID) {
        chatPanel.info("connected as " + StringUtils.bytesToHex(myID));
    }

    public void connectionRefused(String message) {
        chatPanel.info("connection refused: `" + message + "'");
    }

    public void disconnected() {
        chatPanel.info("disconnected");
        System.exit(0);
    }

    public void userJoined(byte[] userID) {
        chatPanel.info(StringUtils.bytesToHex(userID) + " connected");
    }

    public void userLeft(byte[] userID) {
        chatPanel.info(StringUtils.bytesToHex(userID) + " disconnected");
    }

    public void failOverInProgress() {
        log.finer("failOverInProgress - client choosing to exit");
        System.exit(1);
    }

    public void reconnected() {
        chatPanel.info("reconnected");
    }

    public void channelLocked(String chan, byte[] userID) {
        chatPanel.info("Channel `" + chan + "' is locked");
    }

    public void joinedChannel(final ClientChannel channel) {
        chatPanel.info("Joined `" + channel.getName() + "'");

        channels.put(channel.getName(), channel);

        channel.setListener(new ClientChannelListener() {
            public void playerJoined(byte[] userID) {
                chatPanel.info(StringUtils.bytesToHex(userID) + " joined `"
                        + channel.getName() + "'");
            }

            public void playerLeft(byte[] userID) {
                chatPanel.info(StringUtils.bytesToHex(userID) + " left `"
                        + channel.getName() + "'");
            }

            public void dataArrived(byte[] userID, ByteBuffer data,
                    boolean reliable) {

                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);

                String userName = clientManager.isServerID(userID) ? "SERVER"
                        : StringUtils.bytesToHex(userID);

                chatPanel.messageArrived(userName, channel.getName(),
                        new String(bytes));
            }

            public void channelClosed() {
                chatPanel.info("Channel " + channel.getName() + " closed");
                if (currentChannel == channels.remove(channel.getName())) {
                    currentChannel = null;
                }
            }
        });
    }

    // main()

    public static void main(String[] args) {
        try {
            new DIRC("BattleTrolls",
                    new File("FakeDiscovery.xml").toURI().toURL()).run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}
