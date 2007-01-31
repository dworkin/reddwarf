package com.sun.sgs.impl.service.data.store.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.Socket;

class SocketIO {
    final Socket socket;
    final DataInputStream in;
    final DataOutputStream out;
    SocketIO(Socket socket) throws IOException {
	this.socket = socket;
	in = new DataInputStream(
	    new BufferedInputStream(
		socket.getInputStream()));
	out = new DataOutputStream(
	    new BufferedOutputStream(
		socket.getOutputStream()));
    }
    void checkResult() throws IOException {
	out.flush();
	boolean ok = in.readBoolean();
	if (ok) {
	    return;
	}
	String className = in.readUTF();
	String message = readString();
	Throwable exception;
	try {
	    Class<? extends Throwable> exceptionClass =
		Class.forName(className).asSubclass(Throwable.class);
	    Constructor<? extends Throwable> constructor
		= exceptionClass.getConstructor(String.class);
	    exception = constructor.newInstance(message);
	} catch (Exception e) {
	    e.printStackTrace();
	    IOException ioe = new IOException(
		"Problem deserializing exception: " + e);
	    ioe.initCause(e);
	    throw ioe;
	}
	if (exception instanceof IOException) {
	    throw (IOException) exception;
	} else if (exception instanceof RuntimeException) {
	    throw (RuntimeException) exception;
	} else {
	    throw (Error) exception;
	}
    }
    void failure(Throwable t) throws IOException {
	out.writeBoolean(false);
	out.writeUTF(t.getClass().getName());
	writeString(t.getMessage());
	out.flush();
    }
    byte[] readBytes() throws IOException {
	int numBytes = in.readInt();
	byte[] result = new byte[numBytes];
	in.readFully(result);
	return result;
    }
    void writeBytes(byte[] bytes) throws IOException {
	out.writeInt(bytes.length);
	out.write(bytes);
    }
    String readString() throws IOException {
	return in.readBoolean() ? in.readUTF() : null;
    }
    void writeString(String string) throws IOException {
	if (string == null) {
	    out.writeBoolean(false);
	} else {
	    out.writeBoolean(true);
	    out.writeUTF(string);
	}
    }
    void writeLongs(long[] array) throws IOException {
	int len = array.length;
	out.writeInt(len);
	for (int i = 0; i < len; i++) {
	    out.writeLong(array[i]);
	}
    }
    long[] readLongs() throws IOException {
	int len = in.readInt();
	long[] result = new long[len];
	for (int i = 0; i < len; i++) {
	    result[i] = in.readLong();
	}
	return result;
    }
}
