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

package com.sun.gi.framework.status.impl;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import com.sun.gi.framework.status.StatusReport;

public class StatusReportImpl implements StatusReport {
    ReportBlock root;
    long expirationDate;

    class ReportBlock {
        String name;
        Map<String, String> parameters = new HashMap<String, String>();
        List<ReportBlock> children = new ArrayList<ReportBlock>();

        public ReportBlock(String name) {
            this.name = name;
        }

        public ReportBlock() {
            super();
        }

        public void setParameter(String name, String value) {
            parameters.put(name, value);
        }

        public void addChild(ReportBlock blk) {
            children.add(blk);
        }

        public ReportBlock getChild(String childName) {
            for (ReportBlock block : children) {
                if (block.name.equalsIgnoreCase(childName)) {
                    return block;
                }
            }
            return null;
        }

        public void write(ByteBuffer buff) {
            buff.putInt(name.length());
            buff.put(name.getBytes());
            buff.putInt(parameters.size());
            for (Entry<String, String> entry : parameters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                buff.putInt(key.length());
                buff.put(key.getBytes());
                buff.putInt(value.length());
                buff.put(value.getBytes());
            }
            // do children
            buff.putInt(children.size());
            for (ReportBlock block : children) {
                block.write(buff);
            }
        }

        public void read(ByteBuffer buff) {
            int sz = buff.getInt();
            byte[] barray = new byte[sz];
            buff.get(barray);
            name = new String(barray);
            int pcount = buff.getInt();
            for (int i = 0; i < pcount; i++) {
                sz = buff.getInt();
                byte[] keybytes = new byte[sz];
                buff.get(keybytes);
                sz = buff.getInt();
                byte[] valuebytes = new byte[sz];
                buff.get(valuebytes);
                parameters.put(new String(keybytes), new String(valuebytes));
            }
            int childcount = buff.getInt();
            for (int i = 0; i < childcount; i++) {
                ReportBlock child = new ReportBlock();
                child.read(buff);
                children.add(child);
            }
        }

        public int getSize() {
            int sz = 0;
            sz += name.length() + 4;
            sz += 4;
            for (Entry<String, String> entry : parameters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                sz += key.length() + 4;
                sz += value.length() + 4;
            }
            sz += 4;
            for (ReportBlock block : children) {
                sz += block.getSize();
            }
            return sz;
        }

        /**
         * getParameter
         * 
         * @param currentTok String
         * @return String
         */
        private String getParameter(String currentTok) {
            return parameters.get(currentTok);
        }

        /**
         * collectAllParameters
         * 
         * @param strlist List
         */
        private void collectAllParameters(List<String> strlist, String prefix) {
            for (String key : parameters.keySet()) {
                strlist.add(prefix + "." + key);
            }
            for (ReportBlock block : children) {
                block.collectAllParameters(strlist, prefix + "." + name);
            }
        }

        public void dump(PrintStream outstrm, String prefix) {
            prefix += "." + name;
            for (Entry<String, String> entry : parameters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                outstrm.println(prefix + "." + key + " " + value);
            }
            outstrm.flush();
            for (ReportBlock block : children) {
                block.dump(outstrm, prefix);
            }
        }
    }

    private StatusReportImpl() {
        super();
    }

    StatusReportImpl(String rootName) {
        this();
        root = new ReportBlock(rootName);
    }

    /**
     * StatusReportImpl This is pacakge private. It is intended to only
     * be called by ReportManagerImpl
     * 
     * @param byteBuffer ByteBuffer
     */
    StatusReportImpl(ByteBuffer byteBuffer) {
        this();
        this.read(byteBuffer);
    }

    public void read(ByteBuffer buff) {
        root = new ReportBlock("");
        expirationDate = buff.getLong();
        root.read(buff);
    }

    /**
     * addParameter
     * 
     * @param paramName String
     * @param value String
     */
    public void setParameter(String blockPath, String paramName, String value) {
        ReportBlock currentBlock = root;
        StringTokenizer tok = new StringTokenizer(blockPath, ".");
        while (tok.hasMoreTokens()) {
            String currentTok = tok.nextToken();
            ReportBlock oldBlock = currentBlock;
            currentBlock = currentBlock.getChild(currentTok);
            if (currentBlock == null) { // add block
                currentBlock = new ReportBlock(currentTok);
                oldBlock.addChild(currentBlock);
            }
        }
        currentBlock.setParameter(paramName, value);
    }

    private ReportBlock getBlock(String blockPath) {
        ReportBlock currentBlock = root;
        StringTokenizer tok = new StringTokenizer(blockPath, ".");
        while (tok.hasMoreTokens()) {
            String currentTok = tok.nextToken();
            currentBlock = currentBlock.getChild(currentTok);
            if (currentBlock == null) { // add block
                return null;
            }
        }
        return currentBlock;

    }

    /**
     * getParameter
     * 
     * @param blockPath String
     * @param paramName String
     * @return String
     */
    public String getParameter(String blockPath, String paramName) {
        ReportBlock currentBlock = getBlock(blockPath);
        if (currentBlock == null) {
            return null;
        }
        return currentBlock.getParameter(paramName);
    }

    /**
     * listAllParameters
     * 
     * @return String[]
     */
    public String[] listAllParameters() {
        List<String> strlist = new ArrayList<String>();
        root.collectAllParameters(strlist, "");
        String[] strs = new String[strlist.size()];
        strlist.toArray(strs);
        return strs;
    }

    /**
     * listBlockParameters
     * 
     * @param blockPath String
     * @return String[]
     */
    public String[] listBlockParameters(String blockPath) {
        ReportBlock currentBlock = getBlock(blockPath);
        if (currentBlock == null) {
            return null;
        }
        Set<String> paramNames = currentBlock.parameters.keySet();
        String[] params = new String[paramNames.size()];
        paramNames.toArray(params);
        return params;
    }

    /**
     * listSubBlocks
     * 
     * @param blockPath String
     * @return String[]
     */
    public String[] listSubBlocks(String blockPath) {
        ReportBlock currentBlock = getBlock(blockPath);
        if (currentBlock == null) {
            return null;
        }
        String[] blocknames = new String[currentBlock.children.size()];
        int c = 0;
        for (ReportBlock block : currentBlock.children) {
            blocknames[c++] = block.name;
        }
        return blocknames;
    }

    /**
     * reportSize
     * 
     * @return int
     */
    public int reportSize() {
        if (root == null) {
            return 0;
        } else {
            return root.getSize() + 8;
        }
    }

    /**
     * writeReport
     * 
     * @param buff ByteBuffer
     */
    public void writeReport(ByteBuffer buff) {
        buff.putLong(expirationDate);
        if (root == null) {
            return;
        } else {
            root.write(buff);
        }
    }

    public String rootName() {
        if (root == null) {
            return null;
        } else {
            return root.name;
        }
    }

    public void dump(PrintStream outstrm) {
        root.dump(outstrm, "");
    }
}
