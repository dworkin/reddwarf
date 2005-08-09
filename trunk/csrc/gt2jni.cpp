// File: gt2jni.cpp
// Purpose: JNI wrappers for Java on gt2
#include <stdlib.h>
#include <stdio.h>
#include <jni.h>
#include <gt2.h>
#include "com_sun_gi_gamespy_jni_Transport.h"
#include <gt2connection.h>

#define DEBUG FALSE
// callback prototypes
void JNICBSocketError(GT2Socket socket);
void JNICBConnected(GT2Connection connection, GT2Result result,
                  GT2Byte *message, int len);
void JNICBReceived(GT2Connection connection, GT2Byte *message, int len,
                       GT2Bool reliable);
void JNICBClosed(GT2Connection connection, GT2CloseReason reason);
void JNICBPing(GT2Connection connection, int latency);
void JNICBConnectAttempt(GT2Socket socket, GT2Connection connection,
                         unsigned int ip, unsigned short port, int latency,
                         GT2Byte *message, int len);

// These are used by the code multiple times
static int lastResult = 0;
static jclass javaClass = NULL;
static JNIEnv* javaEnv = NULL;
static GT2ConnectionCallbacks connectionCallbacks = {JNICBConnected,JNICBReceived,
                                                     JNICBClosed,JNICBPing};
static gt2SocketErrorCallback socketErrorCallback = JNICBSocketError;
static gt2ConnectAttemptCallback connectAttemptCallback = JNICBConnectAttempt;


JNIEXPORT jlong JNICALL Java_com_sun_gi_gamespy_JNITransport_lastResult
  (JNIEnv *env, jclass cl)
  {
    if (DEBUG){
      printf(">>> last result\n");
    }
    return (jlong)lastResult;
}


/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2Accept
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2Accept
  (JNIEnv *env, jclass cl, jlong connaddr)
  {
    if (DEBUG){
      printf(">>> accept\n");
    }

    GT2Connection conn = (GT2Connection)connaddr;

    gt2Accept(conn,&connectionCallbacks);
  }

/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2CloseAllConnections
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2CloseAllConnections
  (JNIEnv *env, jclass cl, jlong socketaddr)
  {
    if (DEBUG){
      printf(">>> closeAllConnections\n");
    }
    GT2Socket socket =   (GT2Socket)socketaddr;
    gt2CloseAllConnections(socket);
  }

/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2CloseAllConnectionsHard
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2CloseAllConnectionsHard
  (JNIEnv *env, jclass cl, jlong socketaddr)
  {
    if (DEBUG){
      printf(">>> closeAllConnectionsHard\n");
    }

   GT2Socket socket =   (GT2Socket)socketaddr;
   gt2CloseAllConnectionsHard(socket);
 }


/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2CloseConnection
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2CloseConnection
  (JNIEnv *env, jclass cl, jlong connaddr){
    if (DEBUG){
      printf(">>> closeConnection\n");
    }
    GT2Connection conn = (GT2Connection )connaddr;
    gt2CloseConnection(conn);
  }

/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2CloseConnectionHard
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2CloseConnectionHard
(JNIEnv *env, jclass cl, jlong connaddr){
  if (DEBUG){
      printf(">>> closeConnectionHard\n");
    }

  GT2Connection conn = (GT2Connection)connaddr;
  gt2CloseConnectionHard(conn);
}


/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2CloseSocket
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2CloseSocket
  (JNIEnv *env, jclass cl , jlong socketaddr)
  {
    if (DEBUG){
      printf(">>> close socket\n");
    }
    GT2Socket socket = (GT2Socket)socketaddr;
    gt2CloseSocket(socket);
  }

/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2Connect
 * Signature: (JLjava/lang/String;[BII)J
 */
JNIEXPORT jlong JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2Connect
  (JNIEnv *env, jclass cl, jlong socketaddr, jstring remoteAddr,
  jbyteArray message, jint len, jint timeout){
    javaEnv = env;
    javaClass = cl;
    if (DEBUG){
      printf(">>> Connect\n");
    }
    GT2Socket socket = (GT2Socket)socketaddr;
    const jbyte *utfRemoteAddress =
      env->GetStringUTFChars(remoteAddr,NULL);
    GT2Connection conn;
    jbyte *msg;
    msg = env->GetByteArrayElements(message,NULL);
    lastResult = gt2Connect(socket,&conn,utfRemoteAddress,msg,
                        len,timeout,&connectionCallbacks,GT2True);
    env->ReleaseStringUTFChars(remoteAddr,utfRemoteAddress);
    env->ReleaseByteArrayElements(message,msg,0);
    if (lastResult == GT2Success){
      return (jlong)conn;
    } else {
      return NULL;
    }
  }

/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2CreateSocket
 * Signature: (Ljava/lang/String;II)J
 */
JNIEXPORT jlong JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2CreateSocket
  (JNIEnv *env, jclass cl, jstring jlocalAddress , jint outBufferSz,
    jint inBufferSz){
      if (DEBUG){
         printf(">>> CReate Socket\n");
       }

    GT2Socket socket;
    const jbyte *utfLocalAddress =
      env->GetStringUTFChars(jlocalAddress,NULL);
    lastResult = gt2CreateSocket(&socket,utfLocalAddress,outBufferSz,
                    inBufferSz,socketErrorCallback);
    env->ReleaseStringUTFChars(jlocalAddress,utfLocalAddress);
    if (lastResult == GT2Success){
      return (jlong)socket;
    } else{
      return NULL;
    }
  }

/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2Listen
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2Listen
  (JNIEnv *env, jclass cl, jlong socketaddr){
    if (DEBUG){
      printf(">>> Listen\n");
    }

    GT2Socket socket = (GT2Socket)socketaddr;
    gt2Listen(socket,JNICBConnectAttempt);
  }

/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2Reject
 * Signature: (J[BI)V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2Reject
  (JNIEnv *env, jclass cl, jlong connectionaddr, jbyteArray message,
  jint len){
    jbyte *msg;
    msg = env->GetByteArrayElements(message,NULL);
    if (DEBUG){
      printf(">>> reject: (%d) %s\n",len,msg);
    }
    GT2Connection conn = (GT2Connection)connectionaddr;
    gt2Reject(conn, msg,len);
    env->ReleaseByteArrayElements(message,msg,0);
  }

/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2Send
 * Signature: (J[BZ)V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2Send
  (JNIEnv *env, jclass cl, jlong connaddr, jbyteArray message, jint len,
  jboolean jreliable){
    javaEnv=env;
    javaClass = cl;
    if (DEBUG){
      printf(">>> Send\n");
    }
    jbyte *msg;
    msg = env->GetByteArrayElements(message,NULL);
    GT2Connection conn = (GT2Connection)connaddr;
    int reliable  = (jreliable == JNI_TRUE);
    gt2Send(conn,msg,len,reliable);
    env->ReleaseByteArrayElements(message,msg,0);
  }

/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2Think
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2Think
  (JNIEnv *env, jclass cl, jlong socketaddr){
    //if (DEBUG){
    //  printf(">>> Think\n");
    //}
    GT2Socket socket = (GT2Socket)socketaddr;
    // set up globals first
    // store class and environment for calling back
    javaClass = cl;
    javaEnv = env;
    // now call gamespy
    gt2Think(socket); // give it some processor time
  }

/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_init
  (JNIEnv *env, jclass cl){
    if (DEBUG){
      printf(">>> init\n");
    }
    javaEnv = env;
    javaClass = cl;

    // do nothing for now
  }

/*
 * Class:     com_sun_gi_gamespy_jni_Transport
 * Method:    gt2Ping
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_sun_gi_gamespy_JNITransport_gt2Ping
  (JNIEnv *env, jclass cl, jlong connectionHandle){
    GT2Connection conn = (GT2Connection)connectionHandle;
    gt2Ping(conn);
  }



// Callbacks from gamespy

void JNICBSocketError(GT2Socket socket){
  if (DEBUG){
      printf(">>> SOcket Error CB\n");
    }
  jmethodID mid = javaEnv->GetStaticMethodID(javaClass,"gt2SocketErrorCallback",
                    "(J)V");
  if (mid == NULL){
    printf("Error: callback gt2SocketErrorCallback:(J)V  not found");
    return;
  }
  javaEnv->CallStaticVoidMethod(javaClass,mid,socket);
}

void JNICBConnected(GT2Connection connection, GT2Result result,
                  GT2Byte *message, int len){
  if (DEBUG){
    printf(">>> Connected CB: %s\n",message);
  }

  jmethodID mid = javaEnv->GetStaticMethodID(javaClass,"gt2ConnectedCallback",
                      "(JI[BI)V");
  if (mid == NULL){
    printf("Error: callback gt2ConnectedCallback:(JI[BI)V  not found");
    return;
  }
  jbyteArray msg = javaEnv->NewByteArray(len);
  javaEnv->SetByteArrayRegion(msg,0,len,message);
  javaEnv->CallStaticVoidMethod(javaClass,mid,(jlong)connection,(jlong)result,
              msg,(jint)len);
}

void JNICBReceived(GT2Connection connection, GT2Byte *message, int len,
                       GT2Bool reliable){
  if (DEBUG){
    printf(">>> Recieved CB\n");
  }

  jmethodID mid = javaEnv->GetStaticMethodID(javaClass,"gt2ReceivedCallback",
                                             "(J[BIZ)V");
  if (mid == NULL){
    printf("Error: callback gt2ReceivedCallback:(J[BIZ)V  not found");
    return;
  }
  jbyteArray msg = javaEnv->NewByteArray(len);
  jboolean jreliable = (reliable == GT2True);
  javaEnv->SetByteArrayRegion(msg,0,len,message);
  javaEnv->CallStaticVoidMethod(javaClass,mid,(jlong)connection,msg,(jint)len,
              jreliable);
}

void JNICBClosed(GT2Connection connection, GT2CloseReason reason){
  if (DEBUG){
     printf(">>> Closed CB\n");
   }

  jmethodID mid = javaEnv->GetStaticMethodID(javaClass,"gt2ClosedCallback",
                                             "(JI)V");
  if (mid == NULL){
    printf("Error: callback gt2ClosedCallback:(JI)V  not found");
    return;
  }
  javaEnv->CallStaticVoidMethod(javaClass,mid,(jlong)connection,(jint)reason);
}

void JNICBPing(GT2Connection connection, int latency){
  if (DEBUG){
     printf(">>> Ping CB\n");
   }

  jmethodID mid = javaEnv->GetStaticMethodID(javaClass,"gt2PingCallback",
                                              "(JI)V");
   if (mid == NULL){
     printf("Error: callback gt2PingCallback:(JI)V  not found");
     return;
   }
   javaEnv->CallStaticVoidMethod(javaClass,mid,(jlong)connection,(jint)latency);
}


void JNICBConnectAttempt(GT2Socket socket, GT2Connection connection,
                         unsigned int ip, unsigned short port, int latency,
                         GT2Byte *message, int len){
  if (DEBUG) {
    printf("Connection attempt cb.\n");
  }
  jmethodID mid = javaEnv->GetStaticMethodID(javaClass,
  "gt2ConnectAttemptCallback","(JJJSI[BI)V");
  if (mid == NULL){
    printf("Error: callback gt2ConnectAttemptCallback:(JJJSI[BI)V not found");
    return;
  }
  jbyteArray msg = javaEnv->NewByteArray(len);
  javaEnv->SetByteArrayRegion(msg,0,len,message);

  javaEnv->CallStaticVoidMethod(javaClass,mid,(jlong)socket, (jlong)connection,
                                (jlong)ip,(jshort)port,(jint)latency,msg,
                                (jint)len);

}



