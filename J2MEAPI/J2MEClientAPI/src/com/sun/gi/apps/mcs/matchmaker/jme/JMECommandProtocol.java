package com.sun.gi.apps.mcs.matchmaker.jme;

import com.sun.gi.utils.jme.ByteBuffer;
import com.sun.gi.utils.jme.SGSUUIDJMEImpl;

import java.io.UnsupportedEncodingException;
import java.util.Vector;

/**
 * 
 * <p>Title: CommandProtocol</p>
 * 
 * <p>Description: </p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public final class JMECommandProtocol {
	
	private static final long serialVersionUID = 1L;
	
	public final static String LOBBY_MANAGER_CONTROL_CHANNEL = "LobbyManagerControl";
	
	// The command codes are unsigned bytes, but since Java doesn't have unsigned bytes
	// ints are used to fit the range.
	
	/**
	 * Sent to the client to notify that the server is ready to accept commands. 
	 */
	public final static int SERVER_LISTENING = 0x10;
	
	public final static int LIST_FOLDER_REQUEST = 0x70;
	public final static int LIST_FOLDER_RESPONSE = 0x71;
	
	public final static int LOOKUP_USER_ID_REQUEST = 0x72;
	public final static int LOOKUP_USER_ID_RESPONSE = 0x73;

	public final static int LOOKUP_USER_NAME_REQUEST = 0x74;
	public final static int LOOKUP_USER_NAME_RESPONSE = 0x75;
	
	public final static int FIND_USER_REQUEST = 0x76;
	public final static int LOCATE_USER_RESPONSE = 0x77;
	
	public final static int JOIN_LOBBY = 0x78;
	public final static int JOIN_GAME = 0x79;
	public final static int PLAYER_ENTERED_LOBBY = 0x90;
	
	public final static int SEND_TEXT = 0x91;
	
	public final static int GAME_PARAMETERS_REQUEST = 0x94;
	public final static int GAME_PARAMETERS_RESPONSE = 0x95;
	
	public final static int CREATE_GAME = 0x96;
	public final static int CREATE_GAME_FAILED = 0x97;
	public final static int GAME_CREATED = 0x98;
	
	public final static int PLAYER_JOINED_GAME = 0x9B;			// sent to lobby
	public final static int PLAYER_ENTERED_GAME = 0xB0;		// sent to game room
	public final static int PLAYER_LEFT_GAME = 0x9C;			// sent to lobby
	
	public final static int GAME_DELETED = 0x99;				// sent to lobby
	
	// type codes
	private final static int TYPE_INTEGER = 0x1;
	private final static int TYPE_BOOLEAN = 0x2;
	private final static int TYPE_STRING = 0x3;
	private final static int TYPE_BYTE = 0x4;
	private final static int TYPE_UUID = 0x5;
	
	public JMECommandProtocol() {}
	
	/**
	 * <p>Assembles a response based on the given list.  The list is disassembled
	 * in order and packed into a ByteBuffer according to type.  The contents of the
	 * list should be:</p>
	 * 
	 * <ul>
	 * <li>The Command Code as an Integer, which will be packed into the bufferas an unsigned byte.</li>
	 * <li>Strings are packed as bytes with a leading int indicating its length.</li>
	 * <li>UUIDs are packed as bytes with a leading int indicated its length.</li>
	 * <li>Booleans are stored as ints with 1 being true, and 0 being false.</li>
	 * </ul>
	 * 
	 * @param list			the list of objects to pack into the buffer
	 * 
	 * @return a ByteBuffer representing the contents of the list.
	 */
	public ByteBuffer assembleCommand(Vector list) {
		Vector byteList = new Vector();
                Integer firstElement = (Integer)list.elementAt(0);
		byteList.addElement(getUnsignedByte(firstElement));
		int bufferSize = 1;
		
		// this first pass is twofold:  to get the buffer size, and to
		// convert the input list to lengths (in ints) and byte arrays.
		for (int i = 1; i < list.size(); i++) {
			Object curObj = list.elementAt(i);
			
			// if the obj is null, translate that to a zero length "nothing" as a placeholder.
			if (curObj == null) {
				bufferSize += 4;
				byteList.addElement(new Integer(0));
				continue;
			}
			if (curObj instanceof String) {
				byte[] strBytes = ((String) curObj).getBytes();
				byteList.addElement(new Integer(strBytes.length));
				byteList.addElement(strBytes);
				bufferSize += 4 + strBytes.length;
			}
			else if (curObj instanceof SGSUUIDJMEImpl) {
				SGSUUIDJMEImpl id = (SGSUUIDJMEImpl) list.elementAt(i);
				byte[] idBytes = id.toByteArray();
				byteList.addElement(new Byte((byte) idBytes.length));
				byteList.addElement(idBytes);
				bufferSize += 1 + idBytes.length;
			}
			else if (curObj instanceof Boolean) {
				boolean b = ((Boolean) curObj).booleanValue();
				byteList.addElement(b ? new Integer(1) : new Integer(0));
				bufferSize += 4;
			}
			else if (curObj instanceof Integer) {
				byteList.addElement(curObj);
				bufferSize += 4;
			}
			else if (curObj instanceof UnsignedByte) {
				byteList.addElement(new Byte(((UnsignedByte) curObj).byteValue()));
				bufferSize++;
			}
		}
		
		// now that we know the buffer size, allocate the buffer
		// and pack it with the contents of the byte list.
		ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
                int size = byteList.size();
		for (int i = 0;i < size;i++) {
                    Object curObj = byteList.elementAt(i);
			if (curObj instanceof Byte) {
				//System.out.println("adding Byte " + curObj);
				buffer.put(((Byte) curObj).byteValue());
			}
			else if (curObj instanceof Integer) {
				//System.out.println("adding Integer " + curObj);
				buffer.putInt(((Integer) curObj).intValue());;
			}
			else if (curObj instanceof byte[]) {
				//System.out.println("adding byte[] " + ((byte[]) curObj).length);
				buffer.put((byte[]) curObj);
			}
		}
		return buffer;
	}
	
	public Vector createCommandList(int commandCode) {
		Vector list = new Vector();
		list.addElement(new Integer(commandCode));
		
		return list;
	}
	
	/**
	 * Converts the given int to an unsigned byte (by downcasting).
	 * 
	 * @param b			the byte stored as an int
	 * 
	 * @return the same value returned as a byte unsigned.
	 */
	private Byte getUnsignedByte(Integer b) {
		return new Byte((byte) b.intValue());
	}	
	
	/**
	 * Reads a UUID from the current position on the ByteBuffer
	 * and returns a UserID.  The first byte read is the length
	 * of the UUID.
	 * 
	 * @param data		the ByteBuffer containing the UUID info.
	 * 
	 * @return an UserID based on the UUID read from the buffer.
	 */
	public SGSUUIDJMEImpl readUserID(ByteBuffer data) {
		byte[] uuid = readBytes(data, true);
		
		SGSUUIDJMEImpl id = null;
		try {
			id = new SGSUUIDJMEImpl(uuid);
		}
		catch (InstantiationException ie) {
			ie.printStackTrace();
		}
		
		return id;
	}
	
	/**
	 * Reads an SGSUUID from the given ByteBuffer.
	 * 
	 * @param data			the buffer to read the UUID from
	 * 
	 * @return an SGSUUID read from the given buffer
	 */
	public SGSUUIDJMEImpl readUUID(ByteBuffer data) {
		byte[] uuid = readBytes(data, true);
		if (uuid.length == 0) {
			return null;
		}
		SGSUUIDJMEImpl sgsuuid = null;
		try {
			sgsuuid = new SGSUUIDJMEImpl(uuid);
		}
		catch (InstantiationException ie) {
			ie.printStackTrace();
		}
		return sgsuuid;
	}
	
	public String readString(ByteBuffer data) {
		String str = null;
		byte[] stringBytes = readBytes(data, false);
		try {
			str = new String(stringBytes, "UTF-8");
		}
		catch (UnsupportedEncodingException uee) {
			uee.printStackTrace();
		}
		
		return str;
	}
	
	/**
	 * Reads the next int off the buffer and returns true if it
	 * equals 1, otherwise false.
	 * 
	 * @param data		the data buffer
	 * 
	 * @return true if the int read equals one.
	 */
	public boolean readBoolean(ByteBuffer data) {
		return data.getInt() == 1;
	}
	
	/**
	 * Reads the next int off the given ByteBuffer, interpreting it as a size,
	 * and reads that many more bytes from the buffer, returning the resulting
	 * byte array.
	 * 
	 * @param data		the buffer to read from
	 * 
	 * @return a byte array matching the length of the first int read from the buffer
	 */
	public byte[] readBytes(ByteBuffer data, boolean isLengthByte) {
		int length = isLengthByte ? readUnsignedByte(data) : data.getInt();
		byte[] bytes = new byte[length];
		data.get(bytes);
		
		return bytes;
	}
	
	/**
	 * Reads the next unsigned byte off the buffer, maps it to a type,
	 * and reads the resulting object off the buffer as that type. 
	 * 
	 * @param data			the buffer to read from
	 * 
	 * @return an object matching the type specified from the initial byte
	 */
	public Object readParamValue(ByteBuffer data) {
		int type = readUnsignedByte(data);
		if (type == TYPE_BOOLEAN) {
			return new Boolean(readBoolean(data));
		}
		else if (type == TYPE_BYTE) {
			return new Integer(readUnsignedByte(data));
		}
		else if (type == TYPE_INTEGER) {
			return new Integer(data.getInt());
		}
		else if (type == TYPE_STRING) {
			return readString(data);
		}
		else if (type == TYPE_UUID) {
			return readUUID(data);
		}
		
		// unknown type
		return null;
	}
	
	/**
	 * Returns one of the CommandCodes.TYPE_X values based of the object type 
	 * of "value". 
	 * 
	 * @param value			the value to map to a type
	 * 
	 * @return of the the CommandCodes.TYPE_X static ints.
	 */
	public UnsignedByte mapType(Object value) {
		if (value instanceof Integer) {
			return new UnsignedByte(TYPE_INTEGER);
		}
		else if (value instanceof Boolean) {
			return new UnsignedByte(TYPE_BOOLEAN);
		}
		else if (value instanceof String) {
			return new UnsignedByte(TYPE_STRING);
		}
		else if (value instanceof UnsignedByte) {
			return new UnsignedByte(TYPE_BYTE);
		}
		else if (value instanceof SGSUUIDJMEImpl) {
			return new UnsignedByte(TYPE_UUID);
		}
		
		// unknown, or unsupported type.
		return new UnsignedByte(0);
			
	}	
	
	
	/**
	 * Reads a regular old Java signed byte off the buffer and converts 
	 * it to an unsigned one (0-255).
	 *  
	 * @param data		the buffer from which to read
	 * 
	 * @return the unsigned representation of the next byte off the buffer (as an int).
	 */
	public int readUnsignedByte(ByteBuffer data) {
		return data.get() & 0xff;
	}
	

}

/**
 *  This class represents an unsigned byte value.  It uses
 *  an int to store the value since a regular Java signed byte
 *  isn't wide enough.  
 *  
 *  This class exists primarily to mark an int to be intrepreted
 *  as an unsigned byte instead of an int. 
 */
class UnsignedByte {
	
	private int value;
	
	UnsignedByte(int num) {
		this.value = num;
	}
	
	public float floatValue() {
		return value;
	}
	
	public long longValue() {
		return value;
	}
	
	public double doubleValue() {
		return value;
	}
	
	public int intValue() {
		return value;
	}
	
	public byte byteValue() {
		return (byte) value;
	}
	
	public boolean equals(Object obj) {
		return (obj instanceof UnsignedByte && ((UnsignedByte) obj).intValue() == value);
	}
	
	public int hashCode() {
		return value;
	}
	
	public int compareTo(UnsignedByte b) {
		return b.intValue() == value ? 0 : b.intValue() > value ? -1 : 1;
	}
        
	
}
