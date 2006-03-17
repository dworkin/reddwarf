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
    private CheatDetector detector;

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
	this("FooModule", "foo", 0f, 0f, 0f, 0f, null);
    }

    protected Area(String moduleName,
		   String areaName,
		   float  startX,
		   float  startY,
		   float  startZ,
		   float  startFacing,
		   CheatDetector detector) {
	this.moduleName  = moduleName;
	this.areaName    = areaName;
	this.startX      = startX;
	this.startY      = startY;
	this.startZ      = startZ;
	this.startFacing = startFacing;
	this.detector    = detector;

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
}
