/*
 * $Id$
 *
 * Copyright 2006 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * -Redistributions of source code must retain the above copyright
 * notice, this  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF  OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

package com.sun.gi.apps.jnwn;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A Melee manages combat among two or more Characters.
 *
 * @author  James Megquier
 * @version $Rev$, $Date$
 */
public class Melee implements GLO {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger("com.sun.gi.apps.jnwn");

    private GLOReference<Area>            area;
    private Set<GLOReference<Character>>  fighters;
    private GLOReference<Melee>           thisRef;

    public static GLOReference<Melee> create() {
	SimTask task = SimTask.getCurrent();
	Melee templateMelee = new Melee();
	GLOReference<Melee> ref = task.createGLO(templateMelee);
	ref.get(task).boot(ref);
	return ref;
    }

    public void addCharacter(Character ch) {
	SimTask task = SimTask.getCurrent();
	fighters.add(ch.getReference());
	// XXX
    }

    protected Melee() {
	fighters = new HashSet<GLOReference<Character>>();
    }

    protected void boot(GLOReference<Melee> ref) {
	thisRef = ref;
    }

/*
    protected String getLoadModuleMessage() {
	// XXX precompute this message for this area
	StringBuffer sb = new StringBuffer();
	sb.append("load module \"")
	  .append(moduleName)
	  .append("\"");
	return sb.toString();
    }

    protected String getLoadAreaMessage() {
	StringBuffer sb = new StringBuffer();
	sb.append("load area \"")
	  .append(areaName)
	  .append("\"");
	return sb.toString();
    }

    protected String getAddCharacterMessage(Character ch) {
	PlayerInfo info = map.get(ch.getReference());
	StringBuffer sb = new StringBuffer();
	sb.append("add character ")
	  .append(ch.getCharacterID())
	  .append(" ")
	  .append(info.pos.x)
	  .append(" ")
	  .append(info.pos.y)
	  .append(" ")
	  .append(info.pos.z)
	  .append(" ")
	  .append(info.pos.heading)
	  .append(" ")
	  .append(info.model)
	  .append(" ")
	  .append(ch.getName());
	return sb.toString();
    }

    protected String getTeleportMessage(Character ch) {
	PlayerInfo info = map.get(ch.getReference());
	StringBuffer sb = new StringBuffer();
	sb.append("move ")
	  .append(ch.getCharacterID())
	  .append(" ")
	  .append(info.pos.heading)
	  .append(" ")
	  .append(info.pos.x)
	  .append(" ")
	  .append(info.pos.y)
	  .append(" ")
	  .append(info.pos.z);
	return sb.toString();
    }

    protected String getRemoteWalkMessage(Character ch) {
	PlayerInfo info = map.get(ch.getReference());
	StringBuffer sb = new StringBuffer();
	sb.append("walk ")
	  .append(ch.getCharacterID())
	  .append(" ")
	  .append(info.lastPos.heading) // XXX: pos or lastPos?
	  .append(" ")
	  .append(info.pos.x)
	  .append(" ")
	  .append(info.pos.y)
	  .append(" ")
	  .append(info.pos.z);
	return sb.toString();
    }

    protected String getRemoveCharacterMessage(Character ch) {
	StringBuffer sb = new StringBuffer();
	sb.append("remove character ")
	  .append(ch.getCharacterID());
	return sb.toString();
    }

    protected String getPreexistingCharacterMessage() {
	return "add character -1 15 20 0 2.356195 nw_troll joey";
    }

    protected void sendCurrentCharactersTo(Character ch) {
	SimTask task = SimTask.getCurrent();

	// XXX debugging; pre-existing fake character
	//sendToCharacter(ch, getPreexistingCharacterMessage());

	for (GLOReference<Character> ref : map.keySet()) {
	    sendToCharacter(ch, getAddCharacterMessage(ref.peek(task)));
	}
    }

    protected void broadcast(String message) {
	log.finest("Broadcasting `" + message + "' on " + channel);
	ByteBuffer buf = ByteBuffer.wrap(message.getBytes());
	buf.position(buf.limit());
	SimTask.getCurrent().broadcastData(channel, buf, true);
    }

    protected void sendToCharacter(Character ch, String message) {
	log.finest("Sending `" + message + "' to " + ch.getUID());
	ByteBuffer buf = ByteBuffer.wrap(message.getBytes());
	buf.position(buf.limit());
	SimTask.getCurrent().sendData(channel, ch.getUID(), buf, true);
    }
*/
    /**
     * Handle data that was sent directly to the server.
     */
/*
    public void dataReceived(Character ch, ByteBuffer data) {
	log.finer("Direct data from character " + ch.getCharacterID());

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String text = new String(bytes);

	log.finest("(" + text + ")");
	String[] tokens = text.split("\\s+");
	if (tokens.length == 0) {
	    log.warning("empty message");
	    return;
	}

	handleClientCommand(ch, tokens);
    }

    protected PlayerInfo getStartInfo() {
	// XXX negating startFacing; bug? -jm
	return new PlayerInfo(System.currentTimeMillis(),
	    startX, startY, startZ,
	    -startFacing, 0.0f, startModel);
    }

    protected void handleClientCommand(Character ch, String[] args) {
	if ("walk".equals(args[0])) {
	    handleWalk(ch, args);
	} else if ("attack".equals(args[0])) {
	    handleAttack(ch, args);
	} else if ("flee".equals(args[0])) {
	    handleFlee(ch, args);
	} else {
	}
    }

    protected void handleWalk(Character ch, String[] args) {
	SimTask task = SimTask.getCurrent();

	final int EXPECTED_ARGS = 7;

	if (args.length != EXPECTED_ARGS) {
	    log.warning("Walk requires " + EXPECTED_ARGS +
		" args, got " + args.length);
	    return;
	}

	PlayerInfo info = map.get(ch.getReference());

	if (info == null) {
	    log.severe("No player-info for character " + ch.getCharacterID());
	    return;
	}

	if (info.melee != null) {
	    log.fine("Character " + ch.getCharacterID() + " is fighting");
	    return;
	}

	info.doWalk(args);

	if (detectWalkCheat(info)) {
	    log.info("Character " + ch.getCharacterID() + " is a cheater!");
	    broadcast(getTeleportMessage(ch));
	} else {
	    broadcast(getRemoteWalkMessage(ch));
	}
    }

    protected boolean detectWalkCheat(PlayerInfo info) {
	return (detector != null) && detector.detectWalkCheat(info);
    }

    protected void handleAttack(Character ch, String[] args) {
	SimTask task = SimTask.getCurrent();
	log.warning("Attack not supported yet");
    }

    protected void handleFlee(Character ch, String[] args) {
	SimTask task = SimTask.getCurrent();
	log.warning("Flee not supported yet");
    }


    // SimChannelMembershipListener methods

    public void characterJoined(Character ch) {
	log.fine("Character " + ch.getCharacterID() + " joined " + areaName);

	sendToCharacter(ch, getLoadModuleMessage());
	sendToCharacter(ch, getLoadAreaMessage());
	sendCurrentCharactersTo(ch); // @@ must come before map.put
	map.put(ch.getReference(), getStartInfo());
	broadcast(getAddCharacterMessage(ch));
    }

    public void characterLeft(Character ch) {
	log.fine("Character " + ch.getCharacterID() + " left " + areaName);
	map.remove(ch.getReference());
	broadcast(getRemoveCharacterMessage(ch));
    }
*/
}
