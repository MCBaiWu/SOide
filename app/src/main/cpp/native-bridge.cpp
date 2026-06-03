// native-bridge.cpp
// JNI 桥接层：让 Java 层调用 native NDK 实现的 keystone 风格汇编器与 capstone 风格反汇编器。
//
// 之所以自实现一个轻量级版本而不是直接绑定 capstone/keystone 源码，是因为完整
// capstone/keystone 源代码 (各 100+ C 文件) 集成到 Android NDK 体积巨大且配置复杂。
// 这里提供 ARM / Thumb 子集，足以满足本项目对二进制解析的需要。
//
// 后续如果需要扩展，可直接将 capstone / keystone 源码作为 CMake 子目录加入构建：
//   add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/third_party/capstone)
//   add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/third_party/keystone)
// 并在 target_link_libraries 中追加 capstone; keystone 即可。
#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>
#include <cstring>
#include <android/log.h>

#include "ndk_native.h"

#define LOG_TAG "soide-native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------
// JNI: nativeDisasm
//   输入 byte[], 返回 Object[] 数组，每项 Object[] = { address:long, size:int,
//     bytes:byte[], mnemonic:String, opStr:String }
// ---------------------------------------------------------------
static jlongArray toLongArray(JNIEnv* env, const std::vector<uint64_t>& v) {
    jlongArray arr = env->NewLongArray((jsize) v.size());
    if (!arr) return nullptr;
    env->SetLongArrayRegion(arr, 0, (jsize) v.size(), (const jlong*) v.data());
    return arr;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_soide_nativebridge_NativeBridge_nativeDisasm(
        JNIEnv* env, jclass /*clazz*/,
        jbyteArray code, jlong address, jboolean isThumb) {

    jsize len = env->GetArrayLength(code);
    jbyte* bytes = env->GetByteArrayElements(code, nullptr);
    if (!bytes) return nullptr;

    std::vector<ndk_insn_t> insns(2048);
    int n = ndk_disasm_arm(reinterpret_cast<const uint8_t*>(bytes), (size_t) len,
                           (uint64_t) address, (int) isThumb,
                           insns.data(), (int) insns.size());
    env->ReleaseByteArrayElements(code, bytes, JNI_ABORT);

    if (n < 0) n = 0;
    if (n > (int) insns.size()) n = (int) insns.size();

    jclass arrClass = env->FindClass("[Ljava/lang/Object;");
    jclass objClass = env->FindClass("java/lang/Object");
    jclass strClass = env->FindClass("java/lang/String");
    jclass byteArrClass = env->FindClass("[B");

    jobjectArray result = env->NewObjectArray((jsize) n, objClass, nullptr);

    for (int i = 0; i < n; i++) {
        const ndk_insn_t& ins = insns[i];
        // 构造 byte[] bytes
        int bsz = (int) ins.size;
        jbyteArray jbytes = env->NewByteArray(bsz);
        jbyte buf[8];
        for (int k = 0; k < bsz && k < 8; k++) buf[k] = (jbyte) (ins.bytes[k] & 0xff);
        env->SetByteArrayRegion(jbytes, 0, bsz, buf);

        jstring jmn = env->NewStringUTF(ins.mnemonic);
        jstring jop = env->NewStringUTF(ins.op_str);

        // Object[] = { address, size, bytes, mnemonic, opStr }
        jobjectArray row = env->NewObjectArray(5, objClass, nullptr);
        env->SetObjectArrayElement(row, 0, env->NewObject(
                env->FindClass("java/lang/Long"), "(J)V", (jlong) ins.address));
        env->SetObjectArrayElement(row, 1, env->NewObject(
                env->FindClass("java/lang/Integer"), "(I)V", (jint) ins.size));
        env->SetObjectArrayElement(row, 2, jbytes);
        env->SetObjectArrayElement(row, 3, jmn);
        env->SetObjectArrayElement(row, 4, jop);

        env->SetObjectArrayElement(result, i, row);
    }
    return result;
}

// ---------------------------------------------------------------
// JNI: nativeAssemble
//   输入 String 指令, boolean isThumb, 返回 byte[]
// ---------------------------------------------------------------
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_soide_nativebridge_NativeBridge_nativeAssemble(
        JNIEnv* env, jclass /*clazz*/,
        jstring line, jboolean isThumb) {

    const char* cstr = env->GetStringUTFChars(line, nullptr);
    if (!cstr) return nullptr;

    uint8_t out[8] = {0};
    int n = ndk_asm_arm(cstr, (int) isThumb, out, (int) sizeof(out));
    env->ReleaseStringUTFChars(line, cstr);

    if (n <= 0) {
        // 失败 -> 返回空数组
        jbyteArray empty = env->NewByteArray(0);
        return empty;
    }
    jbyteArray result = env->NewByteArray(n);
    env->SetByteArrayRegion(result, 0, n, (const jbyte*) out);
    return result;
}

// ---------------------------------------------------------------
// JNI: nativeSysvHash / nativeGnuHash
//   native 实现 ELF 字符串哈希算法，避免 Java 反射调用开销
// ---------------------------------------------------------------
extern "C" JNIEXPORT jlong JNICALL
Java_com_soide_nativebridge_NativeBridge_nativeSysvHash(JNIEnv* env, jclass /*clazz*/, jstring name) {
    const char* cstr = env->GetStringUTFChars(name, nullptr);
    if (!cstr) return 0;
    uint32_t h = 0;
    while (*cstr) {
        h = (h << 4) + (uint8_t) *cstr++;
        uint32_t g = h & 0xf0000000u;
        if (g) h ^= g >> 24;
        h &= ~g;
    }
    env->ReleaseStringUTFChars(name, cstr);
    return (jlong) (uint64_t) h;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_soide_nativebridge_NativeBridge_nativeGnuHash(JNIEnv* env, jclass /*clazz*/, jstring name) {
    const char* cstr = env->GetStringUTFChars(name, nullptr);
    if (!cstr) return 0;
    uint32_t h = 5381;
    int c;
    while ((c = (uint8_t) *cstr++) != 0) {
        h = (h << 5) + h + c;
    }
    env->ReleaseStringUTFChars(name, cstr);
    return (jlong) (uint64_t) h;
}

// ---------------------------------------------------------------
// JNI: nativeVersion - 返回 SO 版本字符串
// ---------------------------------------------------------------
extern "C" JNIEXPORT jstring JNICALL
Java_com_soide_nativebridge_NativeBridge_nativeVersion(JNIEnv* env, jclass /*clazz*/) {
    return env->NewStringUTF("soide-native 1.3.0 (CMake/NDK; capstone/keystone-style subset)");
}
