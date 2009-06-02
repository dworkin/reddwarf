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

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
/**
 *
 * @author waldo
 */
public class smokeTestClient implements SimpleClientListener{

    private SimpleClient client;

    private static String host = "localhost";
    private static int port = 1099;
    private static String loginName;
    private static Properties props;

    private static void printUse(){
        System.out.println("usage: java smokeTestClient " +
                "[host = hostname] [port = portnum]" +
                " -usage");
    }
    public final static void main(String[] args){

        props = parseArgs(args);



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
            System.out.print("error sending reply to logged in message");
        }

    }

    public void loginFailed(String reason){

    }

    public ClientChannelListener joinedChannel(ClientChannel channel){
        ClientChannelListener channelListener = new smokeTestChannelListener (this.client);
        String toSend = "joinedChannel" + channel.getName();
        try{
         channel.send(ByteBuffer.wrap(toSend.getBytes()));
        } catch (IOException e){
            System.out.println("unable to send on newly joined channel" + channel.getName());
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
            System.out.println("Unable to respoind to message");
        }
    }

    public void reconnecting(){

    }

    public void reconnected(){

    }

    public void disconnected(boolean graceful, String reason) {
        if (!loginName.equals("discme")) {
        }
        System.out.println("Passed disconnection test");
        loginName = "smokeTest";
        try {
            client.login(props);
        } catch (IOException e) {
            System.out.println("unable to log in for main tests");
        }

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

}
