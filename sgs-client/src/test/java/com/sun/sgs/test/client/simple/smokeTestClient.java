/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.sgs.test.client.simple;

import java.util.Properties;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.client.simple.SimpleClient;


/**
 *
 * @author waldo
 */
public class smokeTestClient implements SimpleClientListener{

    private SimpleClient client;

    private static String host = "localhost";
    private static int port = 1099;

    private static Properties parseArgs(String[] args){
        Properties returnProps = new Properties();
        Boolean error = false;

        for (int i = 0; i < args.length; i++){
            if (args[i].equals("-usage")){
                printUse();
            }
            if (args[i].equals("host")){
                if (args[++i].equals("=")){
                    i++;
                }
                host = args[i];
            }
            if (args[i].equals("port")){
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

    private static void printUse(){

    }
    public final static void main(String[] args){

        Properties props = parseArgs(args);

    }
}
