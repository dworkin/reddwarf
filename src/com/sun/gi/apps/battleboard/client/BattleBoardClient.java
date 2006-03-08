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

package com.sun.gi.apps.battleboard.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;

public class BattleBoardClient implements ClientConnectionManagerListener {

    private static Logger log =
        Logger.getLogger("com.sun.gi.apps.battleboard.client");

    private ClientConnectionManager mgr;

    private static Pattern wsRegexp = Pattern.compile("\\s");
    private State state = State.CONNECTING;

    enum State {
        CONNECTING,
        JOINING_GAME
    }

    /*
     * Allow the user to control some aspects of the system from the
     * commandline rather than interactively.
     * 
     * The properties battleboard.userName, battleboard.userPassword,
     * and battleboard.playerName can be used to specify the user name,
     * password, and player name, respectively.
     * 
     * If the "battleboard.interactive" property is "false", then append
     * a newline to each prompt. This makes it considerably easier to
     * attach the client to a line-oriented test harness.
     */

    private String userName = System.getProperty("battleboard.userName", null);
    private String userPassword = System.getProperty(
            "battleboard.userPassword", null);
    private String playerName = System.getProperty("battleboard.playerName",
            null);
    private static boolean nonInteractive = "false".equals(System.getProperty(
            "battleboard.interactive", "true"));

    public BattleBoardClient() {
    // no-op
    }

    /**
     * 
     */
    public void run() {
        try {
            mgr = new ClientConnectionManagerImpl("BattleBoard",
                    new URLDiscoverer(
                            new File("FakeDiscovery.xml").toURI().toURL()));
            mgr.setListener(this);
            String[] classNames = mgr.getUserManagerClassNames();
            mgr.connect(classNames[0]);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    protected void showPrompt(String prompt) {
        System.out.print(prompt + ": ");
        if (nonInteractive) {
            System.out.println();
        }
        System.out.flush();
    }

    public void visitNameCallback(NameCallback cb) {
        log.finer("visitNameCallback");
        if (userName != null) {
            cb.setName(userName);
        } else {
            showPrompt(cb.getPrompt());
            String line = getLine();
            userName = wsRegexp.matcher(line).replaceAll("");
            cb.setName(line);
        }
    }

    public void visitPasswordCallback(PasswordCallback cb) {
        log.finer("visitPasswordCallback");
        if (userPassword != null) {
            cb.setPassword(userPassword.toCharArray());
        } else {
            showPrompt(cb.getPrompt());
            cb.setPassword(getLine().toCharArray());
        }
    }

    // ClientConnectionManagerListener methods

    public void validationRequest(Callback[] callbacks) {
        log.fine("validationRequest");

        for (Callback cb : callbacks) {
            try {
                if (cb == null) {
                    // shouldn't happen.
                    log.warning("null callback");
                } else if (cb instanceof NameCallback) {
                    visitNameCallback((NameCallback) cb);
                } else if (cb instanceof PasswordCallback) {
                    visitPasswordCallback((PasswordCallback) cb);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        mgr.sendValidationResponse(callbacks);
    }

    protected void sendJoinReq(ClientChannel chan) {
        String cmd = "join " + playerName;
        ByteBuffer buf = ByteBuffer.wrap(cmd.getBytes());
        buf.position(buf.limit());
        mgr.sendToServer(buf, true);
    }

    protected String getLine() {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    System.in));
            String line = input.readLine();
            return (line == null) ? "" : line;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public void connected(byte[] myID) {
        log.fine("connected");

        switch (state) {
            case CONNECTING:
                break;
            default:
                log.warning("connected(): unexpected state " + state);
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void connectionRefused(String message) {
        log.info("connectionRefused");
    }

    /**
     * {@inheritDoc}
     */
    public void disconnected() {
        log.fine("disconnected");
    }

    /**
     * {@inheritDoc}
     */
    public void userJoined(byte[] userID) {
        log.fine("userJoined");
    }

    /**
     * {@inheritDoc}
     */
    public void userLeft(byte[] userID) {
        log.fine("userLeft");
    }

    /**
     * {@inheritDoc}
     */
    public void failOverInProgress() {
        log.info("failOverInProgress - client choosing to exit");
        System.exit(1);
    }

    /**
     * {@inheritDoc}
     */
    public void reconnected() {
        log.info("reconnected");
    }

    /**
     * {@inheritDoc}
     */
    public void channelLocked(String chan, byte[] userID) {
        log.warning("Channel `" + chan + "' is locked");
    }

    /**
     * {@inheritDoc}
     */
    public void joinedChannel(final ClientChannel channel) {
        log.fine("joinedChannel " + channel.getName());

        if (channel.getName().equals("matchmaker")) {

            if (playerName == null) {

                /*
                 * If the user hasn't provided a playerName, offer that
                 * they use their userName as their playerName.
                 */

                playerName = userName;
                showPrompt("Enter your handle [" + userName + "]");
                String line = getLine();
                if (line.length() > 0) {
                    // Spaces aren't allowed
                    playerName = wsRegexp.matcher(line).replaceAll("");
                }
            }

            /*
             * This Matchmaker channel listener, isn't strictly needed.
             * The server will manage the process of joining this client
             * to the correct Game channel when enough players are
             * present.
             * 
             * A more sophisticated game might match players to games
             * based on various parameters.
             * 
             * This listener will also get called back if the Matchmaker
             * channel closes for some reason, which a client may wish
             * to handle.
             * 
             * Any non-trivial listener should be implemented as a
             * full-fledged class rather than as an anonynmous class.
             */
            channel.setListener(new ClientChannelListener() {
                public void playerJoined(byte[] playerID) {
                // log.fine("playerJoined on " + channel.getName());
                }

                public void playerLeft(byte[] playerID) {
                // log.fine("playerLeft on " + channel.getName());
                }

                public void dataArrived(byte[] uid, ByteBuffer data,
                        boolean reliable) {
                    // log.fine("dataArrived on " + channel.getName());

                    byte[] bytes = new byte[data.remaining()];
                    data.get(bytes);

                    // log.fine("got matchmaker data `" + bytes + "'");
                }

                public void channelClosed() {
                // log.fine("channel " + channel.getName() + " closed");
                }
            });

            state = State.JOINING_GAME;
            sendJoinReq(channel);
            return;
        }

        // Ok, must be a new game channel we've joined
        if (state == State.JOINING_GAME) {
            channel.setListener(new BattleBoardPlayer(mgr, channel, playerName));
        }
    }

    // main()

    public static void main(String[] args) {
        new BattleBoardClient().run();
    }
}
