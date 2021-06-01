
#include "ne_zig_demo_HelloJNI.h"



#if defined(__cplusplus)
extern "C" {
#endif

/*
 * Class:     ne_zig_demo_HelloJNI
 * Method:    sayHello
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_ne_zig_demo_HelloJNI_sayHello
  (JNIEnv *env, jclass cls) {
    return (* env)->NewStringUTF(env, "Hello zig!");
}


#ifdef  __cplusplus
}
#endif