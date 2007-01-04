package com.sun.sgs.example.battleboard.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.PasswordAuthentication;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

public class BattleBoardClient
    implements SimpleClientListener
{
    private static Logger log =
        Logger.getLogger("com.sun.gi.apps.battleboard.client");

    private SimpleClient client;

    private static Pattern wsRegexp = Pattern.compile("\\s");
    private State state = State.CONNECTING;

    enum State {
        CONNECTING,
        JOINING_GAME
    }

    /*
     * Allow the user to control some aspects of the system from the
     * commandline rather than interactively.
     * 
     * The properties battleboard.userName, battleboard.userPassword,
     * and battleboard.playerName can be used to specify the user
     * name, password, and player name, respectively.
     * 
     * If the "battleboard.displayMode" property is "swing", then an
     * interactive Swing-based GUI is used.  If the displayMode is
     * "text", then a simple interactive text-based interface is used. 
     * If the displayMode is "batch" then an even simpler text-based
     * interface, designed for use by the test scripts, is used.
     */

    private String userName =
	    System.getProperty("battleboard.userName", null);
    private String userPassword =
	    System.getProperty("battleboard.userPassword", null);
    private String playerName =
	    System.getProperty("battleboard.playerName", null);
    private String displayMode =
	    System.getProperty("battleboard.displayMode", "swing");

    private boolean batchMode = "batch".equals(displayMode);
    private boolean swingMode = "swing".equals(displayMode);

    public BattleBoardClient() {
    }

    public boolean isBatchMode() {
	return batchMode;
    }

    protected void showPrompt(String prompt) {
        System.out.print(prompt + ": ");
        if (batchMode) {
            System.out.println();
        }
        System.out.flush();
    }

    /**
     * Gets a line of input from the keyboard.
     *
     * @return a line of input from the keyboard
     */
    protected String getLine() {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    System.in));
            String line = input.readLine();
            return (line == null) ? "" : line;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    // Implement SimpleClientListener

    public PasswordAuthentication getPasswordAuthentication(String prompt) {
        if (userName == null) {
            showPrompt(prompt);
            String line = getLine();
            userName = wsRegexp.matcher(line).replaceAll("");
        }

        char[] pass;
        if (userPassword != null) {
            pass = userPassword.toCharArray();
        } else {
            pass = getLine().toCharArray();
        }
            
        return new PasswordAuthentication(userName, pass);
    }
    
    /**
     * {@inheritDoc}
     */
    public void loggedIn() {
        log.fine("loggedIn");

        switch (state) {
            case CONNECTING:
                break;
            default:
                log.warning("connected(): unexpected state " + state);
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void loginFailed(String reason) {
        log.info("loginFailed: " + reason);
	System.exit(1);
    }

    // Implement ServerSessionListener
    
    /**
     * {@inheritDoc}
     */
    public void reconnecting() {
        log.info("failOverInProgress - client choosing to exit");
        System.exit(1);
    }

    /**
     * {@inheritDoc}
     */
    public void reconnected() {
        log.info("reconnected");
    }

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(byte[] message) {
        log.warning("got unexpected direct message " + new String(message));
    }

    /**
     * {@inheritDoc}
     */    
    public void disconnected(boolean graceful) {
        log.fine("disconnected; graceful = " + graceful);
    }
    
    /**
     * {@inheritDoc}
     */
    public ClientChannelListener joinedChannel(ClientChannel channel) {
        log.fine("joinedChannel " + channel.getName());

        if (channel.getName().equals("matchmaker")) {
            if (playerName == null) {

		/*
		 * If the user hasn't provided a playerName, offer
		 * that they use their userName as their
		 * playerName.
		 */

		playerName = userName;
		showPrompt("Enter your handle [" + userName + "]");
		String line = getLine();
		if (line.length() > 0) {
		    // Spaces aren't allowed
		    playerName = wsRegexp.matcher(line).replaceAll("");
		}
	    }

            /*
	     * This Matchmaker channel listener isn't strictly needed. 
	     * The server will manage the process of joining this
	     * client to the correct Game channel when enough players
	     * are present.
             * 
	     * A more sophisticated game might match players to games
	     * based on various parameters.
             * 
	     * This listener will also get called back if the
	     * Matchmaker channel closes for some reason, which a
	     * client may wish to handle.
             */
            state = State.JOINING_GAME;
            sendJoinReq(channel);
            
            return new BattleBoardChannelListener();
        }

        // Ok, must be a new game channel we've joined
        if (state == State.JOINING_GAME) {
            return new BattleBoardPlayer(
                    client, channel, playerName, swingMode);
        }
        
        throw new RuntimeException("Joined unexpected channel " + channel);
    }
    
    static class BattleBoardChannelListener implements ClientChannelListener
    {
        public void receivedMessage(ClientChannel channel,  SessionId sender,
                byte[] message)
        {
            // log.fine("dataArrived on " + channel.getName());

            // log.fine("got matchmaker data `" + new String(message) + "'");
        }

        public void leftChannel(ClientChannel channel) {
            // log.fine("channel " + channel.getName() + " closed");
        }
    }

    protected void sendJoinReq(ClientChannel chan) {
        String cmd = "join " + playerName;
        client.send(cmd.getBytes());
        System.out.println("Waiting for players");
    }

    /**
     * Set up and begin the game.
     */
    public void run() {
        try {
            Properties props = new Properties();
            props.put("host", "127.0.0.1");
            props.put("port", "1510");
            client = new SimpleClient(this);
            client.login(props);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private void setParams(String userName, String userPassword,
	    String playerName) {
	if (userName != null) {
	    this.userName = userName;
	}
	if (userPassword != null) {
	    this.userPassword = userPassword;
	}
	if (playerName != null) {
	    this.playerName = playerName;
	}
    }

    // main()

    public static void main(String[] args) {
	BattleBoardClient client = new BattleBoardClient();

	if (args.length == 2) {
	    client.setParams(args[0], args[1], args[0]);
	} else if (args.length == 3) {
	    client.setParams(args[0], args[1], args[2]);
        }
        
        

        client.run();
    }
}
