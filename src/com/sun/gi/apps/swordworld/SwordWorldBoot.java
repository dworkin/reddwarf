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


package com.sun.gi.apps.swordworld;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;

import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;

/**
 * The bootstrap class for the toy text-mud minimal SGS example app
 * SwordWorld.  <p>
 *
 * The Boot method is invoked by the system when an SGS stack starts
 * up.  <p>
 *
 * It implements {@link SimBoot} so it can be a boot object.  It
 * implements {@link SimUserListener} so it can handle users joining
 * and leaving the system.</p>
 *
 * @author Jeff Kesselman
 *
 * @version 1.0
 */
public class SwordWorldBoot
	implements SimBoot<SwordWorldBoot>, SimUserListener {

    /**
     * All GLOs should define a <code>serialVersionUID</code> because
     * they are serialized.  This turns off version checking so we can
     * change the class and still load old data that might already be
     * in the ObjectStore.
     */
    private static final long serialVersionUID = 1L;
    
    /**
     * This field holds a GLOReference to the one room in our
     * minimal MUD world.  
     * 
     * All fields on a GameLogicObject (GLO) that refer to other GLOs
     * must be GLOReferences.  If we just said "Room roomRef" then
     * we would end up with a copy of the room as part of the state 
     * of this GLO rather then a reference to the other GLO. 
     */
    private GLOReference<Room> roomRef;

    /**
     * Currently all data sent from the server to users must be sent
     * on a channel. (This is likely to change  in the next major
     * version of the API.)
     * 
     * This field holds the ID of a channel that is used to send data 
     * back to all the users. 
     */
    private ChannelID appChannel;
    
    /**
     * We get informed of who events regarding users such as 
     * userJoined and userLeft refer to by an identifying UserID. <p>
     * 
     * This map lets us retrieve the Player GLO assoigned to that
     * User.  For more information, see the Player.java file.
     */
    private Map<UserID,GLOReference<Player>> uidToPlayerRef =
	    new HashMap<UserID,GLOReference<Player>>();

    /**
     * This is the boot method which gets called when the SGS stack
     * starts up.
     * 
     * Fundementally we get two paremeters in the boot call.  The
     * first is of type GLOReference<SwordWorldBot> and is a
     * GLOReference to this boot object.  (You almost always need it
     * here so for convenience we pass it in.)
     * 
     * The second is a boolean called "firstBoot".  When the SGS stack
     * is started up the stack looks for an existing boot object.  if
     * the boot object is there then it just gets it and calls boot()
     * on it with firstTime set to false.  If it is *not* there, it
     * creates it and calls boot() with firstTime set to true.
     * 
     * This means that you can tell if the ObjectStore is empty by the
     * value of firstTime.  If the ObjectStore is empty, you will want
     * to do set up and create the other GLOs your app requires.  is
     * not there
     * 
     * @see
     * com.sun.gi.logic.SimBoot#boot(com.sun.gi.logic.GLOReference,
     * boolean)
     */

    public void boot(GLOReference<? extends SwordWorldBoot> thisGLO,
	    boolean firstBoot) {

	/*
	 * The SimTask object is your window back into the system. 
	 * All actions on the system such as creating or destroying
	 * GLOs, registering event handlers, and so forth require you
	 * to call methods on the SimTask.
	 *
	 * A SimTask object refers to the task that is performing the
	 * access to this object and therefore it is ONLY valid during
	 * the execution of the Task in which SimTask.getCurrent() is
	 * called.  For this reason you should NEVER store a SimTask
	 * object in a GLO field unless that field is marked transient
	 * and thus not saved past the end of the task.
	 */
	SimTask simTask = SimTask.getCurrent();

	if (firstBoot){

	    /*
	     * firstBoot is true, so we need to create our game GLOs. 
	     * In order to create a GLO, you first create a template
	     * object.  You then pass that template into
	     * simTask.createGLO().
	     */

	    Room roomTemplate = new Room("A big brightly lit room.");
	    roomRef = simTask.createGLO(roomTemplate);

	    /*
	     * IMPORTANT:  The template is *not* the GLO.  If you want
	     * to make changes to the fields of the GLO you MUST get
	     * the GLO itself with a GLOReference.get().  Changing
	     * fields on the template after the createGLO has been
	     * called will have NO effect on the GLO itself.
	     */

	    Room roomGLO = roomRef.get(simTask);

	    /*
	     * Now that we have a room GLO, let's make a sword GLO
	     */
	    RoomObject sword = new RoomObject("A shiny new sword");
	    GLOReference<RoomObject> swordRef = simTask.createGLO(sword);

	    /*
	     * We are going to add the sword to the inventory list of
	     * the room.  Note that we do *not* need to get() the
	     * swordGLO itself for this.  Since we never want to store
	     * Java refernces to GLOs in the fields of other GLOs but
	     * only GLOReferences, what we have is all we need.
	     */
	    roomGLO.addToInventory(swordRef);			
	}

	/*
	 * A channel is required in the current version of the API in
	 * order to send data back to users.  This code and the client
	 * code have agreed apriori to call the one channel we will
	 * use "GAMECHANNEL".  By opening the channel both here and in
	 * the client, we establish a data path from client to server.
	 */
	appChannel = simTask.openChannel("GAMECHANNEL");

	/*
	 * Finally, we want to know when users log in to the server so
	 * we can hook them up to a Player object.  Therefore, we
	 * register ourselves as the SimUserListener.
	 */
	simTask.addUserListener(thisGLO);		
	
    }

    /**
     * This callback is called by the system when a user logs into the
     * game.  It takes 2 parameters.  The first is the UserID of the
     * user.  IMPORTANT:  A UserID is really a session ID and is valid
     * until the current session ends (the user is disconnected from
     * the system). The next time the same user logs in he or she will
     * get a *different* UserID. <p>
     * 
     * The second parameter is a Subject.  This is a JDK class and can
     * be looked up in the Sun J2SE API docs.  It is bascially a
     * container for ids (called Principals) and permissions. <p>
     * 
     * When the user logs in, the Validator for this game sets all
     * this information in the Subject.  By convention, the first
     * Principal always contains a String denoting the user's login
     * name.  @see
     * com.sun.gi.logic.SimUserListener#userJoined(com.sun.gi.comm.routing.UserID,
     * javax.security.auth.Subject)
     */
    public void userJoined(UserID uid, Subject subject) {

	/*
	 * first we get the first principal
	 */
	Set<Principal> principles = subject.getPrincipals();
	Principal principal = principles.iterator().next();

	/*
	 * next we create a name for a named GLO by combining the
	 * prefix "player_" with the player's login name as
	 * returned by the first principal.
	 * For any given login this will always result in the same
	 * name.
	 */
	String playerName = "player_"+principal.getName();
	SimTask simTask = SimTask.getCurrent();

	/*
	 * The following "three phase lookup" pattern is a common
	 * GLO pattern and should be used whenever you want to
	 * create a named GLO if it doesn't already exist:
	 * First we ask the system if a named GLO already exists that
	 * has this name.
	 */
	GLOReference<Player> playerRef = simTask.findGLO(playerName);
	if (playerRef == null){

	    /*
	     * If the findGLO returns null then there was no named GLO
	     * with that name, so we attempt to create it with a new
	     * instance of Player.
	     *
	     * Note the design pattern of creating the object for
	     * which we want to create a GLO inside the call to
	     * createGLO.  This prevents a reference to that object
	     * from "leaking" into our own code, where we might
	     * accidently use it.  GLO objects should ONLY be
	     * referenced via a GLOReference.
	     */
	    playerRef = simTask.createGLO(
		    new Player(playerName, appChannel));
	    if (playerRef == null) {

		/*
		 * Because of the concurrent nature of the system, it
		 * is possible that we ended up in a race with another
		 * task to create this object.  We both tried to get
		 * it at the same time, then since it didn't exist we
		 * both tried to create it.  The system enforces that
		 * a named object can only be created if it doesn't
		 * exist, and only once if two tasks try to create at
		 * the same time.  The task the system denied creation
		 * to gets a null reference back from the createGLO
		 * call.  In this case, the other task created it so
		 * now we can safely just ask for it by name again,
		 * knowing it now exists.
		 *
		 * Note that there is one condition under which this
		 * assumption is not true, and that's if another task
		 * could have destroyed it between when we called
		 * createGLO and the second attempt to find.  In this
		 * app we know that will never happen, but a more
		 * general solution would actually wrap a while
		 * (playerRef == null) around this and keep trying
		 * until it either created or found the GLO.
		 */
		playerRef = simTask.findGLO(playerName);
	    }
	}

	/*
	 * we are going to add the player to the room's
	 * player list.  For that we need the room GLO so
	 * we ask the GLOReference to the room to get the GLO
	 * itself for us.
	 */
	Room roomGLO= roomRef.get(simTask);
	roomGLO.addPlayer(playerRef);

	/*
	 * When we get a userLeft message we are going to want to
	 * remove the player from the room.  In order to do that we
	 * will need to get the GLOReference to the player listening
	 * to that UID.  We do that by storing that relationship in a
	 * map for later lookup.
	 */
	uidToPlayerRef.put(uid, playerRef);

	/*
	 * Finally we tell the system that we want the playerGLO we
	 * created to receive UserDataListener events reating to the
	 * user we hav associated with that Player GLO.
	 */
	simTask.addUserDataListener(uid,playerRef);
        String out = "Sworld welcomes "+playerName;
        ByteBuffer outbuff = ByteBuffer.allocate(out.length());
        outbuff.put(out.getBytes());
        simTask.broadcastData(appChannel, outbuff, true);
    }

    /**
     * This callback gets called to tell us a User's session has ended
     * because they are disconnected/logged out from the system.
     *
     * @param uid the user to remove
     *
     * @see
     * com.sun.gi.logic.SimUserListener#userLeft(com.sun.gi.comm.routing.UserID)
     */
    public void userLeft(UserID uid) {
	SimTask simTask = SimTask.getCurrent();

	/*
	 * We fetch the GLOReference to the player who was listening
	 * to SimUserData events for this user from the map where we
	 * stored that information in userJoined and then remove it
	 * from the map because it is going away.
	 */
	GLOReference<Player> playerRef = uidToPlayerRef.remove(uid);

	/*
	 * We fetch the roomGLO so we can tell it he left, and remove
	 * the Player GLO from the Room's list of players.
	 */
	Room roomGLO= roomRef.get(simTask);
	roomGLO.removePlayer(playerRef);		
    }
}
