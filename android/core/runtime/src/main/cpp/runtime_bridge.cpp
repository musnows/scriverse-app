#include <jni.h>
#include <unistd.h>

#include <string>

extern "C" JNIEXPORT jint JNICALL
Java_com_scriverse_app_core_runtime_RuntimeBridge_nativePageSize(JNIEnv*, jobject) {
  return static_cast<jint>(sysconf(_SC_PAGESIZE));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_scriverse_app_core_runtime_RuntimeBridge_nativeLibraryProbe(JNIEnv* env, jobject) {
  const long page_size = sysconf(_SC_PAGESIZE);
  const std::string result = "native-ok;page-size=" + std::to_string(page_size);
  return env->NewStringUTF(result.c_str());
}
