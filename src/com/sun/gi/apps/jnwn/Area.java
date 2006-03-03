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

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.HashMap;
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
    private float      startX;
    private float      startY;
    private float      startZ;
    private float      startFacing;
    private String     startModel = "nw_troll";
    private ChannelID  channel;
    private GLOReference<Area>  thisRef;
    private HashMap<GLOReference<Character>, PlayerInfo>  map;

    public static GLOReference<Area> create() {
	SimTask task = SimTask.getCurrent();
	//GLOReference<Area> ref = task.createGLO(new Area(name), gloname);
	Area templateArea = null;
	try {
	    templateArea = AreaFactory.create();
	} catch (Exception e) {
	    log.warning("Creating default area");
	    templateArea = new Area();
	    e.printStackTrace();
	}
	String gloname = "Area:" + templateArea.getName();
	GLOReference<Area> ref = task.createGLO(templateArea, gloname);
	ref.get(task).boot(ref);
	return ref;
    }

    public String getName() {
	return areaName;
    }

    public void addCharacter(Character ch) {
	SimTask task = SimTask.getCurrent();
	task.join(ch.getUID(), channel);
    }

    protected Area() {
	this("FooModule", "foo", 0f, 0f, 0f, 0f);
    }

    protected Area(String moduleName,
		   String areaName,
		   float  startX,
		   float  startY,
		   float  startZ,
		   float  startFacing) {
	this.moduleName  = moduleName;
	this.areaName    = areaName;
	this.startX      = startX;
	this.startY      = startY;
	this.startZ      = startZ;
	this.startFacing = startFacing;

	map = new HashMap<GLOReference<Character>, PlayerInfo>();

	SimTask task = SimTask.getCurrent();
	String channelName = "Area:" + areaName;
	channel = task.openChannel(channelName);
	task.lock(channel, true);
    }

    protected void boot(GLOReference<Area> ref) {
	thisRef = ref;
    }

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

    protected void sendCurrentCharactersTo(Character ch) {
	SimTask task = SimTask.getCurrent();
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

    /**
     * Handle data that was sent directly to the server.
     */
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
	return new PlayerInfo(System.currentTimeMillis(),
	    startX, startY, startZ,
	    startFacing, 0.0f, startModel);
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

	if (args.length != 7) {
	    log.warning("Walk requires 7 args, got " + args.length);
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
	// FIXME - dummy impl for testing
	if (info.pos.distance(info.lastPos) > 5.0f) {
	    Pos last_valid_position = info.lastPos.clone();
	    info.pos = last_valid_position;
	    return true;
	}
	return false;
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

    //

    static class Pos implements Serializable, Cloneable {

	private static final long serialVersionUID = 1L;

	public long  timestamp;
	public float x;
	public float y;
	public float z;
	public float heading; // in radians
	public float velocity;

	public Pos(long timestamp, float x, float y, float z,
		float heading, float velocity) {
	    update(timestamp, x, y, z, heading, velocity);
	}

	public void update(long timestamp, float x, float y, float z,
		float heading, float velocity) {
	    this.timestamp = timestamp;
	    this.x = x;
	    this.y = y;
	    this.z = z;
	    this.heading = heading;
	    this.velocity = velocity;
	}

	public Pos clone() {
	    try {
		return (Pos) super.clone();
	    } catch (CloneNotSupportedException e) {
		return null;
	    }
	}

	public float distance(final Pos o) {
	    final float dX = x - o.x;
	    final float dY = y - o.y;
	    final float dZ = z - o.z;
	    return (float) Math.sqrt((dX * dX) + (dY * dY) + (dZ * dZ));
	}
    }

    static class PlayerInfo implements Serializable {

	private static final long serialVersionUID = 1L;

	public Pos    pos;
	public Pos    lastPos;
	public String model;
	public Object melee;

	public PlayerInfo(long timestamp, float x, float y, float z,
		float heading, float velocity, String model) {
	    this.model = model;
	    this.pos = new Pos(timestamp, x, y, z, heading, velocity);
	    this.lastPos = pos.clone();
	    this.melee = null;
	}

	public void doWalk(String[] args) {
	    long  newTime     = Long.parseLong(args[1]);
	    float newX        = Float.parseFloat(args[2]);
	    float newY        = Float.parseFloat(args[3]);
	    float newZ        = Float.parseFloat(args[4]);
	    float newFacing   = Float.parseFloat(args[5]);
	    float newVelocity = Float.parseFloat(args[6]);
	    lastPos = pos.clone();
	    pos.update(newTime, newX, newY, newZ, newFacing, newVelocity);
	}
    }
}
