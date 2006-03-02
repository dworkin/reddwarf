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

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.logging.Logger;

/**
 * A JNWN Area represents a shared, multicharacter subspace in NWN.
 *
 * @author  James Megquier
 * @version $Rev$, $Date$
 */
public class Area implements GLO {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger("com.sun.gi.apps.jnwn");

    private String     moduleName;
    private String     areaName;
    private ChannelID  channel;
    private GLOReference<Area>  thisRef;
    private LinkedList<GLOReference<Character>>  characters;

    public static GLOReference create(String name) {
	SimTask task = SimTask.getCurrent();
	String gloname = "Area:" + name;
	GLOReference<Area> ref = task.createGLO(new Area(name), gloname);
	ref.get(task).boot(ref);
	return ref;
    }

    public void addCharacter(Character character) {
	SimTask.getCurrent().join(character.getUID(), channel);
    }

    protected Area(String name) {

	moduleName = "FooModule"; // XXX
	areaName = name;
	characters = new LinkedList<GLOReference<Character>>();

	SimTask task = SimTask.getCurrent();
	String channelName = "Channel:Area:" + areaName;
	channel = task.openChannel(areaName);
	task.lock(channel, true);
    }

    protected void boot(GLOReference<Area> ref) {
	thisRef = ref;
    }

    protected String getLoadModuleMessage() {
	// XXX precompute this message for this area
	StringBuffer sb = new StringBuffer();
	sb.append("load module ")
	  .append(moduleName);
	return sb.toString();
    }

    protected String getLoadAreaMessage() {
	StringBuffer sb = new StringBuffer();
	sb.append("load area ")
	  .append(areaName);
	return sb.toString();
    }

    protected String getAddCharacterMessage(Character ch) {
	StringBuffer sb = new StringBuffer();
	sb.append("add character ")
	  .append(ch.getCharacterID())
	  .append(" ")
	  .append(ch.getX())
	  .append(" ")
	  .append(ch.getY())
	  .append(" ")
	  .append(ch.getZ())
	  .append(" ")
	  .append(ch.getDirection())
	  .append(" ")
	  .append(ch.getModel())
	  .append(" ")
	  .append(ch.getName());
	return sb.toString();
    }

    protected String getRemoveCharacterMessage(Character ch) {
	StringBuffer sb = new StringBuffer();
	sb.append("remove character ")
	  .append(ch.getCharacterID());
	return sb.toString();
    }

    protected void sendCurrentCharactersTo(Character ch) {
	SimTask task = SimTask.getCurrent();
	for (GLOReference<Character> ref : characters) {
	    sendToCharacter(ch, getAddCharacterMessage(ref.peek(task)));
	}
    }

    protected void handleWalk(Character ch, String[] tokens) {
	SimTask task = SimTask.getCurrent();
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

    /**
     * Handle data that was sent directly to the server.
     */
    public void dataReceived(Character ch, ByteBuffer data) {
	log.finer("Direct data from character " + ch.getCharacterID());

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String text = new String(bytes);

	log.finest("dataReceived: (" + text + ")");
	String[] tokens = text.split("\\s+");
	if (tokens.length == 0) {
	    log.warning("empty message");
	    return;
	}

	//GLOReference<Character> characterRef = Character.getRef(uid);
	//handleResponse(characterRef, tokens);
    }

    // SimChannelMembershipListener methods

    public void characterJoined(Character ch) {
	log.fine("Character " + ch.getCharacterID() + " joined " + areaName);

	sendToCharacter(ch, getLoadModuleMessage());
	sendToCharacter(ch, getLoadAreaMessage());
	sendCurrentCharactersTo(ch); // @@ must come before characters.add
	characters.add(ch.getReference());
	broadcast(getAddCharacterMessage(ch));
    }

    public void characterLeft(Character ch) {
	log.fine("Character " + ch.getCharacterID() + " left " + areaName);
	characters.remove(ch.getReference());
	broadcast(getRemoveCharacterMessage(ch));
    }
}
