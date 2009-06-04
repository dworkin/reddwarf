/*
 * Copyright (c) 2007-2009, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
public class SmokeTestClient implements SimpleClientListener{

    private SimpleClient myclient;
    private boolean joinChannelPass, receiveMsgPass,
            logOutPass;
    private SmokeTestChannelListener mychannel;

    private static String host = "localhost";
    private static String port = "1139";
    private static String loginName;
    private static Properties props;

    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(SmokeTestClient.class.getName()));


    public SmokeTestClient(){
        joinChannelPass = receiveMsgPass = logOutPass = false;
        myclient = new SimpleClient(this);
    }

    public final static void main(String[] args){

        props = parseArgs(args);
        SmokeTestClient testClient = new SmokeTestClient();
        testClient.start();
        while(true){
       synchronized(testClient){
        try {
            testClient.wait();
        } catch (Exception e) {
            e.printStackTrace();
        }
       }
        }
    }

    public void start(){
        loginName = "kickme";
        try {
                    myclient.login(props);
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
            myclient.send(ByteBuffer.wrap(msg.getBytes()));
        } catch (IOException e) {
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
            myclient.login(props);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Exception generated while logging in as " + loginName);
            System.exit(1);
        }
    }

    public ClientChannelListener joinedChannel(ClientChannel channel){
        mychannel = new SmokeTestChannelListener (this.myclient);
        String toSend = "joinedChannel:" + channel.getName();
        try{
         channel.send(ByteBuffer.wrap(toSend.getBytes()));
        } catch (IOException e){
            logger.log(Level.WARNING, "unable to send on newly joined channel" + channel.getName());
        }
        return mychannel;
    }

    public void receivedMessage(ByteBuffer message){
        String prefix = "receivedMessage:";
        String msg = message.asCharBuffer().toString();

        if(msg.equals("logout")){
            myclient.logout(false);
            return;
        }

        try{
            myclient.send(ByteBuffer.wrap((prefix+msg).getBytes()));
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
                myclient.login(props);
            } catch (IOException e) {
                logger.log(Level.WARNING, "unable to log in for main tests");
            }
        } else {
            System.exit(printResults());
        }
    }

    /**
     * Print out any test failures to the log, and calculate the number of failures that
     * have occurred. Return the number of failures.
     */
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
            logger.log(Level.WARNING, "Smoke test failed logout test");
            failures++;
        }
        if (!mychannel.getReceiveStatus()){
            logger.log(Level.WARNING, "Smoke test failed channel recieve message test");
            failures++;
        }
        if (!mychannel.getChannelLeftStatus()){
            logger.log(Level.WARNING, "Smoke test failed leaving channel test");
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
                port = args[i];
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
