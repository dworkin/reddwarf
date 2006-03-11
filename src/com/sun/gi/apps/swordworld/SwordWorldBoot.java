
package com.sun.gi.apps.swordworld;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;

/**
 *
 * <p>Title: SwordWorldBoot.java</p>
 * <p>Description:</p>
 * <p>This class is the boot strap class for the toy text-mud 
 * minimal SGS example app SwordWorld.  The Boot method gets run by
 * the system when an SGS stack starts up.</p>
 * <p>It impelments SimBoot so it can be a boot object.  It implements
 * SimUserListener so it can handle users joining and leaving the system.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class SwordWorldBoot implements SimBoot<SwordWorldBoot>, SimUserListener {

	/**
	 * Effectively disable serial version checking.  This allows
	 * us to modify this class without invalidating the copy that 
	 * might already be stored in the ObjectStore.  
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
	 * userJoined and userLeft refer to by an identifying UserID.
	 * 
	 * This map lets us retrieve the Player GLO assoigned to that
	 * User.  For more information, see the Player.java file.
	 */
	private Map<UserID,GLOReference<Player>> uidToPlayerRef =
		new HashMap<UserID,GLOReference<Player>>();

	/* (non-Javadoc)
	 * This is the boot method which gets called when the SGS stack starts
	 * up.  The rather arcane parameters have to do with how Java generics work.
	 * Im not going to try to explain them here.  If you want to udnerstand
	 * everything its doing, google "Java Generics".
	 * 
	 * Fundementally we get two paremeters in the boot call.  The first
	 * is of type GLOReference<SwordWorldBot> and is a GLOReference to
	 * this boot object.  (You almost always need it here so for convenience
	 * we pass it in.)  
	 * 
	 * The second is a boolean called "firstTime.  When the SGS stack is started
	 * up the stack looks for an existing boot object.  if the boot object
	 * is there then it just gets it and calls boot() on it with
	 * firstTime set to false.  If it is *not* there, it creates it
	 * and calls boot() with firstTime set to true.
	 * 
	 * This means that you can tell if the ObjectStore is empty by the
	 * value of firstTime.  If the ObjectStore is empty, you will want to
	 * do set up and create the other GLOs your app requires.
	 * is not there 
	 * 
	 * @see com.sun.gi.logic.SimBoot#boot(com.sun.gi.logic.GLOReference, boolean)
	 */
	public void boot(GLOReference<? extends SwordWorldBoot> thisGLO, boolean firstBoot) {
		// The SimTask object is your window back into the system.
		// All actions on the system such as creating or destroying
		// GLOs, registering event handlers, and so forth require
		// you to call methods on the SimTask.
		//
		// A SimTask object is a lot like the Graphics object in Java.
		// It is ONLY valid during the execution of the Task in which
		// SimTask.getCurrent() is called. For this reason you should
		// *never* try to store a SimTask object in a GLO field unless
		// that field is marked Transient and thus not saved past the
		// end of the task.
		SimTask simTask = SimTask.getCurrent();
		if (firstBoot){
			// firstBoot is true, so we need to create our game
			// GLOs.
			// In order to create a GLO, you first create a template
			// object.  You then pass that template into simTask.createGLO()			
			Room roomTemplate = new Room("A big brightly lit room.");
			roomRef = simTask.createGLO(roomTemplate);
			//IMPORTANT: The template is *not* the GLO.  If you want
			//to make changes to the fields of the GLO you *must*
			//get the GLO itself with a GLOReference.get().  
			// Changing fields on the template after the createGLO has been
			// called will have NO effect on the GLO itself.
			Room roomGLO = (Room)roomRef.get(simTask);
			//Now we have a room GLO, lets make a sword GLO
			RoomObject sword = new RoomObject("A shiney new sword");
			GLOReference<RoomObject> swordRef = simTask.createGLO(sword);
			// We are going to add the sword to the inventory list
			// of the room.  Note that we do *not* need to get() the
			// swordGLO itself for this.  Since we never want to store 
			// Java refernces to GLOs in the fields of other GLOs but
			// only GLOReferences, what we have is all we need.
			roomGLO.addToInventory(swordRef);			
		}
		// A channel is required in the current version of the API
		// in order to send data back to users.  This code and the
		// client code have agreed apriori to call the one channel
		// we will use "GAMECHANNEL".  By opening the channel both here
		// and in the client, we establish a data path from client to
		// server.
		appChannel = simTask.openChannel("GAMECHANNEL");
		// Finally, we want to know when users log in to the server
		// so we can hook them up to a Player object.  Therefor,
		// we register ourselves as the SimUserListener.
		simTask.addUserListener(thisGLO);		
		
	}

	/**
	 * This callback is called by the system when a user logs into the
	 * game.  It takes 2 parameters.  The first is the UserID of the user.
	 * IMPORTANT: A UserID is really a session ID and is valid until the current session
	 * ends (the user is disconnected from the system.)  The next time
	 * the same user logs in he or she will get a *different* UserID.
	 * 
	 * The second parameter is a Subject.  This is a JDK class and
	 * can be looked up in the Sun J2SE API docs.  It is bascially
	 * a container for ids (called Principals) and permissions.
	 * 
	 * When the user logs in, the Validator for this game sets all
	 * this information in the Subject.  By convention, the first
	 * Principal always contains a String denoting the user's login
	 * name.
	 * @see com.sun.gi.logic.SimUserListener#userJoined(com.sun.gi.comm.routing.UserID, javax.security.auth.Subject)
	 */
	public void userJoined(UserID uid, Subject subject) {
		// first we get the first princpal
		Set<Principal> principles = subject.getPrincipals();
        Principal principal = principles.iterator().next();
        // next we create a name for a named GLO by combining the
        // prefix "player_" with the player's login name as
        // returned by the first principal.
        // For any given login this will always result in the same
        // name.
        String playerName = "player_"+principal.getName();
        SimTask simTask = SimTask.getCurrent();
        // The following "three phase lookup" pattern is a common
        // GLO pattern and shoudl be used whenever you want to
        // create a named GLO if it doesnt already exist:
        //First we ask the system if a named GLO already exists that
        //has this name.
        GLOReference<Player> playerRef = simTask.findGLO(playerName);
		if (playerRef == null){
			// if the findGLO returns null then there was no named
			// GLO with that name, so we want to create it.
			// In order to doso we first create a template object
			Player playerTemplate = 
				new Player(principal.getName(),appChannel);
			// now we attempt to create the GLO
			playerRef=simTask.createGLO(playerTemplate);
			if (playerRef==null){
				// because of the parallel nature of the system,
				// it is possible that we ended up in a race with
				// another task to create this object.
				// We both tried to get it at the same time, then
				// sicne ti didnt exist we both tried to create it.
				// The system enforces that a named object can only be
				// created if it doesnt exist, and only once if two
				// tasks try to create at the same time.
				// The task the system *denied* creation to gets a 
				// null reference back from the createGLO call.
				// In this case, the oterh task created it so now we
				// can safely just ask for it by name again, knowing
				// it now exists.
				playerRef = simTask.findGLO(playerName);
				// Note that there is one condition under which
				// this assumption is not true, and thats if another
				// task coudl have destroyed it between when we called
				// createGLO and the second attempt to find.
				// In this app we know that will never happen, but 
				// a more general solution would actually wrap a
				// while (playerRef==null) around this and
				// keep trying til it either created or found the GLO.
			}
		}
		// we are going to add the player to the room's
		// player list.  For that we need the room GLO so
		// we ask the GLOReference to the room to get the GLO
		// itself for us.
		Room roomGLO= roomRef.get(simTask);
		// now we add the player, see Room.java for more details.
		roomGLO.addPlayer(playerRef);
		// When we get a userLeft message we are going to want to
		//remove the player from the room.  In order to do that we
		// will need to get the GLOReference to the player listening
		// to that UID.  We do that by storing that relationship in
		// a map for later lookup.
		uidToPlayerRef.put(uid,playerRef);
		//finally we tell the system that we want the playerGLO
		//we created to receive UserDataListener events reating
		// to the user we hav associated with that Player GLO.
		simTask.addUserDataListener(uid,playerRef);
	}


	/**
	 * This callback gets called to tell us a User's session has
	 * ended.  (they are disconnected/logged out from the system.)
	 * @see com.sun.gi.logic.SimUserListener#userLeft(com.sun.gi.comm.routing.UserID)
	 */
	public void userLeft(UserID uid) {
		// We will need a simTask to do some work, so we get the
		// current one
		SimTask simTask = SimTask.getCurrent();
		//we fetch the GLOReference to the player who was listening
		//to SimUserData events for this user from the map where
		//we stored that information in userJoined and then
		//remove him from the map as he is going away
		GLOReference<Player> playerRef = uidToPlayerRef.remove(uid);
		//we fetch the roomGLO so we can tell it he left.
		Room roomGLO= roomRef.get(simTask);
		//we remove the Player GLO from the Room's list of players.
		roomGLO.removePlayer(playerRef);		
	}

}
