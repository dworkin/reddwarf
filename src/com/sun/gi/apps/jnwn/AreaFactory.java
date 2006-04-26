/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/

package com.sun.gi.apps.jnwn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.logging.Logger;

import com.worldwizards.nwn.ResourceManager;
import com.worldwizards.nwn.files.ERF;
import com.worldwizards.nwn.files.KeyTable;
import com.worldwizards.nwn.files.NWNModuleInfo;
import com.worldwizards.nwn.files.NWNResource;
import com.worldwizards.nwn.files.resources.GFF;
//import com.worldwizards.nwn.j3d.AreaLoader;
//import com.worldwizards.nwn.j3d.AreaSceneBase;
//import com.worldwizards.nwn.j3d.WalkMesh;
import com.worldwizards.nwn.j3d.WalkMeshMap;
import com.worldwizards.nwn.j3d.WalkMeshMapLoader;
//import com.worldwizards.nwn.j3d.WalkMeshPosition;
//import com.worldwizards.nwn.j3d.WalkMeshTracker;
//import com.worldwizards.nwn.j3d.WalkMesh.Face;


/**
 * A JNWN AreaFactory loads NWN area data and creates an Area GLO for it.
 *
 * @version $Rev: 456 $, $Date: 2006-03-02 22:44:27 -0500 (Thu, 02 Mar 2006) $
 */
public class AreaFactory {

    private static Logger log = Logger.getLogger("com.sun.gi.apps.jnwn");

    public static Area create() {

	log.fine("AreaFactory::create");

	final ResourceManager mgr = new ResourceManager();

	File dir = new File(System.getProperty("jnwn.data.dir"));
	if (dir == null) {
	    log.severe("Can't find jnwn.data.dir");
	    return null;
	}

	File[] keyFiles = dir.listFiles(new FilenameFilter() {
	    public boolean accept(File file, String name) {
		return name.endsWith(".key");
	    }
	});

	for (File keyFile : keyFiles) {
	    try {
		mgr.addKeyTable(new KeyTable(
		    new FileInputStream(keyFile).getChannel()));
	    } catch (FileNotFoundException e) {
		log.warning("File `" + keyFile + "' not found");
	    }
	}

	log.fine("Loaded keyfiles");

	final String moduleName = "DEMO - House of Doors.mod";

	try {
	    File moduleDir = new File(dir, "modules");
	    File moduleFile = new File(moduleDir, moduleName);
	    mgr.addErf(new ERF(new FileInputStream(moduleFile).getChannel()));
	} catch (FileNotFoundException e) {
	    log.warning("Module file `" + moduleName + "' not found");
	}

	log.fine("Loaded module `" + moduleName + "'");

	GFF infoResource = (GFF) mgr.getResource("module.ifo");
	NWNModuleInfo minfo = new NWNModuleInfo(infoResource, 0);

	final String areaName = minfo.getStartingArea();
	log.finer("Starting area is `" + areaName + "'");

	WalkMeshMap wmm = null;

	try {
            ByteBuffer areaFile =
                mgr.getRawResource(areaName, (short) 2012);
            WalkMeshMapLoader loader = new WalkMeshMapLoader(mgr);
            wmm = loader.load("start", areaFile, false);
/*
	    AreaLoader areaLoader = new AreaLoader(mgr);
	    ByteBuffer areaBuf = mgr.getRawResource(areaName, (short) 2012);
	    AreaSceneBase areaSB =
		(AreaSceneBase) areaLoader.load(areaName, areaBuf, false);
*/
	} catch (NullPointerException e) {
	    e.printStackTrace();
	}

	return new Area(minfo.getModuleName(),
			areaName,
			minfo.getStartX(),
			minfo.getStartY(),
			minfo.getStartZ(),
			minfo.getStartingFacing(),
			//new FakeCheatDetector());
			new WalkMeshCheatDetector(wmm));
    }

    static class FakeCheatDetector implements CheatDetector {

	private static final long serialVersionUID = 1L;

	public boolean detectWalkCheat(PlayerInfo info) {
	    if (info.pos.distance(info.lastPos) > 5.0f) {
		Pos last_valid_position = info.lastPos.clone();
		info.pos = last_valid_position;
		return true;
	    }
	    return false;
	}
    }
}
