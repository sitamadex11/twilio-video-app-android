/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_twilio_conversations_impl_ConversationImpl */

#ifndef _Included_com_twilio_conversations_impl_ConversationImpl
#define _Included_com_twilio_conversations_impl_ConversationImpl

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL Java_com_twilio_conversations_impl_ConversationImpl_wrapOutgoingSession
  (JNIEnv *, jobject, jlong, jlong, jobjectArray);

JNIEXPORT void JNICALL Java_com_twilio_conversations_impl_ConversationImpl_start
  (JNIEnv *, jobject, jlong, jboolean, jboolean, jboolean, jboolean, jobject, jobjectArray, jobject);

JNIEXPORT void JNICALL Java_com_twilio_conversations_impl_ConversationImpl_stop
  (JNIEnv *, jobject, jlong);

JNIEXPORT void JNICALL Java_com_twilio_conversations_impl_ConversationImpl_setExternalCapturer
  (JNIEnv *, jobject, jlong, jlong);

JNIEXPORT void JNICALL Java_com_twilio_conversations_impl_ConversationImpl_setSessionObserver
  (JNIEnv *, jobject, jlong, jlong);

JNIEXPORT void JNICALL Java_com_twilio_conversations_impl_ConversationImpl_freeNativeHandle
  (JNIEnv *, jobject, jlong);

JNIEXPORT jboolean JNICALL Java_com_twilio_conversations_impl_ConversationImpl_enableVideo
  (JNIEnv *, jobject, jlong, jboolean, jboolean);

JNIEXPORT jboolean JNICALL Java_com_twilio_conversations_impl_ConversationImpl_mute
  (JNIEnv *, jobject, jlong, jboolean);

JNIEXPORT jboolean JNICALL Java_com_twilio_conversations_impl_ConversationImpl_isMuted
  (JNIEnv *, jobject, jlong);

JNIEXPORT void JNICALL Java_com_twilio_conversations_impl_ConversationImpl_inviteParticipants
  (JNIEnv *, jobject, jlong, jobjectArray);

JNIEXPORT jstring JNICALL Java_com_twilio_conversations_impl_ConversationImpl_getConversationSid
  (JNIEnv *, jobject, jlong);

JNIEXPORT jboolean JNICALL Java_com_twilio_conversations_impl_ConversationImpl_enableAudio
  (JNIEnv *, jobject, jlong, jboolean, jboolean);


#ifdef __cplusplus
}
#endif
#endif
