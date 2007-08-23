/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.PasswordAuthentication;
import java.net.ProtocolException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.client.simple.SimpleClient;

import com.sun.sgs.germwar.client.gui.MainFrame;
import com.sun.sgs.germwar.shared.Bacterium;
import com.sun.sgs.germwar.shared.Coordinate;
import com.sun.sgs.germwar.shared.GermWarConstants;
import com.sun.sgs.germwar.shared.InvalidMoveException;
import com.sun.sgs.germwar.shared.Location;
import com.sun.sgs.germwar.shared.Pair;
import com.sun.sgs.germwar.shared.message.AckResponse;
import com.sun.sgs.germwar.shared.message.AppMessage;
import com.sun.sgs.germwar.shared.message.ChatMessage;
import com.sun.sgs.germwar.shared.message.ChatRequest;
import com.sun.sgs.germwar.shared.message.InitializationData;
import com.sun.sgs.germwar.shared.message.LocationUpdate;
import com.sun.sgs.germwar.shared.message.MoveRequest;
import com.sun.sgs.germwar.shared.message.NackResponse;
import com.sun.sgs.germwar.shared.message.Protocol;
import com.sun.sgs.germwar.shared.message.TurnUpdate;

import com.sun.sgs.impl.sharedutil.HexDumper;

/**
 * A simple client for the GermWar application.  This class abstracts all of the
 * application logic from the GUI code.
 * <p>
 * The {@code GermWarClient} understands the following properties:
 * <ul>
 * <li><code>{@value #HOST_PROPERTY}</code> <br>
 *     <i>Default:</i> {@value #DEFAULT_HOST} <br>
 *     The hostname of the {@code GermWarApp} server.<p>
 *
 * <li><code>{@value #PORT_PROPERTY}</code> <br>
 *     <i>Default:</i> {@value #DEFAULT_PORT} <br>
 *     The port of the {@code GermWarApp} server.<p>
 * </ul>
 */
public class GermWarClient implements GameLogic {
    /** The name of the host property */
    public static final String HOST_PROPERTY = "GermWarClient.host";

    /** The default hostname */
    public static final String DEFAULT_HOST = "localhost";

    /** The name of the port property */
    public static final String PORT_PROPERTY = "GermWarClient.port";

    /** The default port */
    public static final String DEFAULT_PORT = "6169";

    /**
     * The global channel, used by the server when it wants to broadcast
     * a message all users.  Clients should never send on this channel.
     */
    private ClientChannel globalChannel;

    /**
     * Cache of world locations.  A location should only be cached if this
     * client is currently a member of the map-update channel for that location,
     * which ensures that any changes to that location will be received by this
     * client, so that the cached copy of the location can be updated.
     */
    private Map<Coordinate,Location> locationCache =
        new ConcurrentHashMap<Coordinate,Location>(512, 0.75f, 1);

    /** Used for communication with the server */
    private SimpleClient client;

    /** Outstanding chat message requests (i.e. server has not responded yet). */
    private Map<Integer,ChatRequest> pendingChatRequests =
        Collections.synchronizedMap(new HashMap<Integer,ChatRequest>());

    /** Assigned by the server once we log in. */
    private long playerId = -1;

    /** Generates (mostly) unique message IDs. */
    private AtomicInteger msgIdGen = new AtomicInteger(0);

    /** The {@code GameGui} instance to interface with. */
    private GameGui gui = new NullGui("default", "pwd");

    /** Whether a logout request was sent but not yet responded to. */
    private boolean disconnectExpected = false;

    /** The logger for this class. */
    private static final Logger logger =
        Logger.getLogger(GermWarClient.class.getName());

    // Constructor

    /**
     * Creates a new {@code GermWarClient}.
     */
    public GermWarClient() {
        // empty
    }

    // Main

    /**
     * Runs a new {@code GermWarClient} application with an instance of {@link
     * MainFrame} as the GUI.
     * 
     * @param args the commandline arguments (not used)
     */
    public static void main(String[] args) {
        GermWarClient gameClient = new GermWarClient();
        GameGui gui = new MainFrame(gameClient);
        gameClient.setGui(gui);
        gui.setVisible(true);
    }

    /** Sets the {@code GameGui} that this object should interface with. */
    public void setGui(GameGui gui) {
        this.gui = gui;
        gui.setLoginStatus(client != null);
    }

    /**
     * Handles received messages (from any of multiple entry points - direct
     * from server, on global channel, on map-update channels, etc.).
     */
    private void processServerMessage(byte[] message) {
        AppMessage appMsg;

        try {
            appMsg = Protocol.read(message);
        } catch (ProtocolException e) {
            logger.log(Level.SEVERE,
                String.format("ProtocolException while processing server" +
                    " message: %s\nmessage: %s", e.toString(),
                    Arrays.toString(message)));
            return;
        }

        // AckResponse
        if (appMsg instanceof AckResponse) {
            AckResponse ack = (AckResponse)appMsg;
            int origMsgId = ack.getOriginalMsgId();

            ChatRequest request = pendingChatRequests.remove(origMsgId);

            if (request == null) {
                logger.log(Level.WARNING, "Ack received for unknown" +
                    " ChatRequest.  id=" + origMsgId);
                return;
            }

            /**
             * Else, chat message was delivered successfully; echo it to the
             * GUI since server won't echo back to us.
             */
            String msg = request.getMessage();
            String recip = request.getRecipient();
            gui.newChatMessage(null, String.format("You sent to %s: %s", recip,
                                   msg));

            logger.log(Level.FINER,
                String.format("Chat message successfully sent to %s:" +
                    " \"%s\"", msg, recip));
        }
        // NackResponse
        else if (appMsg instanceof NackResponse) {
            NackResponse nack = (NackResponse)appMsg;
            int origMsgId = nack.getOriginalMsgId();

            ChatRequest request = pendingChatRequests.remove(origMsgId);

            if (request == null) {
                logger.log(Level.WARNING, "Nack received for unknown" +
                    " ChatRequest.  id=" + origMsgId);
                return;
            }

            /**
             * Else, chat message was not delivered successfully, probably
             * because the recipient is offline.  Print a message to this
             * effect to the GUI's chat window.
             */
            gui.newChatMessage(null, "** Error: " + request.getRecipient() +
                " is not currently online.");

            logger.log(Level.FINER,
                String.format("Chat message failed to send to %s: \"%s\"",
                    request.getRecipient(), request.getMessage()));
        }
        // ChatMessage
        else if (appMsg instanceof ChatMessage) {
            ChatMessage chatMessage = (ChatMessage)appMsg;
            String sender = chatMessage.getSender();
            String msg = chatMessage.getMessage();
            gui.newChatMessage(sender, msg);

            logger.log(Level.FINER,
                String.format("Received chat message from %s: \"%s\"",
                    sender, msg));
        }
        // LocationUpdate
        else if (appMsg instanceof LocationUpdate) {
            LocationUpdate update = (LocationUpdate)appMsg;
            Location loc = update.getLocation();
            locationCache.put(loc.getCoordinate(), loc);
            gui.mapUpdate(loc);

            logger.log(Level.FINE, "Received location update: " + loc);
        }
        // InitializationData
        else if (appMsg instanceof InitializationData) {
            InitializationData initData = (InitializationData)appMsg;
            playerId = initData.playerId();

            logger.log(Level.INFO, "Received initialization-data; playerId=" +
                playerId);
        }
        // TurnUpdate
        else if (appMsg instanceof TurnUpdate) {
            TurnUpdate turnUpdate = (TurnUpdate)appMsg;
            long turnNo = turnUpdate.getTurn();

            for (Location loc : locationCache.values()) {
                loc.update(turnNo);
            }

            gui.newTurn();
            logger.log(Level.FINE, "Received turn update (" + turnNo + ")");
        }
        else {
            logger.log(Level.SEVERE, "Received unsupported message from" +
                " server: " + appMsg.getClass().getName());
        }
    }

    // Implement GameLogic

    /**
     * {@inheritDoc}
     */
    public void doMove(Location src, Location dest) throws InvalidMoveException {
        logger.log(Level.FINE, "doMove(" + src + ", " + dest + ") called.");

        // todo - more initial error checking?

        if (!src.isOccupied())
            throw new InvalidMoveException(null, src, dest,
                "No bacterium selected");

        Bacterium bact = src.getOccupant();
        int dist = src.getCoordinate().diff(dest.getCoordinate())
            .manhattanLength();

        if (bact.getPlayerId() != playerId)
            throw new InvalidMoveException(bact, src, dest,
                "Selected bacterium is not under your control.");

        if (dest.isOccupied())
            throw new InvalidMoveException(bact, src, dest,
                "Destination is not empty.");

        if (bact.getCurrentMovementPoints() < dist)
            throw new InvalidMoveException(bact, src, dest,
                "Not enough movement points (" + dist + " required).");

        if (bact.getHealth() < Bacterium.MOVE_HEALTH_COST)
            throw new InvalidMoveException(bact, src, dest,
                "Not enough health to move (" + Bacterium.MOVE_HEALTH_COST +
                " required).");

        try {
            MoveRequest request = new MoveRequest(bact.getId(),
                src.getCoordinate(), dest.getCoordinate());

            client.send(Protocol.write(request));

            logger.log(Level.FINER, "Move request sent for " + bact + " from " +
                src + " to " + dest);
        } catch (IOException ioe) {
            logger.log(Level.WARNING,
                "IOException sending move request: " + ioe.getMessage());

            InvalidMoveException ime = new InvalidMoveException(bact, src, dest);
            ime.initCause(ioe);
            throw ime;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Location getLocation(Coordinate coord) {
        return locationCache.get(coord);
    }

    /**
     * {@inheritDoc}
     */
    public long getPlayerId() {
        if (playerId == -1)
            throw new IllegalStateException("playerId has not yet been" +
                " initialized by the server.");

        return playerId;
    }

    /**
     * {@inheritDoc}
     */
    public void login() {
        String host = System.getProperty(HOST_PROPERTY, DEFAULT_HOST);
        String port = System.getProperty(PORT_PROPERTY, DEFAULT_PORT);

        try {
            Properties props = new Properties();
            props.put("host", host);
            props.put("port", port);
            
            logger.log(Level.INFO, String.format("Logging into %s:%s",
                           host, port));
            
            client = new SimpleClient(new SimpleClientListenerImpl());
            client.login(props);
            gui.setStatusMessage("Logging in...");
            // TODO: enable the loginButton as a "Cancel Login" action.
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void logout(boolean force) {
        if (client == null) throw new IllegalStateException("not connected");
        logger.log(Level.INFO, "Logging out.");
        disconnectExpected = true;
        client.logout(force);
        gui.setStatusMessage("Logging out...");
    }

    /**
     * {@inheritDoc}
     */
    public void quit() {
        if (client != null) logout(true);  /** force */
        System.exit(0);
    }

    /**
     * {@inheritDoc}
     */
    public void sendChatMessage(String recipient, String message) {
        try {
            int requestId = msgIdGen.getAndAdd(1);
            ChatRequest request = new ChatRequest(recipient, message, requestId);

            if (pendingChatRequests.containsKey(requestId)) {
                logger.log(Level.SEVERE, "Duplicate requestIds detected: " +
                    requestId);
                return;
            }

            client.send(Protocol.write(request));
            pendingChatRequests.put(requestId, request);
            
            logger.log(Level.FINER,
                String.format("Chat request sent to %s: \"%s\"",
                    recipient, message));
        } catch (IOException e) {
            gui.popup("Exception sending chat message:\n" + e.toString(),
                JOptionPane.ERROR_MESSAGE);
            
            e.printStackTrace();
        }
    }

    // utility methods

    /**
     * Decodes the given {@code bytes} into a message string.
     *
     * @param bytes the encoded message
     * @return the decoded message string
     */
    private static String fromMessageBytes(byte[] bytes) {
        try {
            return new String(bytes, GermWarConstants.MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset " +
                GermWarConstants.MESSAGE_CHARSET + " not found", e);
        }
    }

    /**
     * Encodes the given message string into a byte array.
     *
     * @param s the message string to encode
     * @return the encoded message as a byte array
     */
    private static byte[] toMessageBytes(String s) {
        try {
            return s.getBytes(GermWarConstants.MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset " +
                GermWarConstants.MESSAGE_CHARSET + " not found", e);
        }
    }

    /**
     * Listener for client connection events.
     */
    private class SimpleClientListenerImpl implements SimpleClientListener {
        /**
         * {@inheritDoc}
         */
        public void disconnected(boolean graceful, String reason) {
            if (!disconnectExpected) {
                gui.popup("Disconnected: " +
                    (graceful ? "gracefully" : "ungracefully") +
                    ": " + reason,
                    JOptionPane.WARNING_MESSAGE);
            }
            
            logger.log(Level.INFO,
                String.format("Disconnected.  graceful=%s, expected=%s, reason=%s",
                    graceful, disconnectExpected, reason));
            
            disconnectExpected = false;
            gui.setLoginStatus(false);
            gui.setStatusMessage("Disconnected");
            client = null;
        }

        /**
         * {@inheritDoc}
         */
        public PasswordAuthentication getPasswordAuthentication() {
            gui.setStatusMessage("Validating...");
            return gui.promptForLogin();
        }

        /**
         * {@inheritDoc}
         */
        public ClientChannelListener joinedChannel(ClientChannel channel) {
            /** Save a handle to the global channel. */
            if (channel.getName().equals(GermWarConstants.GLOBAL_CHANNEL_NAME)) {
                globalChannel = channel;
                logger.log(Level.FINE, "Joined global channel: " +
                    channel.getName());
                
                return new GlobalListener();
            }
            
            /* else, unknown channel */
            logger.log(Level.WARNING, "An unknown channel was joined: " +
                channel.getName());

            return new LoggingListener(Level.INFO);
        }

        /**
         * {@inheritDoc}
         */
        public void loggedIn() {
            SessionId session = client.getSessionId();
            logger.log(Level.INFO, String.format("Logged in with session-id %s",
                           HexDumper.toHexString(session.toBytes())));
            
            gui.setStatusMessage("Connected");
            gui.popup("Login success!\nWelcome to Germ Wars!",
                JOptionPane.INFORMATION_MESSAGE);
            gui.setLoginStatus(true);
        }

        /**
         * {@inheritDoc}
         */
        public void loginFailed(String reason) {
            logger.log(Level.INFO, String.format("Login failed: %s", reason));
            gui.setStatusMessage("Disconnected");
            gui.popup("Login failed: " + reason,
                JOptionPane.WARNING_MESSAGE);
        }

        /**
         * {@inheritDoc}
         */
        public void receivedMessage(byte[] message) {
            processServerMessage(message);
        }

        /**
         * {@inheritDoc}
         */
        public void reconnecting() {
            gui.setStatusMessage("Reconnecting...");
            logger.log(Level.FINE, "Reconnecting.");
        }

        /**
         * {@inheritDoc}
         */
        public void reconnected() {
            gui.setStatusMessage("Connected");
            logger.log(Level.FINE, "Reconnected.");
            
            gui.popup("Your session has reconnected.",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Listener for messages on the global channel.
     */
    private class GlobalListener implements ClientChannelListener {
        /**
         * Creates a new {@code GlobalListener}.
         */
        public GlobalListener() {
            // empty
        }

        // implement ClientChannelListener

        /**
         * {@inheritDoc}
         */
        public void receivedMessage(ClientChannel channel, SessionId sender,
            byte[] message)
        {
            if (sender == null) {  /** sent by server */
                logger.log(Level.FINE, "Received message from server on" +
                    " channel " + channel.getName() + ".");

                processServerMessage(message);
            } else {
                logger.log(Level.WARNING, "Received unexpected message from " +
                    sender.toString() + " on channel " + channel.getName() +
                    ": " + fromMessageBytes(message));
            }
        }

        /**
         * {@inheritDoc}
         */
        public void leftChannel(ClientChannel channel) {
            logger.log(Level.FINE, "Left global channel: " + channel.getName());
                
            if (!disconnectExpected) {
                System.err.println("Warning: kicked from global channel.");
            }
        }
    }

    /**
     * Listener that just logs actions.
     */
    private class LoggingListener implements ClientChannelListener {
        private Level level;

        /**
         * Creates a new {@code LoggingListener}.
         */
        public LoggingListener(Level level) {
            this.level = level;
        }

        // implement ClientChannelListener

        /**
         * {@inheritDoc}
         */
        public void receivedMessage(ClientChannel channel, SessionId sender,
            byte[] message)
        {
            logger.log(level, "Received message on channel " + channel.getName() +
                " from " + sender + ": " + fromMessageBytes(message));
        }

        /**
         * {@inheritDoc}
         */
        public void leftChannel(ClientChannel channel) {
            logger.log(level, "Left channel: " + channel.getName());
        }
    }
}
