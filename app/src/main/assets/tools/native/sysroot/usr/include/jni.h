/*
 * Standard Android JNI Header (jni.h) for Sandboxed Native Compilation
 */

#ifndef _JNI_H_
#define _JNI_H_

#include <stdarg.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Primitive types */
typedef uint8_t         jboolean;
typedef int8_t          jbyte;
typedef uint16_t        jchar;
typedef int16_t         jshort;
typedef int32_t         jint;
typedef int64_t         jlong;
typedef float           jfloat;
typedef double          jdouble;
typedef jint            jsize;

#define JNI_FALSE 0
#define JNI_TRUE 1

#define JNI_VERSION_1_1 0x00010001
#define JNI_VERSION_1_2 0x00010002
#define JNI_VERSION_1_4 0x00010004
#define JNI_VERSION_1_6 0x00010006

#define JNI_OK          (0)
#define JNI_ERR         (-1)

#ifdef __cplusplus
class _jobject {};
class _jclass : public _jobject {};
class _jstring : public _jobject {};
class _jarray : public _jobject {};

typedef _jobject*       jobject;
typedef _jclass*        jclass;
typedef _jstring*       jstring;
typedef _jarray*        jarray;
#else
struct _jobject;
typedef struct _jobject* jobject;
typedef jobject         jclass;
typedef jobject         jstring;
typedef jobject         jarray;
#endif

/* Forward declaration for JNIEnv */
struct JNINativeInterface;

#ifdef __cplusplus
struct JNIEnv_ {
    const struct JNINativeInterface* functions;

    jclass FindClass(const char* name) {
        return 0;
    }
    jstring NewStringUTF(const char* bytes) {
        return (jstring)0;
    }
    const char* GetStringUTFChars(jstring string, jboolean* isCopy) {
        return 0;
    }
    void ReleaseStringUTFChars(jstring string, const char* utf) {
    }
};
typedef struct JNIEnv_ JNIEnv;
#else
typedef const struct JNINativeInterface* JNIEnv;
#endif

#ifndef JNIEXPORT
#define JNIEXPORT __attribute__ ((visibility ("default")))
#endif

#ifndef JNICALL
#define JNICALL
#endif

#ifdef __cplusplus
}
#endif

#endif /* _JNI_H_ */
