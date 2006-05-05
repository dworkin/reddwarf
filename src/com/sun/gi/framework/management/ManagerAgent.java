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
package com.sun.gi.framework.management;

import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.modelmbean.ModelMBean;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanOperationInfo;
import javax.management.modelmbean.RequiredModelMBean;

import com.sun.gi.SGS;
import com.sun.gi.framework.install.Deployer;
import com.sun.gi.framework.management.html.HTMLServer;

/**
 * 
 * <p>Title: ManagerAgent</p>
 * 
 * <p>Description: Central class for managing the JMX MBean access.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class ManagerAgent {
	
	private MBeanServer server;
	private HTMLServer htmlServer;
	
	public ManagerAgent() {
		server = MBeanServerFactory.createMBeanServer();
	}
	
	private ObjectName createObjectName(String name) {
		ObjectName objName = null;
		
		try {
			objName = new ObjectName(server.getDefaultDomain() + name);
		}
		catch (MalformedObjectNameException e) {
			e.printStackTrace();
		}
		
		return objName;
	}
	
	private ModelMBeanInfo createDeployerBeanInfo() {
		ModelMBeanInfo beanInfo = null;
		
		ModelMBeanOperationInfo[] operations = new ModelMBeanOperationInfo[3];
		
		try {
			operations[0] = new ModelMBeanOperationInfo("listContexts", 
					"List Simulations", null, "java.util.List<SimulationContext>", 
					MBeanOperationInfo.INFO);
			
			MBeanParameterInfo[] params = new MBeanParameterInfo[1];
			params[0] = new MBeanParameterInfo("App ID", "int", 
													"App ID");
			operations[1] = new ModelMBeanOperationInfo("startContext", 
					"Start the given Simulation", params, "void", 
					MBeanOperationInfo.INFO);
			
			operations[2] = new ModelMBeanOperationInfo("stopContext", 
					"Stops the given Simulation", params, "void", 
					MBeanOperationInfo.INFO);
			
			beanInfo = new ModelMBeanInfoSupport(Deployer.class.getName(), null, null, null, 
					operations, null);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		return beanInfo;
	}
	
	public ModelMBean createDeployerMBean(Deployer deployer) {
		ObjectName objName = createObjectName(":name=Deployer");
		
		ModelMBeanInfo beanInfo = createDeployerBeanInfo();
		RequiredModelMBean modelBean = null;
		try {
			modelBean = new RequiredModelMBean(beanInfo);
			modelBean.setManagedResource(deployer, "ObjectReference");
			server.registerMBean(modelBean, objName);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		return modelBean;
	}
	
	private ModelMBeanInfo createSGSBeanInfo() {
		ModelMBeanInfo beanInfo = null;
		
		ModelMBeanOperationInfo[] operations = new ModelMBeanOperationInfo[1];
		
		try {
			operations[0] = new ModelMBeanOperationInfo("shutdown", 
					"Shutdown the server", null, "void", 
					MBeanOperationInfo.INFO);
			
			beanInfo = new ModelMBeanInfoSupport(SGS.class.getName(), null, null, null, 
					operations, null);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		return beanInfo;
	}
	
	public ModelMBean createSGSMBean(SGS sgs) {
		ObjectName objName = createObjectName(":name=SGS");
		
		ModelMBeanInfo beanInfo = createSGSBeanInfo();
		RequiredModelMBean modelBean = null;
		try {
			modelBean = new RequiredModelMBean(beanInfo);
			modelBean.setManagedResource(sgs, "ObjectReference");
			server.registerMBean(modelBean, objName);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		return modelBean;
	}
	
	/**
	 * Starts a simple HTML management server listening on the specified port.
	 * 
	 * @param port		the port on which to listen for HTTP management requests 
	 */
	public void startHTMLAdapter(int port) {
		htmlServer = new HTMLServer(port);
		try {
			server.registerMBean(htmlServer, 
						createObjectName(":name=htmladaptor,port=" + port));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		htmlServer.start();
	}
	
	public void shutdown() {
		htmlServer.stop();
	}
	
	
}
