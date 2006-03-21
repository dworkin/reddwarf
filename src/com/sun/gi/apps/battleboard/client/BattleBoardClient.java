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

package com.sun.gi.apps.battleboard.client;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
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
     * and battleboard.playerName can be used to specify the user
     * name, password, and player name, respectively.
     * 
     * If the "battleboard.displayMode" property is "swing", then an
     * interactive Swing-based GUI is used.  If the displayMode is
     * "text", then a simple interactive text-based interface is used. 
     * If the displayMode is "batch" then an even simpler text-based
     * interface, designed for use by the test scripts, is used.
     */

    private String userName =
	    System.getProperty("battleboard.userName", null);
    private String userPassword =
	    System.getProperty("battleboard.userPassword", null);
    private String playerName =
	    System.getProperty("battleboard.playerName", null);
    private String displayMode =
	    System.getProperty("battleboard.displayMode", "swing");

    private boolean batchMode = "batch".equals(displayMode);
    private boolean swingMode = "swing".equals(displayMode);

    public BattleBoardClient() {
    }

    public boolean isBatchMode() {
	return batchMode;
    }

    protected void showPrompt(String prompt) {
        System.out.print(prompt + ": ");
        if (batchMode) {
            System.out.println();
        }
        System.out.flush();
    }

    /**
     * Gets a line of input from the keyboard.
     *
     * @return a line of input from the keyboard
     */
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

    /**
     * {@inheritDoc}
     */
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
	System.exit(1);
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
		 * If the user hasn't provided a playerName, offer
		 * that they use their userName as their
		 * playerName.
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
	     * This Matchmaker channel listener isn't strictly needed. 
	     * The server will manage the process of joining this
	     * client to the correct Game channel when enough players
	     * are present.
             * 
	     * A more sophisticated game might match players to games
	     * based on various parameters.
             * 
	     * This listener will also get called back if the
	     * Matchmaker channel closes for some reason, which a
	     * client may wish to handle.
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
            channel.setListener(
		    new BattleBoardPlayer(mgr, channel, playerName, swingMode));
        }
    }

    protected void sendJoinReq(ClientChannel chan) {
        String cmd = "join " + playerName;
        ByteBuffer buf = ByteBuffer.wrap(cmd.getBytes());
        buf.position(buf.limit());
        mgr.sendToServer(buf, true);
        System.out.println("Waiting for players");
    }

    /**
     * Set up and begin the game.
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

    private void setParams(String userName, String userPassword,
	    String playerName) {
	if (userName != null) {
	    this.userName = userName;
	}
	if (userPassword != null) {
	    this.userPassword = userPassword;
	}
	if (playerName != null) {
	    this.playerName = playerName;
	}
    }

    // main()

    public static void main(String[] args) {
	BattleBoardClient client = new BattleBoardClient();

	if (args.length == 2) {
	    client.setParams(args[0], args[1], args[0]);
	} else if (args.length == 3) {
	    client.setParams(args[0], args[1], args[2]);
	}

        client.run();
    }
}
