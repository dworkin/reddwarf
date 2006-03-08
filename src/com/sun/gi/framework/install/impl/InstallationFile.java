package com.sun.gi.framework.install.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.framework.install.InstallationLoader;
import com.sun.gi.framework.install.UserMgrRec;
import com.sun.gi.framework.install.ValidatorRec;

public class InstallationFile implements InstallationLoader {

    private Map<Integer, DeploymentRec> idToDeploymentRec =
        new HashMap<Integer, DeploymentRec>();

    public InstallationFile() {
        super();
    }

    /**
     * InstallationFile
     * 
     * @param file File
     */
    public InstallationFile(File file) throws InstantiationException,
            FileNotFoundException, IOException {
        if (!file.isFile()) {
            throw new InstantiationException("Installation file "
                    + file.getAbsoluteFile() + " is not a valid file!");
        }
        BufferedReader rdr = new BufferedReader(new FileReader(file));
        String inline = rdr.readLine();
        URLDeploymentReader deprdr = new URLDeploymentReader();
        while (inline != null) {
            if ((inline.length() > 0) && (inline.charAt(0) != '#')) {
                StringTokenizer tok = new StringTokenizer(inline);
                int appID = Integer.parseInt(tok.nextToken());
                String urlname = tok.nextToken();
                DeploymentRec drec = deprdr.getDeploymentRec(new URL(urlname));
                if (drec != null) {
                    drec.setID(appID);
                    idToDeploymentRec.put(appID, drec);
                }
            }
            inline = rdr.readLine();
        }
    }

    /**
     * listGames
     * 
     * @return List
     */
    public Collection<DeploymentRec> listGames() {
        return idToDeploymentRec.values();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[] { "Install.txt" };
        }
        try {
            InstallationLoader inst = new InstallationFile(new File(args[0]));
            for (DeploymentRec game : inst.listGames()) {
                System.out.println("Game: " + game.getName());
                for (UserMgrRec mgr : game.getUserManagers()) {
                    System.out.println("    User Manager:"
                            + mgr.getServerClassName());
                    for (ValidatorRec mod : mgr.getValidatorModules()) {
                        System.out.println("        Login Module: "
                                + mod.getValidatorClassName());
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
