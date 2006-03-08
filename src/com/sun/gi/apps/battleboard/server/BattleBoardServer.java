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

package com.sun.gi.apps.battleboard.server;

import java.util.logging.Logger;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;

/**
 * The BattleBoard server.
 * <p>
 * 
 * There's not much to this class:
 */
public class BattleBoardServer
        implements SimBoot<BattleBoardServer>, SimUserListener {
    private static final long serialVersionUID = 1;

    private static Logger log =
        Logger.getLogger("com.sun.gi.apps.battleboard.server");

    // SimBoot methods

    /**
     * Boots the BattleBoard application.
     * <p>
     * 
     * Invoked by the SGS stack when BattleBoard is booted. If
     * <code>firstBoot</code> is true, this call represents the first
     * time that this method has been called for this application across
     * all stacks for the current instance of the app server. When this
     * is true, additional intialization may be necessary. Otherwise,
     * this app has been booted already and is simply being brought up
     * in a new stack.
     * 
     * @param thisGLO a GLOReference to this server itself
     * 
     * @param firstBoot <code>true</code> if this is the first
     * instance of this app to be created, <code>false</code>
     * otherwise
     */
    public void boot(GLOReference<? extends BattleBoardServer> thisGLO,
            boolean firstBoot) {
        SimTask task = SimTask.getCurrent();

        log.info("Booting BattleBoard Server as appID " + task.getAppID());

        /*
         * firstBoot is true if and only if this is the first time the
         * boot method has been called for this app on this system (or
         * if evidence that the app has already been started has been
         * removed -- for example if someone removes all the data
         * associated with this app from the ObjectStore). Therefore
         * when firstBoot is true, we do all the initialization.
         * 
         * For this app, initialization is very simple: all we need to
         * do is to create the matchmaker.
         */

        if (firstBoot) {
            Matchmaker.create();
        }

        /*
         * Register this object as the handler for login and disconnect
         * events for all users on this app.
         */

        task.addUserListener(thisGLO);
    }

    // SimUserListener methods

    /**
     * {@inheritDoc}
     */
    public void userJoined(UserID uid, Subject subject) {
        Player.userJoined(uid, subject);
    }

    /**
     * {@inheritDoc}
     */
    public void userLeft(UserID uid) {
        Player.userLeft(uid);
    }
}
