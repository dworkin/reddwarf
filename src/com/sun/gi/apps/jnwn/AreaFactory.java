/*
 * $Id$
 *
 * Copyright 2006 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * -Redistributions of source code must retain the above copyright
 * notice, this  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF  OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
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
import com.worldwizards.nwn.j3d.AreaLoader;
import com.worldwizards.nwn.j3d.AreaSceneBase;
//import com.worldwizards.nwn.j3d.WalkMesh;
import com.worldwizards.nwn.j3d.WalkMeshMap;
//import com.worldwizards.nwn.j3d.WalkMeshPosition;
//import com.worldwizards.nwn.j3d.WalkMeshTracker;
//import com.worldwizards.nwn.j3d.WalkMesh.Face;


/**
 * A JNWN AreaFactory loads NWN area data and creates an Area GLO for it.
 *
 * @author  James Megquier
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
	    AreaLoader areaLoader = new AreaLoader(mgr);
	    ByteBuffer areaBuf = mgr.getRawResource(areaName, (short) 2012);
	    AreaSceneBase areaSB =
		(AreaSceneBase) areaLoader.load(areaName, areaBuf, false);
	    wmm = areaSB.getWalkMeshMap();
	} catch (NullPointerException e) {
	    e.printStackTrace();
	}

	return new Area(minfo.getModuleName(),
			areaName,
			minfo.getStartX(),
			minfo.getStartY(),
			minfo.getStartZ(),
			minfo.getStartingFacing(),
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
