/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
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
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
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
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.framework.install.impl;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.framework.install.InstallationLoader;
import com.sun.gi.framework.install.UserMgrRec;
import com.sun.gi.framework.install.ValidatorRec;

public class InstallationURL implements InstallationLoader {
    private Map<Integer, DeploymentRec> idToDeploymentRec = new HashMap<Integer, DeploymentRec>();

    public InstallationURL() {
        super();
    }

    /**
     * InstallationURL
     * 
     * @param url
     */
    public InstallationURL(URL url) throws InstantiationException,
            FileNotFoundException, IOException {
        URLConnection conn = url.openConnection();
        conn.connect();
        InputStream content = conn.getInputStream();
        InputStreamReader isrdr = new InputStreamReader(content);
        BufferedReader rdr = new BufferedReader(isrdr);
        String inline = rdr.readLine();
        URLDeploymentReader deprdr = new URLDeploymentReader();
        while (inline != null) {
            if ((inline.length() > 0) && (inline.charAt(0) != '#')) {
                StringTokenizer tok = new StringTokenizer(inline);
                int appID = Integer.parseInt(tok.nextToken());
                String urlname = tok.nextToken();
                URL drecURL = new URL(urlname);
                DeploymentRec drec = deprdr.getDeploymentRec(drecURL);
                if (drec != null) {
                    drec.setID(appID);
                    URL ctext = new URL(drecURL.getProtocol() + ":"
                            + drecURL.getPath());
                    String root = ctext.toString();
                    root = root.substring(0,root.lastIndexOf("/"));
                    drec.setRootURL(root);                    		
                    if (drec.getClasspathURL() != null) {
                        String classpath = drec.getClasspathURL().trim();
                        if (classpath.substring(0, 5).equalsIgnoreCase("file:")
                                && (!classpath.substring(5, 7).equals("//"))) {
                            // realtive file URL, monkey with it                            
                            URL turl = new URL(ctext, classpath.substring(5));
                            classpath = turl.toExternalForm();
                            // System.err.println("Modified cp:"+classpath);
                            ((DeploymentRecImpl) drec).setClasspathURL(classpath);
                        }
                    }
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
            args = new String[] { "file:Install.txt" };
        }
        try {
            InstallationLoader inst = new InstallationURL(new URL(args[0]));
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
