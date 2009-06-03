/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.sgs.test.client.simple;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import java.util.Properties;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.client.simple.SimpleClient;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 *
 * @author waldo
 */
public class smokeTestClient implements SimpleClientListener{

    private SimpleClient client;
    private boolean joinChannelPass, receiveMsgPass,
            logOutPass;
    private smokeTestChannelListener channel;

    private static String host = "localhost";
    private static int port = 1099;
    private static String loginName;
    private static Properties props;

    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(smokeTestClient.class.getName()));


    public smokeTestClient(){
        joinChannelPass = receiveMsgPass = logOutPass = false;
        client = new SimpleClient(this);
    }

    public final static void main(String[] args){

        props = parseArgs(args);
        smokeTestClient testClient = new smokeTestClient();
        testClient.start();
    }

    public void start(){
        loginName = "discme";
        try {
                    client.login(props);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Exception generated on initial login");
            System.exit(1);
        }
    }

    public PasswordAuthentication getPasswordAuthentication() {
        PasswordAuthentication auth = new PasswordAuthentication(loginName, loginName.toCharArray());
        return auth;
    }

    public void loggedIn(){
        String msg = "loggedIn:" + loginName;
        try {
            client.send(ByteBuffer.wrap(msg.getBytes()));
        } catch (Exception e) {
            logger.log(Level.WARNING, "error sending reply to logged in message");
        }

    }

    public void loginFailed(String reason) {
        if (loginName.equals("kickme")) {
            logger.log(Level.WARNING, "Login failure test passed");
        } else {
            logger.log(Level.WARNING, "Unexpected login failure with client name " + loginName);
            logger.log(Level.WARNING, "Failure reason reported " + reason);
            System.exit(1);
        }
        loginName = "discme";
        try {
            client.login(props);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Exception generated while logging in as " + loginName);
            System.exit(1);
        }
    }

    public ClientChannelListener joinedChannel(ClientChannel channel){
        ClientChannelListener channelListener = new smokeTestChannelListener (this.client);
        String toSend = "joinedChannel" + channel.getName();
        try{
         channel.send(ByteBuffer.wrap(toSend.getBytes()));
        } catch (IOException e){
            logger.log(Level.WARNING, "unable to send on newly joined channel" + channel.getName());
        }
        return channelListener;
    }

    public void receivedMessage(ByteBuffer message){
        String msg = "receivedMessage:" + message.toString();

        if(message.toString().equals("logout")){
            client.logout(false);
            return;
        }

        try{
            client.send(ByteBuffer.wrap(msg.getBytes()));
        } catch (IOException e){
            logger.log(Level.WARNING, "Unable to respoind to message");
        }
    }

    public void reconnecting(){

    }

    public void reconnected(){

    }

    public void disconnected(boolean graceful, String reason) {
        if (loginName.equals("discme")) {
        logger.log(Level.WARNING, "Passed disconnection test");
        loginName = "smokeTest";
        try {
            client.login(props);
        } catch (IOException e) {
            logger.log(Level.WARNING, "unable to log in for main tests");
        }
        } else {
            System.exit(printResults());
        }
    }

    private int printResults(){
        int failures = 0;

        if (!receiveMsgPass){
            logger.log(Level.WARNING, "Smoke test failed in receive message test");
            failures++;
        }
        if (!joinChannelPass){
            logger.log(Level.WARNING, "Smoke test failed in channel join test");
            failures++;
        }
        if (!logOutPass){
            logger.log(Level.WARNING, "Smoke test faled logout test");
            failures++;
        }
        return failures;
    }
    /**
     *  Parse any command line arguments to determine the
     * correct host and port for connecting to the smoke test
     * server. If there are no arguments, the default settings
     * will be used
     * @param args The arguments passed in on the command line
     * @return a property object that can be passed in to the
     * {@link SimpleClient.login} method of the {@link SimpleClient}.
     */
    private static Properties parseArgs(String[] args){
        Properties returnProps = new Properties();
        Boolean error = false;

        for (int i = 0; i < args.length; i++){
            if (args[i].equals("-usage")){
                printUse();
            }
            if (args[i].equals("host") || args[i].equals("host=")){
                if (args[++i].equals("=")){
                    i++;
                }
                host = args[i];
            }
            if (args[i].equals("port")|| args[i].equals("port=")){
                if (args[++i].equals("=")){
                    i++;
                }
                port = Integer.decode(args[i]).intValue();
            }
        }
       returnProps.put("host", host);
       returnProps.put("port", port);
       return(returnProps);
    }

    /**
     * Print the usage message on the command line. Should
     * only be called if the program is invoked incorrectly. Will
     * print a standard usage message.
     */
    private static void printUse(){
        System.out.println("usage: java smokeTestClient " +
                "[host = hostname] [port = portnum]" +
                "[ -usage]");
    }
}
