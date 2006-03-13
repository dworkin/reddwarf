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

/*
 * SGSUUIDJMEImpl.java
 *
 * Created on March 6, 2006, 8:54 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.gi.utils.jme;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

/**
 *
 * @author as93050
 */
public class SGSUUIDJMEImpl {
    
    static Random random = null;
    private long randomValue;
    private long timeValue;
    
    public SGSUUIDJMEImpl(long time, long tiebreaker){
        timeValue = time;
        randomValue = tiebreaker;
    }
    
    public SGSUUIDJMEImpl() {
        if (random == null) {
            random = new Random();            
        }
        randomValue = random.nextLong();
        timeValue = System.currentTimeMillis();
    }
    
    /**
     * StatisticalUUID
     *
     * @param bs byte[]
     */
    public SGSUUIDJMEImpl(byte[] bs) throws InstantiationException {
        if (bs.length != 16) {
            throw new InstantiationException();
        }
        randomValue = ((( (long) bs[0])&0xFF) << 56) | ( (( (long) bs[1])&0xFF) << 48) |
                ( (( (long) bs[2])&0xFF) << 40) |
                ( (( (long) bs[3])&0xFF) << 32) |
                ( (( (long) bs[4])&0xFF) << 24) | ( (( (long) bs[5])&0xFF) << 16) |
                ( (( (long) bs[6])&0xFF) << 8) | ((long) bs[7]&0xFF);
        timeValue = ((( (long) bs[8])&0xFF) << 56) | ( (( (long) bs[9])&0xFF) << 48) |
                ( (( (long) bs[10])&0xFF) << 40) |
                ( (( (long) bs[11])&0xFF) << 32) |
                ( (( (long) bs[12])&0xFF) << 24) | ( (( (long) bs[13])&0xFF) << 16) |
                ( (( (long) bs[14])&0xFF) << 8) | ((long) bs[15]&0xFF);
        
    }
    
    /**
     * StatisticalUUID
     *
     * @param byteBuffer ByteBuffer
     */
    public SGSUUIDJMEImpl(ByteBuffer byteBuffer) {
        timeValue = byteBuffer.getLong();
        randomValue = byteBuffer.getLong();
    }
    
    /**
     * @param uid
     */
    public SGSUUIDJMEImpl(SGSUUIDJMEImpl uid) {
        this(uid.timeValue,uid.randomValue);
    }
    
    public int compareTo(Object object) {
        long otherTime=0;
        long otherRand=0;
        if (object instanceof SGSUUIDJMEImpl){
            SGSUUIDJMEImpl other = (SGSUUIDJMEImpl) object;
            otherTime = other.timeValue;
            otherRand = other.randomValue;
        } else if (object instanceof byte[]){
            byte[] bs = (byte[])object;
            otherRand = ((( (long) bs[0])&0xFF) << 56) | ( (( (long) bs[1])&0xFF) << 48) |
                    ( (( (long) bs[2])&0xFF) << 40) |
                    ( (( (long) bs[3])&0xFF) << 32) |
                    ( (( (long) bs[4])&0xFF) << 24) | ( (( (long) bs[5])&0xFF) << 16) |
                    ( (( (long) bs[6])&0xFF) << 8) | ((long) bs[7]&0xFF);
            otherTime = ((( (long) bs[8])&0xFF) << 56) | ( (( (long) bs[9])&0xFF) << 48) |
                    ( (( (long) bs[10])&0xFF) << 40) |
                    ( (( (long) bs[11])&0xFF) << 32) |
                    ( (( (long) bs[12])&0xFF) << 24) | ( (( (long) bs[13])&0xFF) << 16) |
                    ( (( (long) bs[14])&0xFF) << 8) | ((long) bs[15]&0xFF);
        } else {
            throw new RuntimeException(
                    "Statistical UUID may only be compared to same or a byte[]");
        }
        SGSUUIDJMEImpl other = (SGSUUIDJMEImpl) object;
        if (timeValue < otherTime) {
            return -1;
        } else if (timeValue > otherTime) {
            return 1;
        } else {
            if (randomValue < otherRand) {
                return -1;
            } else if (randomValue > otherRand) {
                return 1;
            }
        }
        return 0;
    }
    
    public String toString() {
        return ("UUID(" + timeValue + ":" + randomValue + ")");
    }
    
    public int hashCode() {
        return ( (int) ( (timeValue >> 32) & 0xFFFFFFFF)) ^
                ( (int) (timeValue & 0xFFFFFFFF))
                ^ ( (int) ( (randomValue) >> 32) & 0xFFFFFFFF) ^
                ( (int) (randomValue & 0xFFFFFFFF));
    }
    
    public boolean equals(Object obj) {
        return ( compareTo(obj)==0);
    }
    
    /**
     * read
     *
     * @param buffer ByteBuffer
     */
    public void read(ByteBuffer buffer) {
        timeValue = buffer.getLong();
        randomValue = buffer.getLong();
    }
    
    /**
     * read
     *
     * @param strm InputStream
     */
    public void read(InputStream strm) {
        DataInputStream dais = new DataInputStream(strm);
        try {
            timeValue = dais.readLong();
            randomValue = dais.readLong();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * write
     *
     * @param buffer ByteBuffer
     */
    public void write(ByteBuffer buffer) {
        buffer.putLong(timeValue);
        buffer.putLong(randomValue);
    }
    
    /**
     * write
     *
     * @param strm OutputStream
     */
    public void write(OutputStream strm) {
        DataOutputStream daos = new DataOutputStream(strm);
        try {
            daos.writeLong(timeValue);
            daos.writeLong(randomValue);
            daos.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * ioByteSize
     *
     * @return int
     */
    public int ioByteSize() {
        return 16; // when writing our reading in this value we use 8 bytes
    }
    
    /**
     * toByteArray
     *
     * @return byte[]
     */
    public byte[] toByteArray() {
        byte[] bytes = new byte[16];
        bytes[0] = (byte) ( (randomValue >> 56) & 0xff);
        bytes[1] = (byte) ( (randomValue >> 48) & 0xFF);
        bytes[2] = (byte) ( (randomValue >> 40) & 0xff);
        bytes[3] = (byte) ( (randomValue >> 32) & 0xff);
        bytes[4] = (byte) ( (randomValue >> 24) & 0xff);
        bytes[5] = (byte) ( (randomValue >> 16) & 0xff);
        bytes[6] = (byte) ( (randomValue >> 8) & 0xff);
        bytes[7] = (byte) ( (randomValue) & 0xff);
        bytes[8] = (byte) ( (timeValue >> 56) & 0xff);
        bytes[9] = (byte) ( (timeValue >> 48) & 0xff);
        bytes[10] = (byte) ( (timeValue >> 40) & 0xff);
        bytes[11] = (byte) ( (timeValue >> 32) & 0xff);
        bytes[12] = (byte) ( (timeValue >> 24) & 0xff);
        bytes[13] = (byte) ( (timeValue >> 16) & 0xff);
        bytes[14] = (byte) ( (timeValue >> 8) & 0xff);
        bytes[15] = (byte) ( (timeValue) & 0xff);
        return bytes;
    }
    
    
}
