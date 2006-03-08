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

package com.sun.gi.apps.glotest.client;

import java.io.File;
import java.util.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;

public class Client implements ClientConnectionManagerListener {

    private static final Logger log =
        Logger.getLogger("com.sun.gi.apps.battleboard.client");

    private ClientConnectionManager mgr;
    //private ClientChannel channel;

    private String appName = "ChannelListenerRace";
    private String appUser = "foo";
    private String appPass = "bar";

    public Client() {}

    public Client(String[] args) {
        if (args.length > 0) {
            appName = args[0];
        }
    }

    public void run() {
        try {
            mgr = new ClientConnectionManagerImpl(appName, new URLDiscoverer(
                    new File("FakeDiscovery.xml").toURI().toURL()));
            mgr.setListener(this);

            String[] classNames = mgr.getUserManagerClassNames();

            mgr.connect(classNames[0]);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public void visitNameCallback(NameCallback cb) {
        log.finer("visitNameCallback");
        cb.setName(appUser);
    }

    public void visitPasswordCallback(PasswordCallback cb) {
        log.finer("visitPasswordCallback");
        cb.setPassword(appPass.toCharArray());
    }

    // ClientConnectionManagerListener methods

    public void validationRequest(Callback[] callbacks) {
        log.info("validationRequest");

        for (Callback cb : callbacks) {
            try {
                if (cb instanceof NameCallback) {
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

    public void connected(byte[] myID) {
        log.info("connected");
    }

    public void connectionRefused(String message) {
        log.info("connectionRefused: " + message);
    }

    public void disconnected() {
        log.info("disconnected");
    }

    public void userJoined(byte[] userID) {
        log.info("userJoined");
    }

    public void userLeft(byte[] userID) {
        log.info("userLeft");
    }

    public void failOverInProgress() {
        log.info("failOverInProgress - client choosing to exit");
        System.exit(1);
    }

    public void reconnected() {
        log.info("reconnected");
    }

    public void channelLocked(String chan, byte[] userID) {
        log.warning("Channel `" + chan + "' is locked");
    }

    public void joinedChannel(final ClientChannel aChannel) {
        log.info("joinedChannel " + aChannel.getName());
    }

    // main()

    public static void main(String[] args) {
        new Client(args).run();
    }
}
