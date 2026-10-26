#include <jni.h>
#include <string>
#include <cstring>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <openssl/evp.h>
#include <android/log.h>
#include <atomic>
#include <dlfcn.h>
#include <thread>
#include <chrono>
#include <mutex>
#include <functional>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <dirent.h>
#include <sys/ptrace.h>
#include <fcntl.h>
#include <algorithm>
#include <cctype>
#include <cerrno>

#define LOG_TAG "SafeChecker"

#ifdef NDEBUG
#define LOGI(...) ((void)0)
#define LOGE(...) ((void)0)
#else
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#endif

static const size_t kSha256DigestLength = 32;

// ======================= 签名哈希保护 =======================
// ======================= 签名哈希保护（已根据您的签名重新计算） =======================
// 将 SHA256 哈希分为 4 份，每份使用不同 XOR 密钥
// 真实签名哈希（16进制）：
static const unsigned char part1[] = {
        0xCC, 0xCA, 0xF1, 0xE8, 0x89, 0xFE, 0xA3, 0x59
};
static const unsigned char part2[] = {
        0x1E, 0xFC, 0x22, 0x44, 0x32, 0xFE, 0x90, 0x3C
};
static const unsigned char part3[] = {
        0x5D, 0xBD, 0xEA, 0x3C, 0x49, 0x0D, 0xDD, 0x2F
};
static const unsigned char part4[] = {
        0xA3, 0x30, 0x22, 0xE8, 0xB9, 0xCA, 0xF1, 0x44
};

static unsigned char getXorKey(int partIndex) {
    switch (partIndex) {
        case 0: return 0x55;
        case 1: return 0xA3;
        case 2: return 0x7C;
        case 3: return 0xE9;
        default: return 0x55;
    }
}

static void getExpectedHash(unsigned char *out) {
    memcpy(out, part1, 8);
    memcpy(out + 8, part2, 8);
    memcpy(out + 16, part3, 8);
    memcpy(out + 24, part4, 8);
    for (int i = 0; i < kSha256DigestLength; ++i) {
        int partIdx = i / 8;
        out[i] ^= getXorKey(partIdx);
    }
}

// ======================= 全局 JNI 缓存 =======================
static jclass g_contextClass = nullptr;
static jclass g_packageManagerClass = nullptr;
static jclass g_packageInfoClass = nullptr;
static jclass g_signingInfoClass = nullptr;
static jclass g_signatureClass = nullptr;
static jclass g_buildVersionClass = nullptr;

static jmethodID g_getPackageManager = nullptr;
static jmethodID g_getPackageName = nullptr;
static jmethodID g_getPackageInfo = nullptr;
static jmethodID g_toByteArray = nullptr;
static jmethodID g_getApkContentsSigners = nullptr;

static jfieldID g_signingInfoField = nullptr;
static jfieldID g_signaturesField = nullptr;
static jfieldID g_sdkIntField = nullptr;

static jint g_flagsSigningCertificates = 0;
static jint g_flagsSignatures = 0;

static std::atomic<bool> g_signatureVerified{false};
static std::atomic<bool> g_verifyAttempted{false};

// ======================= 辅助函数 =======================
static void ClearPendingException(JNIEnv *env) {
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

static jclass FindOptionalClass(JNIEnv *env, const char *name) {
    jclass cls = env->FindClass(name);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return nullptr;
    }
    return cls;
}

static jmethodID GetOptionalMethodID(JNIEnv *env, jclass cls, const char *name, const char *sig) {
    if (!cls) return nullptr;
    jmethodID method = env->GetMethodID(cls, name, sig);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return nullptr;
    }
    return method;
}

static jfieldID GetOptionalFieldID(JNIEnv *env, jclass cls, const char *name, const char *sig) {
    if (!cls) return nullptr;
    jfieldID field = env->GetFieldID(cls, name, sig);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return nullptr;
    }
    return field;
}

static jfieldID GetOptionalStaticFieldID(JNIEnv *env, jclass cls, const char *name, const char *sig) {
    if (!cls) return nullptr;
    jfieldID field = env->GetStaticFieldID(cls, name, sig);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return nullptr;
    }
    return field;
}

static int jitterDelayMs() {
    auto ticks = static_cast<unsigned long long>(
            std::chrono::steady_clock::now().time_since_epoch().count());
    auto threadBits = static_cast<unsigned long long>(
            std::hash<std::thread::id>{}(std::this_thread::get_id()));
    return 5 + static_cast<int>((ticks ^ threadBits) % 16);
}

static std::string lowerCopy(const std::string &value) {
    std::string out = value;
    std::transform(out.begin(), out.end(), out.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return out;
}

static bool fileExistsReadable(const char *path) {
    return access(path, R_OK) == 0;
}

static std::string bytesToHex(const unsigned char *data, size_t len) {
    std::stringstream ss;
    ss << std::hex << std::setfill('0');
    for (size_t i = 0; i < len; ++i) {
        ss << std::setw(2) << static_cast<int>(data[i]);
    }
    return ss.str();
}

static bool containsAnyLower(const std::string &value, const char *const *needles, size_t count) {
    const std::string lower = lowerCopy(value);
    for (size_t i = 0; i < count; ++i) {
        if (lower.find(needles[i]) != std::string::npos) return true;
    }
    return false;
}

static bool isExecutableFileExists(const char *path) {
    struct stat st{};
    if (stat(path, &st) == 0 && (st.st_mode & S_IXUSR)) {
        LOGI("Found executable at %s", path);
        return true;
    }
    return false;
}

// ======================= 优化后的调试检测 =======================
static bool isDebugged() {
    std::ifstream status("/proc/self/status");
    if (!status.is_open()) return false;

    std::string line;
    while (std::getline(status, line)) {
        if (line.find("TracerPid:") != std::string::npos) {
            std::string pidStr = line.substr(line.find(':') + 1);
            pidStr.erase(0, pidStr.find_first_not_of(" \t"));

            pid_t tracerPid = 0;
            try { tracerPid = std::stoi(pidStr); }
            catch (...) { return false; }

            if (tracerPid > 0 && tracerPid != getpid()) {
                LOGI("Debug detected: TracerPid=%d", tracerPid);
                return true;
            }
            break;
        }
    }
    return false;
}

// ======================= Root 检测 =======================
static bool isRooted() {
    const char *su_paths[] = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su"
    };
    for (const char *path : su_paths) {
        if (isExecutableFileExists(path)) return true;
    }

    if (access("/data/adb/magisk", F_OK) == 0 ||
        access("/data/adb/.magisk", F_OK) == 0) {
        LOGI("Magisk detected");
        return true;
    }

    if (access("/data/adb/ksu", F_OK) == 0 ||
        access("/data/adb/ksud", F_OK) == 0) {
        LOGI("KernelSU detected");
        return true;
    }

    if (fileExistsReadable("/sys/fs/selinux/enforce")) {
        std::ifstream selinux("/sys/fs/selinux/enforce");
        std::string value;
        if (selinux.is_open() && std::getline(selinux, value) && value == "0") {
            LOGI("SELinux permissive detected");
            return true;
        }
    }

    const char *rootPackages[] = {
            "com.topjohnwu.magisk",
            "com.koushikdutta.superuser",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.kingroot.kinguser"
    };
    std::ifstream packages("/data/system/packages.list");
    if (packages.is_open()) {
        std::string line;
        while (std::getline(packages, line)) {
            if (containsAnyLower(line, rootPackages, sizeof(rootPackages) / sizeof(rootPackages[0]))) {
                LOGI("Root package detected in packages.list");
                return true;
            }
        }
    }

    return false;
}

// ======================= 模拟器检测 =======================
static bool isEmulator() {
    char prop[PROP_VALUE_MAX];

    __system_property_get("ro.kernel.qemu", prop);
    if (strcmp(prop, "1") == 0) return true;

    __system_property_get("ro.product.model", prop);
    if (strstr(prop, "sdk") || strstr(prop, "Emulator") || strstr(prop, "Android SDK built for")) return true;

    __system_property_get("ro.hardware", prop);
    if (strstr(prop, "ranchu") || strstr(prop, "goldfish")) return true;

    __system_property_get("ro.build.characteristics", prop);
    if (strstr(prop, "emulator")) return true;

    __system_property_get("ro.build.flavor", prop);
    if (strstr(prop, "sdk_phone") || strstr(prop, "sdk_arm")) return true;

    __system_property_get("ro.product.manufacturer", prop);
    if (strstr(prop, "Genymotion")) return true;

    if (access("/system/bin/qemu-props", F_OK) == 0 ||
        access("/dev/qemu_pipe", F_OK) == 0) {
        return true;
    }
    return false;
}

// ======================= Xposed 检测（已修复 dlclose 误卸载） =======================
static bool isXposedInstalled() {
    const char *xposed_libs[] = {
            "libxposed_art.so",
            "libdexposed.so",
            "libepic.so",
            "liblsposed.so",
            "libriru.so",
            "libzygisk.so"
    };
    for (const char *lib : xposed_libs) {
        // 仅检查是否已加载，绝不卸载
        void *handle = dlopen(lib, RTLD_NOLOAD | RTLD_NOW);
        if (handle) {
            // 注意：不能调用 dlclose(handle)！否则会错误卸载系统/框架库，导致 SIGABRT
            LOGI("Xposed library detected: %s", lib);
            return true;
        }
    }

    const char *hookMarkers[] = {
            "xposedbridge.jar",
            "xposed",
            "lsposed",
            "riru",
            "zygisk",
            "edxposed",
            "taichi"
    };

    std::ifstream maps("/proc/self/maps");
    if (maps.is_open()) {
        std::string line;
        while (std::getline(maps, line)) {
            if (containsAnyLower(line, hookMarkers, sizeof(hookMarkers) / sizeof(hookMarkers[0]))) {
                return true;
            }
        }
    }
    return false;
}

// ======================= Frida 检测（已修复 dlclose 误卸载） =======================
static bool isFridaRunning() {
    const char *frida_libs[] = {"frida-agent.so", "frida-gadget.so", "libfrida.so"};
    const char *fridaMarkers[] = {
            "frida",
            "gum-js",
            "linjector",
            "frida-agent",
            "frida-gadget"
    };
    for (const char *lib: frida_libs) {
        // 同样仅检查存在性，不卸载
        void *handle = dlopen(lib, RTLD_NOLOAD | RTLD_NOW);
        if (handle) {
            // 不能 dlclose(handle)
            LOGI("Frida library detected: %s", lib);
            return true;
        }
    }

    std::ifstream maps("/proc/self/maps");
    if (maps.is_open()) {
        std::string line;
        while (std::getline(maps, line)) {
            if (containsAnyLower(line, fridaMarkers, sizeof(fridaMarkers) / sizeof(fridaMarkers[0]))) {
                return true;
            }
        }
    }

    const char *fridaPaths[] = {
            "/data/local/tmp/frida-server",
            "/data/local/tmp/frida",
            "/data/local/tmp/.frida",
            "/data/local/tmp/re.frida.server"
    };
    for (const char *path : fridaPaths) {
        if (access(path, F_OK) == 0) {
            LOGI("Frida file detected: %s", path);
            return true;
        }
    }

    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock != -1) {
        int flags = fcntl(sock, F_GETFL, 0);
        fcntl(sock, F_SETFL, flags | O_NONBLOCK);

        struct sockaddr_in addr;
        addr.sin_family = AF_INET;
        addr.sin_port = htons(27042);
        inet_pton(AF_INET, "127.0.0.1", &addr.sin_addr);

        int ret = connect(sock, (struct sockaddr*)&addr, sizeof(addr));
        if (ret == 0) {
            close(sock);
            LOGI("Frida port 27042 is open");
            return true;
        } else if (errno == EINPROGRESS) {
            fd_set fdset;
            FD_ZERO(&fdset);
            FD_SET(sock, &fdset);
            struct timeval tv = {0, 100000};
            if (select(sock + 1, nullptr, &fdset, nullptr, &tv) == 1) {
                int so_error;
                socklen_t len = sizeof(so_error);
                getsockopt(sock, SOL_SOCKET, SO_ERROR, &so_error, &len);
                if (so_error == 0) {
                    close(sock);
                    LOGI("Frida port 27042 is open (non-blocking)");
                    return true;
                }
            }
        }
        close(sock);
    }
    return false;
}

// ======================= 签名校验 =======================
static bool verifySignature(JNIEnv *env, jobject context) {
    if (g_verifyAttempted.load()) return g_signatureVerified.load();

    if (!g_getPackageManager || !g_getPackageName || !g_getPackageInfo || !g_toByteArray) {
        LOGE("JNI method missing");
        return false;
    }

    // 增加保护：确保关键类已被缓存
    if (!g_packageManagerClass || !g_packageInfoClass || !g_signatureClass) {
        LOGE("Critical JNI classes not cached");
        return false;
    }

    jobject packageManager = env->CallObjectMethod(context, g_getPackageManager);
    if (env->ExceptionCheck() || !packageManager) {
        env->ExceptionClear();
        return false;
    }

    jstring packageNameObj = (jstring) env->CallObjectMethod(context, g_getPackageName);
    if (env->ExceptionCheck() || !packageNameObj) {
        env->ExceptionClear();
        env->DeleteLocalRef(packageManager);
        return false;
    }

    jint sdkInt = 0;
    if (g_sdkIntField && g_buildVersionClass) {
        sdkInt = env->GetStaticIntField(g_buildVersionClass, g_sdkIntField);
        env->ExceptionClear();
    }

    jint flags = sdkInt >= 28 ?
                 (g_flagsSigningCertificates != 0 ? g_flagsSigningCertificates : 0x08000000) :
                 (g_flagsSignatures != 0 ? g_flagsSignatures : 0x40);

    jobject packageInfo = env->CallObjectMethod(packageManager, g_getPackageInfo, packageNameObj, flags);
    env->DeleteLocalRef(packageNameObj);
    env->DeleteLocalRef(packageManager);

    if (env->ExceptionCheck() || !packageInfo) {
        env->ExceptionClear();
        return false;
    }

    jobjectArray signatures = nullptr;
    if (sdkInt >= 28 && g_signingInfoField && g_signingInfoClass) {
        jobject signingInfo = env->GetObjectField(packageInfo, g_signingInfoField);
        if (signingInfo && g_getApkContentsSigners) {
            signatures = (jobjectArray) env->CallObjectMethod(signingInfo, g_getApkContentsSigners);
            env->ExceptionClear();
        }
        if (signingInfo) env->DeleteLocalRef(signingInfo);
    }

    if (!signatures && g_signaturesField) {
        signatures = (jobjectArray) env->GetObjectField(packageInfo, g_signaturesField);
        env->ExceptionClear();
    }

    if (!signatures || env->GetArrayLength(signatures) == 0) {
        env->DeleteLocalRef(packageInfo);
        return false;
    }

    unsigned char expectedHash[kSha256DigestLength];
    getExpectedHash(expectedHash);
    bool matched = false;

    jsize signCount = env->GetArrayLength(signatures);
    for (jsize i = 0; i < signCount && !matched; ++i) {
        jobject signature = env->GetObjectArrayElement(signatures, i);
        if (!signature) continue;

        jbyteArray sigBytes = (jbyteArray) env->CallObjectMethod(signature, g_toByteArray);
        if (env->ExceptionCheck() || !sigBytes) {
            env->ExceptionClear();
            env->DeleteLocalRef(signature);
            continue;
        }

        jsize len = env->GetArrayLength(sigBytes);
        jbyte *sigData = env->GetByteArrayElements(sigBytes, nullptr);
        if (!sigData) {
            env->DeleteLocalRef(sigBytes);
            env->DeleteLocalRef(signature);
            continue;
        }

        unsigned char hash[kSha256DigestLength];
        EVP_MD_CTX *mdCtx = EVP_MD_CTX_new();
        if (!mdCtx) {
            env->ReleaseByteArrayElements(sigBytes, sigData, JNI_ABORT);
            env->DeleteLocalRef(sigBytes);
            env->DeleteLocalRef(signature);
            continue;
        }

        bool digestOk = EVP_DigestInit_ex(mdCtx, EVP_sha256(), nullptr) == 1 &&
                        EVP_DigestUpdate(mdCtx, sigData, len) == 1 &&
                        EVP_DigestFinal_ex(mdCtx, hash, nullptr) == 1;
        EVP_MD_CTX_free(mdCtx);

        if (digestOk && memcmp(hash, expectedHash, kSha256DigestLength) == 0) matched = true;

        env->ReleaseByteArrayElements(sigBytes, sigData, JNI_ABORT);
        env->DeleteLocalRef(sigBytes);
        env->DeleteLocalRef(signature);
    }

    memset(expectedHash, 0, kSha256DigestLength);
    env->DeleteLocalRef(signatures);
    env->DeleteLocalRef(packageInfo);

    g_signatureVerified = matched;
    g_verifyAttempted = true;
    return matched;
}

// ======================= 主线程检测 =======================
static bool isMainThread(JNIEnv *env) {
    jclass looperClass = env->FindClass("android/os/Looper");
    if (!looperClass) return false;

    jmethodID getMainLooper = env->GetStaticMethodID(looperClass, "getMainLooper", "()Landroid/os/Looper;");
    jmethodID myLooper = env->GetStaticMethodID(looperClass, "myLooper", "()Landroid/os/Looper;");
    if (!getMainLooper || !myLooper) {
        env->DeleteLocalRef(looperClass);
        return false;
    }

    jobject mainLooper = env->CallStaticObjectMethod(looperClass, getMainLooper);
    jobject curLooper = env->CallStaticObjectMethod(looperClass, myLooper);
    bool result = mainLooper && curLooper && env->IsSameObject(mainLooper, curLooper);

    if (mainLooper) env->DeleteLocalRef(mainLooper);
    if (curLooper) env->DeleteLocalRef(curLooper);
    env->DeleteLocalRef(looperClass);
    return result;
}

// ======================= JNI 函数实现 =======================
static jint nativeVerifySecurity(JNIEnv *env, jclass, jobject context) {
    std::this_thread::sleep_for(std::chrono::milliseconds(jitterDelayMs()));

    if (isMainThread(env)) {
        LOGE("Run on main thread!");
        return 0;
    }

    if (!verifySignature(env, context)) {
        LOGE("Signature failed");
        return 0;
    }

    bool noisyEnvironmentSignal = isRooted() || isEmulator();
    if (noisyEnvironmentSignal) {
        LOGE("Root/emulator environment unsafe");
        return 0;
    }

    if (isDebugged() || isXposedInstalled() || isFridaRunning()) {
        LOGE("Environment unsafe");
        return 0;
    }

    LOGI("All security checks passed");
    return 1;
}

static void nativeForceExit(JNIEnv *env, jclass) {
    _exit(0);
}

// ======================= JNI 注册 =======================
static const JNINativeMethod methods[] = {
        {"nativeVerifySecurity", "(Landroid/content/Context;)I", (void *) nativeVerifySecurity},
        {"nativeForceExit",      "()V",                          (void *) nativeForceExit},
};

__attribute__((visibility("default")))
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;

    jclass tmp;
#define CACHE_CLASS(target, name) \
    tmp = FindOptionalClass(env, name); \
    if (!tmp) { \
        LOGE("Failed to find class: %s", name); \
        return JNI_ERR; \
    } \
    target = (jclass) env->NewGlobalRef(tmp); \
    ClearPendingException(env); \
    env->DeleteLocalRef(tmp);

    CACHE_CLASS(g_contextClass, "android/content/Context");
    CACHE_CLASS(g_packageManagerClass, "android/content/pm/PackageManager");
    CACHE_CLASS(g_packageInfoClass, "android/content/pm/PackageInfo");
    CACHE_CLASS(g_signatureClass, "android/content/pm/Signature");
    CACHE_CLASS(g_buildVersionClass, "android/os/Build$VERSION");

    tmp = FindOptionalClass(env, "android/content/pm/SigningInfo");
    if (tmp) {
        g_signingInfoClass = (jclass) env->NewGlobalRef(tmp);
        ClearPendingException(env);
        env->DeleteLocalRef(tmp);
    }

#undef CACHE_CLASS

    g_getPackageManager = GetOptionalMethodID(env, g_contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    g_getPackageName = GetOptionalMethodID(env, g_contextClass, "getPackageName", "()Ljava/lang/String;");
    g_getPackageInfo = GetOptionalMethodID(env, g_packageManagerClass, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    g_toByteArray = GetOptionalMethodID(env, g_signatureClass, "toByteArray", "()[B");

    if (!g_getPackageManager || !g_getPackageName || !g_getPackageInfo || !g_toByteArray) {
        LOGE("Failed to get essential JNI method IDs");
        return JNI_ERR;
    }

    g_signingInfoField = GetOptionalFieldID(env, g_packageInfoClass, "signingInfo", "Landroid/content/pm/SigningInfo;");
    g_signaturesField = GetOptionalFieldID(env, g_packageInfoClass, "signatures", "[Landroid/content/pm/Signature;");
    g_sdkIntField = GetOptionalStaticFieldID(env, g_buildVersionClass, "SDK_INT", "I");

    jfieldID f = GetOptionalStaticFieldID(env, g_packageManagerClass, "GET_SIGNING_CERTIFICATES", "I");
    if (f) g_flagsSigningCertificates = env->GetStaticIntField(g_packageManagerClass, f);
    ClearPendingException(env);
    f = GetOptionalStaticFieldID(env, g_packageManagerClass, "GET_SIGNATURES", "I");
    if (f) g_flagsSignatures = env->GetStaticIntField(g_packageManagerClass, f);
    ClearPendingException(env);

    if (g_signingInfoClass) {
        g_getApkContentsSigners = GetOptionalMethodID(env, g_signingInfoClass, "getApkContentsSigners", "()[Landroid/content/pm/Signature;");
    }

    jclass cls = FindOptionalClass(env, "com/findmyphone/gpstracker/utils/SafeChecker");
    if (!cls) {
        LOGE("Failed to find SafeChecker class");
        return JNI_ERR;
    }
    if (env->RegisterNatives(cls, methods, sizeof(methods) / sizeof(methods[0])) != JNI_OK) {
        ClearPendingException(env);
        env->DeleteLocalRef(cls);
        return JNI_ERR;
    }
    env->DeleteLocalRef(cls);

    LOGI("JNI_OnLoad success");
    return JNI_VERSION_1_6;
}

__attribute__((visibility("default")))
void JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) return;

#define DELETE_REF(ref) if (ref) env->DeleteGlobalRef(ref);
    DELETE_REF(g_contextClass);
    DELETE_REF(g_packageManagerClass);
    DELETE_REF(g_packageInfoClass);
    DELETE_REF(g_signingInfoClass);
    DELETE_REF(g_signatureClass);
    DELETE_REF(g_buildVersionClass);
#undef DELETE_REF
}