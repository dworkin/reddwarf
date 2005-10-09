package com.sun.gi.comm.users.validation;

import java.nio.ByteBuffer;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextInputCallback;
import java.util.List;
import java.util.ArrayList;

public class ValidationDataProtocol {
  public static final byte CB_TYPE_NAME = 1;
  public static final byte CB_TYPE_PASSWORD = 2;
  public static final byte CB_TYPE_TEXT_INPUT = 3;


  /**
   * makeRequestData
   *
   * @param callbacks Callback[]
   */
  public static void makeRequestData(ByteBuffer requestData,
                               Callback[] currentCallbacks) throws UnsupportedCallbackException {
    requestData.putInt(currentCallbacks.length);
    for (int i = 0; i < currentCallbacks.length; i++) {
      Callback cb = currentCallbacks[i];
      if (cb instanceof NameCallback) {
        requestData.put(CB_TYPE_NAME);
        byte[] prompt = ( (NameCallback) cb).getPrompt().getBytes();
        requestData.putInt(prompt.length);
        requestData.put(prompt);
      }
      else if (cb instanceof PasswordCallback) {
        requestData.put(CB_TYPE_PASSWORD);
        byte[] prompt = ( (PasswordCallback) cb).getPrompt().getBytes();
        requestData.putInt(prompt.length);
        requestData.put(prompt);
        requestData.put( (byte) ( ( (PasswordCallback) cb).isEchoOn() ? 1 : 0));
      }
      else if (cb instanceof TextInputCallback) {
        requestData.put(CB_TYPE_TEXT_INPUT);
        byte[] prompt = ( (NameCallback) cb).getPrompt().getBytes();
        requestData.putInt(prompt.length);
        requestData.put(prompt);
      }
      else {
        throw new UnsupportedCallbackException(cb);
      }
    }
  }

  /**

  /**
   * unpackRequestData
   *
   * @param buff ByteBuffer
   */
  public static Callback[] unpackRequestData(ByteBuffer requestData) {
    ByteBuffer buff = requestData;
    int callbackCount = buff.getInt();
    List callbackList = new ArrayList();
    for (int i = 0; i < callbackCount; i++) {
      Callback currentCallback = null;
      byte cbType = buff.get();
      switch (cbType) {
        case CB_TYPE_NAME:
          int strlen = buff.getInt();
          byte[] strbytes = new byte[strlen];
          buff.get(strbytes);
          String response = new String(strbytes);
          currentCallback = new NameCallback(response);
          break;
        case CB_TYPE_PASSWORD:
          strlen = buff.getInt();
          strbytes = new byte[strlen];
          buff.get(strbytes);
          response = new String(strbytes);
          currentCallback = new PasswordCallback(response,false);
          break;
        case CB_TYPE_TEXT_INPUT:
          strlen = buff.getInt();
          strbytes = new byte[strlen];
          buff.get(strbytes);
          response = new String(strbytes);
         currentCallback = new TextInputCallback(response);
          break;
        default:
          System.out.println("Error: Illegal login callback type: " + cbType);
          return null;
      }
      if (currentCallback != null) {
        callbackList.add(currentCallback);
      }
    }
    Callback[] cbArray = new Callback[callbackList.size()];
    callbackList.toArray(cbArray);
    return cbArray;
  }

}
