package com.sun.sgs.impl.service.data.store.net;

import java.io.IOException;
import java.io.EOFException;
import java.net.ServerSocket;
import java.net.Socket;

class DataStoreServerRemote implements Runnable {
    static final int ALLOCATE_OBJECTS = 1;
    static final int MARK_FOR_UPDATE = 2;
    static final int GET_OBJECT = 3;
    static final int SET_OBJECT = 4;
    static final int SET_OBJECTS = 5;
    static final int REMOVE_OBJECT = 6;
    static final int GET_BINDING = 7;
    static final int SET_BINDING = 8;
    static final int REMOVE_BINDING = 9;
    static final int NEXT_BOUND_NAME = 10;
    static final int CREATE_TRANSACTION = 11;
    static final int PREPARE = 12;
    static final int COMMIT = 13;
    static final int PREPARE_AND_COMMIT = 14;
    static final int ABORT = 15;
    ServerSocket serverSocket;
    private final DataStoreServer server;
    DataStoreServerRemote(int port, DataStoreServer server)
	throws IOException
    {
	serverSocket = new ServerSocket(port);
	this.server = server;
	new Thread(this, "DataStoreServerRemote").start();
    }
    synchronized void shutdown() throws IOException {
	if (serverSocket != null) {
	    serverSocket.close();
	    serverSocket = null;
	}
    }
    private synchronized boolean isShutdown() {
	return serverSocket == null;
    }
    public void run() {
	while (!isShutdown()) {
	    try {
		new Thread(
		    new Handler(serverSocket.accept()), "Handler").start();
	    } catch (EOFException e) {
		break;
	    } catch (Throwable t) {
		if (!isShutdown()) {
		    System.err.println(t);
		}
	    }
	}
    }
    private class Handler extends SocketIO implements Runnable {
	Handler(Socket socket) throws IOException {
	    super(socket);
	}
	public void run() {
	    while (true) {
		try {
		    dispatch();
		} catch (EOFException e) {
		    break;
		} catch (Throwable e) {
		    System.err.println(e);
		}
	    }
	}
	private void dispatch() throws IOException {
	    int op = in.readInt();
	    switch (op) {
	    case ALLOCATE_OBJECTS:
		handleAllocateObjects();
		break;
	    case MARK_FOR_UPDATE:
		handleMarkForUpdate();
		break;
	    case GET_OBJECT:
		handleGetObject();
		break;
	    case SET_OBJECT:
		handleSetObject();
		break;
	    case SET_OBJECTS:
		handleSetObjects();
		break;
	    case REMOVE_OBJECT:
		handleRemoveObject();
		break;
	    case GET_BINDING:
		handleGetBinding();
		break;
	    case SET_BINDING:
		handleSetBinding();
		break;
	    case REMOVE_BINDING:
		handleRemoveBinding();
		break;
	    case NEXT_BOUND_NAME:
		handleNextBoundName();
		break;
	    case CREATE_TRANSACTION:
		handleCreateTransaction();
		break;
	    case PREPARE:
		handlePrepare();
		break;
	    case COMMIT:
		handleCommit();
		break;
	    case PREPARE_AND_COMMIT:
		handlePrepareAndCommit();
		break;
	    case ABORT:
		handleAbort();
		break;
	    default:
		failure(new IOException("Unknown operation: " + op));
	    }
	}
	private void handleAllocateObjects() throws IOException {
	    try {
		int count = in.readInt();
		long result = server.allocateObjects(count);
		out.writeBoolean(true);
		out.writeLong(result);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleMarkForUpdate() throws IOException {
	    try {
		long tid = in.readLong();
		long oid = in.readLong();
		server.markForUpdate(tid, oid);
		out.writeBoolean(true);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleGetObject() throws IOException {
	    try {
		long tid = in.readLong();
		long oid = in.readLong();
		boolean forUpdate = in.readBoolean();
		byte[] result = server.getObject(tid, oid, forUpdate);
		out.writeBoolean(true);
		writeBytes(result);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleSetObject() throws IOException {
	    try {
		long tid = in.readLong();
		long oid = in.readLong();
		byte[] data = readBytes();
		server.setObject(tid, oid, data);
		out.writeBoolean(true);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleSetObjects() throws IOException {
	    try {
		long tid = in.readLong();
		long[] oids = readLongs();
		int dataArrayLength = in.readInt();
		byte[][] dataArray = new byte[dataArrayLength][];
		for (int i = 0; i < dataArrayLength; i++) {
		    dataArray[i] = readBytes();
		}
		server.setObjects(tid, oids, dataArray);
		out.writeBoolean(true);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleRemoveObject() throws IOException {
	    try {
		long tid = in.readLong();
		long oid = in.readLong();
		server.removeObject(tid, oid);
		out.writeBoolean(true);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleGetBinding() throws IOException {
	    try {
		long tid = in.readLong();
		String name = readString();
		long result = server.getBinding(tid, name);
		out.writeBoolean(true);
		out.writeLong(result);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleSetBinding() throws IOException {
	    try {
		long tid = in.readLong();
		String name = readString();
		long oid = in.readLong();
		server.setBinding(tid, name, oid);
		out.writeBoolean(true);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleRemoveBinding() throws IOException {
	    try {
		long tid = in.readLong();
		String name = readString();
		server.removeBinding(tid, name);
		out.writeBoolean(true);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleNextBoundName() throws IOException {
	    try {
		long tid = in.readLong();
		String name = readString();
		String result = server.nextBoundName(tid, name);
		out.writeBoolean(true);
		writeString(result);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleCreateTransaction() throws IOException {
	    try {
		long tid = server.createTransaction();
		out.writeBoolean(true);
		out.writeLong(tid);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}		
	private void handlePrepare() throws IOException {
	    try {
		long tid = in.readLong();
		boolean result = server.prepare(tid);
		out.writeBoolean(true);
		out.writeBoolean(result);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleCommit() throws IOException {
	    try {
		long tid = in.readLong();
		server.commit(tid);
		out.writeBoolean(true);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handlePrepareAndCommit() throws IOException {
	    try {
		long tid = in.readLong();
		server.prepareAndCommit(tid);
		out.writeBoolean(true);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
	private void handleAbort() throws IOException {
	    try {
		long tid = in.readLong();
		server.abort(tid);
		out.writeBoolean(true);
		out.flush();
	    } catch (Throwable t) {
		failure(t);
	    }
	}
    }
}
