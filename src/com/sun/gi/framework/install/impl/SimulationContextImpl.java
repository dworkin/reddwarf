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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.impl.RouterImpl;
import com.sun.gi.comm.users.server.UserManager;
import com.sun.gi.comm.users.validation.UserValidatorFactory;
import com.sun.gi.comm.users.validation.impl.UserValidatorFactoryImpl;
import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.framework.install.SimulationContext;
import com.sun.gi.framework.install.UserMgrRec;
import com.sun.gi.framework.install.ValidatorRec;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.status.StatusReport;
import com.sun.gi.logic.SimKernel;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.impl.SimulationImpl;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.tso.TSOObjectStore;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;

/**
 * 
 * <p>Title: SimulationContextImpl</p>
 * 
 * <p>Description: This concrete implementation of SimulationContext 
 * uses a DeploymentRec for its details.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class SimulationContextImpl implements SimulationContext {

    private DeploymentRec deployment;
    private StatusType status = StatusType.STOPPED;
    
    private SimKernel kernel;
    private TransportManager transportManager;
    private List<UserManager> userManagerList;
    private Simulation simulation;
    //private int reportID = -1;           // this simulation's entry in the
                // installation report.
    private String statusBlockName;
    //private boolean firstStart = true;

    private static final String DEFAULT_OSTORE_DATASPACE =
            "com.sun.gi.objectstore.tso.dataspace.PersistantInMemoryDataSpace";
        
    public SimulationContextImpl(SimKernel kernel, TransportManager transportManager, 
    							DeploymentRec deployment) {
            this.deployment = deployment;
            this.kernel = kernel;
            this.transportManager = transportManager;
            this.userManagerList = new LinkedList<UserManager>();
    }
    
    public void start(StatusReport installationReport) {
        if (deployment == null || status.equals(StatusType.STARTED)) {
        	return;
        }
        Router router = null;
        int gameID = deployment.getID();
        ObjectStore ostore = null;
        try {
            String dataspaceName =
                    System.getProperty("sgs.ostore.dataspace",
                    DEFAULT_OSTORE_DATASPACE);
            Class<?> dataspaceClass = Class.forName(dataspaceName);
            Constructor<?> ctor =
                    dataspaceClass.getConstructor(Long.TYPE);
            DataSpace dataspace = (DataSpace) ctor.newInstance(gameID);
            ostore = new TSOObjectStore(dataspace);
            //ostore = new TSOObjectStore(new InMemoryDataSpace(gameID));
            String cleanProperty = System.getProperty("sgs.ostore.startclean");
            if ((cleanProperty != null)
                    && (cleanProperty.equalsIgnoreCase("true"))) {
                    ostore.clear();
            }
            router = new RouterImpl(transportManager,ostore);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        router.setChannelFilters(deployment.getChannelFilters());
        
        if (statusBlockName == null) {
            int gameCount = Integer.parseInt(installationReport.getParameter(
                            "game", "count"));
            statusBlockName = "game." + gameCount;
            installationReport.setParameter("game", "count",
                                        Integer.toString(gameCount + 1));
            
        }
    
        installationReport.setParameter(statusBlockName, "id",
        	Integer.toString(gameID));
        installationReport.setParameter(statusBlockName, "description",
        	deployment.getDescription());
        installationReport.setParameter(statusBlockName, "name", deployment.getName());
        		
        int umgrCount = 0;
        
        // create simulation container for game
        ClassLoader loader = null;
        if (deployment.getBootClass() != null) {
            try {
                loader = new URLClassLoader(new URL[] { new URL(
                        deployment.getClasspathURL()) });
                
                //			 set app info system properties
                String name = deployment.getName();
                // convert spaces to underbars
                name = name.replaceAll(" ","_").toLowerCase();
                String prefix = "sgs.game."+name+".";
                String rootProp =prefix+"rootURL";
    	           System.setProperty(rootProp, deployment.getRootURL());
    		simulation = new SimulationImpl(kernel, ostore, router, deployment, loader);
            } catch (InstantiationException e) {
        
        		e.printStackTrace();
        		return;
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            
        
        }
        
        // create user managers
        for (UserMgrRec umgrRec : deployment.getUserManagers()) {
            String serverClassName = umgrRec.getServerClassName();
            String umgrBlock = statusBlockName + ".umgr." + umgrCount;
            umgrCount++;
            installationReport.setParameter(statusBlockName + ".umgr", "count", 
                    Integer.toString(umgrCount));
            try {
                Class<?> serverClass = Class.forName(serverClassName, true, loader);
                Constructor<?> constructor = serverClass.getConstructor(new Class[] {
                	Router.class, Map.class });
                UserManager umgr = (UserManager) constructor.newInstance(new Object[] {
                	router, umgrRec.getParameterMap() });
                
                userManagerList.add(umgr);
                installationReport.setParameter(umgrBlock, "clientClassName",
            	umgr.getClientClassname());
                Set clientParams = umgr.getClientParams().entrySet();
                installationReport.setParameter(umgrBlock + ".params", "count",
                			Integer.toString(clientParams.size()));
                int c = 0;
                for (Iterator i2 = clientParams.iterator(); i2.hasNext();) {
                    Entry entry = (Entry) i2.next();
                    installationReport.setParameter(umgrBlock + ".params.keys",
                    Integer.toString(c), (String) entry.getKey());
                    installationReport.setParameter(umgrBlock
                        + ".params.values", Integer.toString(c),
                        	    (String) entry.getValue());
                    c++;
                }
    
                if (umgrRec.hasValidatorModules()) {
                    UserValidatorFactory validatorFactory = new UserValidatorFactoryImpl();
                    for (ValidatorRec lmoduleRec : umgrRec.getValidatorModules()) {
                        String loginModuleClassname = lmoduleRec.getValidatorClassName();
                        Class loginModuleClass = Class.forName(loginModuleClassname, true, loader);
                        validatorFactory.addLoginModule(loginModuleClass,
                			lmoduleRec.getParameterMap());
                    }
                    umgr.setUserValidatorFactory(validatorFactory);
                }
    
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }
        status = StatusType.STARTED;
    }
    
    /**
     * {@inheritDoc}
     * 
     * This implemenation shuts down all running user managers, closes the 
     * object store, and finally removes the Simulation from the SimKernel.
     */
    public void stop(StatusReport installationReport) {
        if (status == StatusType.STOPPED) {
            return;
        }
        
        for (UserManager curUserManager : userManagerList) {
            curUserManager.shutdown();
        }
        userManagerList.clear();
        simulation.getObjectStore().close();
        kernel.removeSimulation(simulation);
        
        // remove all entries of this game from the installationReport
        installationReport.removeBlock(statusBlockName);
        
        status = StatusType.STOPPED;
    }
    
    public StatusType getStatus() {
        return status;
    }
    
    public String getName() {
        return deployment.getName();
    }
    
    public int getID() {
        return deployment.getID();
    }
    
    public Collection<UserManager> getUserManagers() {
        return Collections.unmodifiableCollection(userManagerList);
    }
    
    public String toString() {
        return "[Simulation Context for " + getName() + " ID " + getID() + "]";
    }

}
