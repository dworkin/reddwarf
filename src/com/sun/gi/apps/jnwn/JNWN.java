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

package com.sun.gi.apps.jnwn;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.logic.GLOReference;

import java.util.HashMap;
import java.util.logging.Logger;

import javax.security.auth.Subject;

/**
 *
 * @version $Rev$, $Date$
 */
public class JNWN implements SimBoot<JNWN>, SimUserListener {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger("com.sun.gi.apps.jnwn");

    private HashMap<UserID, GLOReference<User>> userMap;

    public static final String CONTROL_CHANNEL_NAME = "Control";

    private ChannelID controlChannel;

    // SimBoot methods

    /**
     * Called by the SGS stack when this application is booted.
     * If firstBoot is true, this call represents the first time boot()
     * is being called on this application across all stacks.  Otherwise,
     * this app has been booted already (and exists in the DataStore),
     * and is simply being brought up in a new stack as well.
     */
    public void boot(GLOReference<? extends JNWN> thisGLO, boolean firstBoot) {

	SimTask task = SimTask.getCurrent();

	log.info("Booting JNWN Server as appID " + task.getAppID());

	// Get a reference to this object as a GLO
	if (firstBoot) {
	    // Since firstBoot is called exactly once, when the
	    // database is empty, this is the only time we need
	    // to create the supported Area(s).
	    Area.create();
	    userMap = new HashMap<UserID, GLOReference<User>>();
	}

        // Register this object as the handler for login- and
	// disconnect-events for all users on this app.
	task.addUserListener(thisGLO);

	controlChannel = task.openChannel(CONTROL_CHANNEL_NAME);
    }

    // SimUserListener methods

    public void userJoined(UserID uid, Subject subject) {

	SimTask task = SimTask.getCurrent();

	String userName = subject.getPrincipals().iterator().next().getName();
	GLOReference<User> userRef =
	    User.findOrCreate(uid, userName, controlChannel);
	if (userRef == null) {
	    log.severe("No GLO mapped for user " + uid);
	    return;
	}
	userMap.put(uid, userRef);

	// Let the user object do any additional setup
	userRef.get(task).joinedGame();
    }

    public void userLeft(UserID uid) {
	GLOReference<User> userRef = userMap.remove(uid);
	if (userRef == null) {
	    log.severe("No GLO mapped for user " + uid);
	    return;
	}
	// XXX: Check if the userid is mapped correctly

	userRef.get(SimTask.getCurrent()).leftGame();
    }
}
