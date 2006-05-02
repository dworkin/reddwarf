/*
 * Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
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
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd.
 * 
 * Products covered by and information contained in this service manual
 * are controlled by U.S. Export Control laws and may be subject to the
 * export or import laws in other countries. Nuclear, missile, chemical
 * biological weapons or nuclear maritime end uses or end users, whether
 * direct or indirect, are strictly prohibited. Export or reexport to
 * countries subject to U.S. embargo or to entities identified on U.S.
 * export exclusion lists, including, but not limited to, the denied
 * persons and specially designated nationals lists is strictly
 * prohibited.
 * 
 * DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED
 * CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED
 * WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NON-INFRINGEMENT, ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH
 * DISCLAIMERS ARE HELD TO BE LEGALLY INVALID.
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
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 * licenciée exlusivement par X/Open Company, Ltd.
 * 
 * see above Les produits qui font l'objet de ce manuel d'entretien et
 * les informations qu'il contient sont regis par la legislation
 * americaine en matiere de controle des exportations et peuvent etre
 * soumis au droit d'autres pays dans le domaine des exportations et
 * importations. Les utilisations finales, ou utilisateurs finaux, pour
 * des armes nucleaires, des missiles, des armes biologiques et
 * chimiques ou du nucleaire maritime, directement ou indirectement,
 * sont strictement interdites. Les exportations ou reexportations vers
 * des pays sous embargo des Etats-Unis, ou vers des entites figurant
 * sur les listes d'exclusion d'exportation americaines, y compris, mais
 * de maniere non exclusive, la liste de personnes qui font objet d'un
 * ordre de ne pas participer, d'une facon directe ou indirecte, aux
 * exportations des produits ou des services qui sont regi par la
 * legislation americaine en matiere de controle des exportations et la
 * liste de ressortissants specifiquement designes, sont rigoureusement
 * interdites.
 * 
 * LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 * DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT
 * EXCLUES, DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS
 * NOTAMMENT TOUTE GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A
 * L'APTITUDE A UNE UTILISATION PARTICULIERE OU A L'ABSENCE DE
 * CONTREFACON.
 */

package com.sun.gi.comm.users.server.impl;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.protocol.TransportProtocol;
import com.sun.gi.comm.users.protocol.TransportProtocolServer;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;
import com.sun.gi.comm.users.protocol.impl.BinaryPktProtocol;
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.comm.users.validation.UserValidator;

public class SGSUserImpl implements SGSUser, TransportProtocolServer {
	static Logger log = Logger.getLogger("com.sun.gi.comm.users");

	private Router router;

	private Subject subject;

	private UserID userID;

	private Map<ChannelID, SGSChannel> channelMap = new HashMap<ChannelID, SGSChannel>();

	private TransportProtocol transport;

	private UserValidator[] validators;

	private int validatorCounter;

	private boolean connected = true;

	public SGSUserImpl(Router router, TransportProtocolTransmitter xmitter,
			UserValidator[] validators) {
		this.validators = validators;
		this.router = router;
		transport = new BinaryPktProtocol();
		transport.setTransmitter(xmitter);
		transport.setServer(this);
		try {
			userID = new UserID();
			transport.deliverServerID(UserID.SERVER_ID.toByteArray());
		} catch (InstantiationException e) {
			e.printStackTrace();
		}
	}

	public void joinedChan(SGSChannel channel) throws IOException {
		channelMap.put(channel.channelID(), channel);

		try {
			transport.deliverJoinedChannel(channel.getName(), channel
					.channelID().toByteArray());
		} catch (BufferOverflowException e) {
			log.info("User recv buffer overflow forced disconnect: userid = "
					+ userID);
			forceDisconnect(null);
		} catch (Exception e) {
			log.info("Unknown transmit exception forced disconnect: userid = "
					+ userID);
			log.info(e.getMessage());
			forceDisconnect(null);
		}
	}

	public void leftChan(SGSChannel channel) throws IOException {
		channelMap.remove(channel.channelID());

		if (connected) {
			transport.deliverLeftChannel(channel.channelID().toByteArray());
		}
	}

	public void msgReceived(byte[] channel, byte[] from, boolean reliable,
			ByteBuffer data) throws IOException {
		try {
			transport.deliverUnicastMsg(channel, from, userID.toByteArray(),
					reliable, data);
		} catch (BufferOverflowException e) {
			log.info("User recv buffer overflow forced disconnect: userid = "
					+ userID);
			forceDisconnect(null);
		} catch (Exception e) {
			log.info("Unknown transmit exception forced disconnect: userid = "
					+ userID);
			log.info(e.getMessage());
			forceDisconnect(null);
		}
	}

	public void userJoinedSystem(byte[] user) throws IOException {
		try {
			transport.deliverUserJoined(user);
		} catch (BufferOverflowException e) {
			log.info("User recv buffer overflow forced disconnect: userid = "
					+ userID);
			forceDisconnect(null);
		} catch (Exception e) {
			log.info("Unknown transmit exception forced disconnect: userid = "
					+ userID);
			log.info(e.getMessage());
			forceDisconnect(null);
		}
	}

	public void userLeftSystem(byte[] user) throws IOException {
		try {
			transport.deliverUserLeft(user);
		} catch (BufferOverflowException e) {
			log.info("User recv buffer overflow forced disconnect: userid = "
					+ userID);
			forceDisconnect(null);
		} catch (Exception e) {
			log.info("Unknown transmit exception forced disconnect: userid = "
					+ userID);
			log.info(e.getMessage());
			forceDisconnect(null);
		}
	}

	public void userJoinedChannel(byte[] channelID, byte[] user)
			throws IOException {
		try {
			transport.deliverUserJoinedChannel(channelID, user);
		} catch (BufferOverflowException e) {
			log.info("User recv buffer overflow forced disconnect: userid = "
					+ userID);
			forceDisconnect(null);
		} catch (Exception e) {
			log.info("Unknown transmit exception forced disconnect: userid = "
					+ userID);
			log.info(e.getMessage());
			forceDisconnect(null);
		}
	}

	public void userLeftChannel(byte[] channel, byte[] user) throws IOException {
		try {
			transport.deliverUserLeftChannel(channel, user);
		} catch (BufferOverflowException e) {
			log.info("User recv buffer overflow forced disconnect: userid = "
					+ userID);
			forceDisconnect(null);
		} catch (Exception e) {
			log.info("Unknown transmit exception forced disconnect: userid = "
					+ userID);
			log.info(e.getMessage());
			forceDisconnect(null);
		}
	}

	public void reconnectKeyReceived(byte[] key, long ttl) throws IOException {
		try {
			transport.deliverReconnectKey(userID.toByteArray(), key, ttl);
		} catch (BufferOverflowException e) {
			log.info("User recv buffer overflow forced disconnect: userid = "
					+ userID);
			forceDisconnect(null);
		} catch (Exception e) {
			log.info("Unknown transmit exception forced disconnect: userid = "
					+ userID);
			log.info(e.getMessage());
			forceDisconnect(null);
		}
	}

	public UserID getUserID() {
		return userID;
	}

	public void deregistered() {
		// TODO Auto-generated method stub
	}

	// TransportProtocolServer callbacks

	public void rcvUnicastMsg(boolean reliable, byte[] chanID, byte[] to,
			ByteBuffer databuff) {
		SGSChannel chan;
		try {
			chan = channelMap.get(new ChannelID(chanID));
			// FIXME: should never be NULL,
			// if it is we want an exception to figure out why
			ByteBuffer newbuff = databuff.duplicate();
			newbuff.position(newbuff.limit());
			chan.unicastData(userID, new UserID(to), newbuff, reliable);
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void rcvMulticastMsg(boolean reliable, byte[] chanID,
			byte[][] tolist, ByteBuffer databuff) {
		SGSChannel chan;
		try {
			chan = channelMap.get(new ChannelID(chanID));

			UserID[] ids = new UserID[tolist.length];
			for (int i = 0; i < ids.length; i++) {
				ids[i] = new UserID(tolist[i]);
			}
			// FIXME: should never be NULL,
			// if it is we want an exception to figure out why
			ByteBuffer newbuff = databuff.duplicate();
			newbuff.position(newbuff.limit());
			chan.multicastData(userID, ids, newbuff, reliable);
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void rcvBroadcastMsg(boolean reliable, byte[] chanID,
			ByteBuffer databuff) {
		try {
			SGSChannel chan = channelMap.get(new ChannelID(chanID));
			// FIXME: should never be NULL,
			// if it is we want an exception to figure out why
			ByteBuffer newbuff = databuff.duplicate();
			newbuff.position(newbuff.limit());
			chan.broadcastData(userID, newbuff, reliable);
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void rcvConnectReq() {
		subject = new Subject();
		try {
			if (validators == null) {
				router.registerUser(this, subject);
				transport.deliverUserAccepted(userID.toByteArray());
			} else {
				startValidation();
			}
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void startValidation() {
		validatorCounter = 0;
		validators[0].reset(subject);
		doValidationReq();
	}

	/**
	 * doValidationReq
	 */
	private void doValidationReq() {
		Callback[] cb = validators[validatorCounter].nextDataRequest();
		while ((cb == null) && (validatorCounter < validators.length)) {
			if (!validators[validatorCounter].authenticated()) { // rejected
				try {
					transport.deliverUserRejected("Validation failed");
					return;
				} catch (IOException e) {
					e.printStackTrace();
				}
				// FIXME need to disconnect the connection
			} else { // go on
				validatorCounter++;
				if (validatorCounter < validators.length) {
					validators[validatorCounter].reset(subject);
					cb = validators[validatorCounter].nextDataRequest();
				} else {
					cb = null;
				}
			}
		}
		if (cb == null) {
			// we have done them all and are authenticated
			try {
				router.registerUser(this, subject);
				transport.deliverUserAccepted(userID.toByteArray());
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			// next CBs to request
			try {
				transport.deliverValidationRequest(cb);
			} catch (UnsupportedCallbackException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void rcvValidationResp(Callback[] cbs) {
		validators[validatorCounter].dataResponse(cbs);
		doValidationReq();
	}

	public void rcvReconnectReq(byte[] user, byte[] key) {
		try {
			userID = new UserID(user);
			if (router.validateReconnectKey(userID, key)) {
				router.registerUser(this, subject);
			} else {
				transport.deliverUserRejected("Reconnect key failure");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InstantiationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	public void rcvServerMsg(boolean reliable, ByteBuffer databuff) {
		router.serverMessage(reliable, userID, databuff);
	}

	public void rcvReqLeaveChan(byte[] chanID) {
		try {
			SGSChannel chan = channelMap.get(new ChannelID(chanID));
			if (!chan.isLocked()) {
				chan.leave(this);
			} else {
				// System.out.println("Channel is locked, can't leave");
				transport.deliverChannelLocked(chan.getName(), userID
						.toByteArray());
			}
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	public void rcvReqJoinChan(String channame) {
		SGSChannel chan = router.openChannel(channame);
		if (!chan.isLocked()) {
			chan.join(this);
		} else {
			// System.out.println("Channel is locked, can't join");
			try {
				transport.deliverChannelLocked(channame, userID.toByteArray());
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}

	/**
	 * Passes packet data arriving on the raw connection to be processed by the
	 * user.
	 * 
	 * @param inputBuffer
	 *            the incoming data, ready for get() operations
	 */
	protected void packetReceived(ByteBuffer inputBuffer) {
		transport.packetReceived(inputBuffer);
	}

	/**
	 * Called by the raw connection to indicate that the conenction has been
	 * lost.
	 */
	protected void disconnected() {
		connected = false;
		// XXX currently this just immediately dereigsters user.
		// This must be modified for fail-over when we support multiple
		// stacks
		router.deregisterUser(this);
	}

	/*
	 * This call forces a disconnect on the server side to a client. If reason
	 * is not-null it will be sent to the user in a packet before disconenction.
	 * if it is null however no packet is sent. This is necessary if
	 * disconnection is being forced for critical communciation failures such as
	 * a buffer overrun.
	 * 
	 * @see com.sun.gi.comm.users.server.SGSUser#forceDisconnect(java.lang.String)
	 */
	public void forceDisconnect(String reason) {
		if (reason != null) {
			// TODO: send disconnect info packet to user
		}
		try {
			transport.sendLogoutRequest();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
