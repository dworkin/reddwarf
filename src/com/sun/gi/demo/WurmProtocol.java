package com.sun.gi.demo;

import java.io.*;

import com.sun.gi.comm.routing.*;
import com.sun.gi.logic.*;
import com.wurmonline.client.comm.*;
import com.wurmonline.client.gui.*;

public class WurmProtocol {
  public static final byte OP_SERVER_MESSAGE = 1;
  public static final byte OP_PLAYER_POS = 2;
  public static final byte OP_PLAYER_MESSAGE = 3;
  private static final byte OP_MESSAGE_TO_PLAYER = 4;
  private static final byte OP_LOGIN_RESULT = 5;
  private static final byte OP_ADD_CREATURE = 6;
  private static final byte OP_MOVE_CREATURE = 7;
  private static final byte OP_PLAYER_LIST_REQ =8;
  private static final byte OP_PLAYER_LIST_RESPONSE = 9;
  private static final byte OP_WIZ_OZ_ATTACK = 10;
  private static final byte OP_REMOVE_CREATURE = 11;
  /**
   * makeMessageToPlayer
   *
   * @param type int
   * @param string String
   * @return byte[]
   */
  public static byte[] makeMessageToPlayer(int type, String sender,
                                           String message) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oas = new ObjectOutputStream(baos);
      oas.writeByte(OP_MESSAGE_TO_PLAYER);
      oas.writeInt(type);
      oas.writeUTF(sender);
      oas.writeUTF(message);
      oas.flush();
      byte[] buff = baos.toByteArray();
      oas.close();
      baos.close();
      return buff;
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  public static byte[] makeServerMessage(int type, String message) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oas = new ObjectOutputStream(baos);
      oas.writeByte(OP_SERVER_MESSAGE);
      oas.writeInt(type);
      oas.writeUTF(message);
      oas.flush();
      byte[] buff = baos.toByteArray();
      oas.close();
      baos.close();
      return buff;
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  public static byte[] makeLoginResult(boolean result, String message,
                                       float x, float y, float z, float yrot,
                                       long serverTime) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oas = new ObjectOutputStream(baos);
      oas.writeByte(OP_LOGIN_RESULT);
      oas.writeBoolean(result);
      System.out.println("MAking the fricking UTF!");
      oas.writeUTF(message);
      oas.writeFloat(x);
      oas.writeFloat(y);
      oas.writeFloat(z);
      oas.writeFloat(yrot);
      oas.writeLong(serverTime);
      oas.flush();
      byte[] buff = baos.toByteArray();
      oas.close();
      baos.close();
      return buff;
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }

  }

  public static byte[] makePlayerMessage(int type, String message,
                                         String receiver) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oas = new ObjectOutputStream(baos);
      oas.writeByte(OP_PLAYER_MESSAGE);
      oas.writeInt(type);
      oas.writeUTF(message);
      oas.writeUTF(receiver);
      oas.flush();
      byte[] buff = baos.toByteArray();
      oas.close();
      baos.close();
      return buff;
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  public static byte[] makePlayerPosPacket(float x, float y, float z,
                                           float xrot,
                                           float yrot) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oas = new ObjectOutputStream(baos);
      oas.writeByte(OP_PLAYER_POS);
      oas.writeFloat(x);
      oas.writeFloat(y);
      oas.writeFloat(z);
      oas.writeFloat(xrot);
      oas.writeFloat(yrot);
      oas.flush();
      byte[] buff = baos.toByteArray();
      oas.close();
      baos.close();
      return buff;
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  public static byte[] makeMoveCreaturePacket(long id, byte dx, byte dy,
                                              byte dz, byte drot) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oas = new ObjectOutputStream(baos);
      oas.writeByte(OP_MOVE_CREATURE);
      oas.writeLong(id);
      oas.writeByte(dx);
      oas.writeByte(dy);
      oas.writeByte(dz);
      oas.writeByte(drot);
      oas.flush();
      byte[] buff = baos.toByteArray();
      oas.close();
      baos.close();
      return buff;
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  public static byte[] makePlayerListRequest() {
   try {
     ByteArrayOutputStream baos = new ByteArrayOutputStream();
     ObjectOutputStream oas = new ObjectOutputStream(baos);
     oas.writeByte(OP_PLAYER_LIST_REQ);
     oas.flush();
     byte[] buff = baos.toByteArray();
     oas.close();
     baos.close();
     return buff;
   }
   catch (Exception e) {
     e.printStackTrace();
     return null;
   }
 }

 public static byte[] makePlayerListResponse(String[] names) {
   try {
     ByteArrayOutputStream baos = new ByteArrayOutputStream();
     ObjectOutputStream oas = new ObjectOutputStream(baos);
     oas.writeByte(OP_PLAYER_LIST_REQ);
     oas.writeInt(names.length);
     for(int i= 0;i<names.length;i++){
       oas.writeUTF(names[i]);
     }
     oas.flush();
     byte[] buff = baos.toByteArray();
     oas.close();
     baos.close();
     return buff;
   }
   catch (Exception e) {
     e.printStackTrace();
     return null;
   }
 }


 public static byte[] makeWizOzAttack(String name) {
   try {
     ByteArrayOutputStream baos = new ByteArrayOutputStream();
     ObjectOutputStream oas = new ObjectOutputStream(baos);
     oas.writeByte(OP_PLAYER_LIST_REQ);
     oas.writeUTF(name);
     oas.flush();
     byte[] buff = baos.toByteArray();
     oas.close();
     baos.close();
     return buff;
   }
   catch (Exception e) {
     e.printStackTrace();
     return null;
   }
 }



  /**
    * makeRemoveCreaturePacket
    *
    * @param l long
    * @return byte[]
    */
   public static byte[] makeRemoveCreaturePacket(long id) {
     try {
     ByteArrayOutputStream baos = new ByteArrayOutputStream();
     ObjectOutputStream oas = new ObjectOutputStream(baos);
     oas.writeByte(OP_REMOVE_CREATURE);
     oas.writeLong(id);
     oas.flush();
     byte[] buff = baos.toByteArray();
     oas.close();
     baos.close();
     return buff;
   }
   catch (Exception e) {
     e.printStackTrace();
     return null;
   }

   }

  public static byte[] makeAddCreaturePacket(long wurmID, String modelName,
                                             String creatureName,float x,
                                             float y, float z, float yrot) {
   try {
     ByteArrayOutputStream baos = new ByteArrayOutputStream();
     ObjectOutputStream oas = new ObjectOutputStream(baos);
     oas.writeByte(OP_ADD_CREATURE);
     oas.writeLong(wurmID);
     oas.writeUTF(modelName);
     oas.writeUTF(creatureName);
     oas.writeFloat(x);
     oas.writeFloat(y);
     oas.writeFloat(z);
     oas.writeFloat(yrot);
     oas.flush();
     byte[] buff = baos.toByteArray();
     oas.close();
     baos.close();
     return buff;
   }
   catch (Exception e) {
     e.printStackTrace();
     return null;
   }
 }


  public static void playerProcess(ServerConnectionListener listener,
                                   byte[] data,
                                   int length) {
    ByteArrayInputStream bais = null;
    ObjectInputStream ois = null;
    try {
      bais = new ByteArrayInputStream(data, 0, length);
      ois = new ObjectInputStream(bais);
      byte op = ois.readByte();
      System.out.println("Message op = " + op);
      switch (op) {
        case OP_SERVER_MESSAGE:
          int type = ois.readInt();
          String message = (String) ois.readUTF();
          listener.serverMessage(type, message);
          break;
        case OP_MESSAGE_TO_PLAYER:
          type = ois.readInt();
          String sender = (String) ois.readUTF();
          message = (String) ois.readUTF();
          listener.chatMessage(type, sender, message);
          break;
        case OP_LOGIN_RESULT:
          boolean loggedIn = ois.readBoolean();
          message =  ois.readUTF();
          float x = ois.readFloat();
          float y = ois.readFloat();
          float z = ois.readFloat();
          float yRot = ois.readFloat();
          long loginTime = ois.readLong();
          listener.loginResult(loggedIn, message, x, y, z, yRot, loginTime);
          break;
        case OP_ADD_CREATURE:
          long id = ois.readLong();
          String modelName = (String)ois.readUTF();
          String name = (String)ois.readUTF();
          x = ois.readFloat();
          y = ois.readFloat();
          z = ois.readFloat();
          yRot = ois.readFloat();
          listener.addCreature(new Creature(id, modelName, name, x, y, z, yRot));
          // for a MOJANG bug
          //listener.addCreature(new Creature(id, modelName, name, x, z,y, yRot));
          break;
        case OP_MOVE_CREATURE:
          id = ois.readLong();
          byte dx = ois.readByte();
          byte dy = ois.readByte();
          byte dz = ois.readByte();
          byte drot = ois.readByte();
          System.out.println("Move creature "+id+": "+dx+" "+dy+" "+dz+" "+drot);
          listener.moveCreature(id,dx,dy,dz,drot);
          break;
        case OP_REMOVE_CREATURE:
          id = ois.readLong();
          System.out.println("Remove creature "+id);
          listener.deleteCreature(id);
          break;
        case OP_PLAYER_LIST_RESPONSE:
          int count = ois.readInt();
          String[] strarray = new String[count];
          for(int i=0;i<count;i++){
            strarray[i] = ois.readUTF();
          }
          listener.playerList(strarray);
          break;
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    try {
      ois.close();
      bais.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void serverProcess(SimTask task, WurmClientListener listener,
                                   UserID from,
                                   byte[] data, int length) {
    ByteArrayInputStream bais = null;
    ObjectInputStream ois = null;
    try {
      bais = new ByteArrayInputStream(data, 0, length);
      ois = new ObjectInputStream(bais);
      byte op = ois.readByte();
      switch (op) {
        case OP_PLAYER_POS:
          float x = ois.readFloat();
          float y = ois.readFloat();
          float z = ois.readFloat();
          float xrot = ois.readFloat();
          float yrot = ois.readFloat();
          listener.setPlayerPos(task, from, x, y, z, xrot, yrot);
          break;
        case OP_PLAYER_MESSAGE:
          int type = ois.readInt();
          String msg = (String) ois.readUTF();
          String receiver = (String) ois.readUTF();
          listener.playerMessage(task, type, msg, receiver);
          break;
        case OP_PLAYER_LIST_REQ:
          listener.playerListRequest();
          break;
        case OP_WIZ_OZ_ATTACK:
          String target = ois.readUTF();
          listener.wizardOfOz(target);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    try {
      ois.close();
      bais.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }


}
