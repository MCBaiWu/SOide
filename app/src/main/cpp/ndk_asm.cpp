// ndk_asm.cpp
// ----------------------------------------------------------------------
// 真 Keystone 汇编库的 JNI 包装。
// 底层调用 keystone-engine/keystone (https://github.com/keystone-engine/keystone)。
// 静态库由 CI workflow 预构建为 third_party/keystone/lib/<ABI>/libkeystone.a
// 并在 CMakeLists.txt 中链接；头文件在 include/keystone/keystone.h。
//
// 反汇编端 (ndk_disasm.cpp) 使用的真库是 capstone-engine/capstone。
// ----------------------------------------------------------------------
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <vector>
#include <string>

#include <keystone/keystone.h>

#include "ndk_native.h"

extern "C" int ndk_asm_arm(const char* line, int is_thumb, uint8_t* out, int max_out) {
    if (!line || !out || max_out <= 0) return 0;

    ks_engine* ks = nullptr;
    ks_err err = ks_open(KS_ARCH_ARM,
                         is_thumb ? KS_MODE_THUMB : KS_MODE_ARM,
                         &ks);
    if (err != KS_ERR_OK || ks == nullptr) {
        return 0;
    }

    unsigned char* encode = nullptr;
    size_t size = 0;
    size_t count = 0;

    int rc = ks_asm(ks, line, 0, &encode, &size, &count);
    int produced = 0;
    if (rc == 0 && encode != nullptr && size > 0) {
        if ((int) size > max_out) size = (size_t) max_out;
        std::memcpy(out, encode, size);
        produced = (int) size;
    }

    if (encode) ks_free(encode);
    ks_close(ks);
    return produced;
}
