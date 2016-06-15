#include <webrtc/api/java/jni/androidvideocapturer_jni.h>
#include <webrtc/api/java/jni/classreferenceholder.h>
#include "webrtc/api/androidvideocapturer.h"
#include "com_twilio_conversations_CameraCapturer.h"
#include "webrtc/api/java/jni/jni_helpers.h"
#include "TSCoreSDKTypes.h"
#include "TSCoreError.h"
#include "TSCLogger.h"
#include "TSCEndpoint.h"
#include "TSCSession.h"

using namespace twiliosdk;
using namespace webrtc_jni;

#define TAG  "TwilioSDK(native)"

JNIEXPORT void JNICALL Java_com_twilio_conversations_CameraCapturer_nativeStopVideoSource
        (JNIEnv *env, jobject obj, jlong nativeSession)
{
    TS_CORE_LOG_MODULE(kTSCoreLogModulePlatform, kTSCoreLogLevelDebug, "stopVideoSource");
    TSCSessionPtr *session = reinterpret_cast<TSCSessionPtr *>(nativeSession);
    session->get()->stopVideoSource();
}


JNIEXPORT void JNICALL Java_com_twilio_conversations_CameraCapturer_nativeRestartVideoSource
        (JNIEnv *env, jobject obj, jlong nativeSession)
{
    TS_CORE_LOG_MODULE(kTSCoreLogModulePlatform, kTSCoreLogLevelDebug, "stopVideoSource");
    TSCSessionPtr *session = reinterpret_cast<TSCSessionPtr *>(nativeSession);
    session->get()->restartVideoSource();
}

JNIEXPORT jlong JNICALL
Java_com_twilio_conversations_CameraCapturer_nativeCreateNativeCapturer(JNIEnv *env,
                                                                        jobject instance,
                                                                        jobject j_video_capturer,
                                                                        jobject j_egl_context) {
    rtc::scoped_refptr<webrtc::AndroidVideoCapturerDelegate> delegate =
            new rtc::RefCountedObject<AndroidVideoCapturerJni>(env, j_video_capturer, j_egl_context);
    std::unique_ptr<cricket::VideoCapturer> capturer(new webrtc::AndroidVideoCapturer(delegate));
    return jlongFromPointer(capturer.release());
}

JNIEXPORT void JNICALL
Java_com_twilio_conversations_CameraCapturer_nativeDisposeCapturer(JNIEnv *env,
                                                                        jobject instance,
                                                                        jlong nativeVideoCapturerAndroid) {
    webrtc::AndroidVideoCapturer *capturer =
            reinterpret_cast<webrtc::AndroidVideoCapturer *>(nativeVideoCapturerAndroid);
    delete capturer;
}
