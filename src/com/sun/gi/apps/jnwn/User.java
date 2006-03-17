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

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Logger;

import java.security.Principal;
import javax.security.auth.Subject;

/**
 *
 * @version $Rev$, $Date$
 */
public class User implements SimUserDataListener {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger("com.sun.gi.apps.jnwn");

    private String  name;
    private UserID  uid;
    private ChannelID  controlChannel;
    private GLOReference<Character>  characterRef;
    private boolean connected;

    protected User(String userName, UserID userID) {
	name = userName;
	uid  = userID;
	characterRef = null;
	connected = true;
    }

    protected void boot(GLOReference<User> thisRef, ChannelID controlChan) {

	controlChannel = controlChan;

	if (characterRef == null) {
	    // This is a new user

	    // Create a new character for this user
	    characterRef = Character.create(thisRef);
	}
    }

    public static User get(String name) {
	SimTask task = SimTask.getCurrent();
	GLOReference<User> ref = task.findGLO(name);
	return ref == null ? null : ref.get(task);
    }

    public String getName() {
	return name;
    }

    public UserID getUID() {
	return uid;
    }

    protected void setUID(UserID userID) {
	uid = userID;
    }

    public GLOReference<Character> getCharacterRef() {
	return characterRef;
    }

    public void setCharacterRef(GLOReference<Character> ref) {
	characterRef = ref;
    }

    public static GLOReference<User> findOrCreate(UserID userID,
	    String userName, ChannelID controlChan) {

	SimTask task = SimTask.getCurrent();
	GLOReference<User> ref =
	    findOrCreateGLO(userID, userName);

	// Register the new User GLO as the event-handler for this uid.
	task.addUserDataListener(userID, ref);

	// Do any additional init
	ref.get(task).boot(ref, controlChan);

	return ref;
    }

    protected boolean isConnected() {
	return connected;
    }

    protected static GLOReference<User> findOrCreateGLO(UserID userID,
	    String userName) {

	SimTask task = SimTask.getCurrent();
	GLOReference<User> ref = task.findGLO(userName);
	if (ref != null) {
	    User user = ref.get(task);
	    if (user != null) {
		UserID oldUserID = user.getUID();
		if (user.isConnected() && oldUserID != null) {
		    log.severe("User " + userName +
			" still logged in as uid " + oldUserID);
		    // user.kick();
		    //   - or -
		    // task.disconnect(oldUserID);
		}
		user.setUID(userID);
		return ref;
	    }
	    // Else we have a reference to an non-existant object, so
	    // fall-though and create a new one.
	}

	return task.createGLO(new User(userName, userID), userName);
    }

    public void joinedGame() {
	log.fine("User " + name + " [" + uid + "] joined game");

	// Put this user on the global control channel
	SimTask.getCurrent().join(uid, controlChannel);

	connected = true;
    }

    public void leftGame() {
	log.fine("User " + name + " [" + uid + "] left game");
	connected = false;
    }

    // SimUserDataListener methods

    public void userJoinedChannel(ChannelID channelID, UserID userID) {
	log.fine("User " + userID + " joined channel " + channelID);

	SimTask task = SimTask.getCurrent();

	// Paranoia
	if (! uid.equals(userID)) {
	    log.warning("Got UID " + userID + " expected " + uid);
	    return;
	}

	if (characterRef == null) {
	    log.severe("Channel joined, but no character assigned");
	    return;
	}

	Character ch = characterRef.peek(task);

	if (controlChannel.equals(channelID)) {
	    String message = "userid " + ch.getCharacterID();
	    ByteBuffer buf = ByteBuffer.wrap(message.getBytes());
	    buf.position(buf.limit());
	    task.sendData(controlChannel, uid, buf, true);

	    // Join the map we're going to be playing
	    ch.joinArea();

	    return;
	}

	// Otherwise, let my Character handle it
	ch.joinedChannel(channelID);
    }

    public void userLeftChannel(ChannelID channelID, UserID userID) {
	log.fine("User " + userID + " left channel " + channelID);

	if (controlChannel.equals(channelID)) {
	    // ignore it; we're shutting down
	    return;
	}

	// Paranoia
	if (! uid.equals(userID)) {
	    log.warning("Got UID " + userID + " expected " + uid);
	    return;
	}

	if (characterRef == null) {
	    log.severe("Channel left, but no character assigned");
	    return;
	}

	// Let my Character handle it
	characterRef.peek(SimTask.getCurrent()).leftChannel(channelID);
    }

    public void userDataReceived(UserID userID, ByteBuffer data) {
	log.finer("User " + userID + " direct data");

	// Paranoia
	if (! uid.equals(userID)) {
	    log.warning("Got UID " + userID + " expected " + uid);
	    return;
	}

	if (characterRef == null) {
	    log.severe("Channel data, but no character assigned");
	    return;
	}

	// Let my Character handle it
	characterRef.peek(SimTask.getCurrent()).dataReceived(data);
    }

    public void dataArrivedFromChannel(ChannelID channelID, UserID userID,
	    ByteBuffer data) {
	// no-op
    }
}
