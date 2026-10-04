/* libretro 核心宿主 shim：
 * - dlopen 核心 .so（lib<core>_libretro.so,核心由平台决定）,dlsym 全部入口
 * - retro_* 回调转接：视频帧/音频采样拷入 JVM 侧复用缓冲，输入 up-call 位掩码
 * 线程纪律：retro_run 与其触发的全部回调都发生在调用 nativeRunFrame 的
 * 单一游戏线程上，该线程的 JNIEnv 缓存在 game_env；回调不得跨线程使用。 */
#include <jni.h>
#include <dlfcn.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <stdio.h>
#include "libretro_cbs.h"

#define TAG "corenative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void *core_lib;
static int pixel_format;            /* 0=XRGB8888,1=RGB565(SET_PIXEL_FORMAT 捕获) */

/* --- 核心 API 符号 --- */
static void (*p_retro_init)(void);
static void (*p_retro_deinit)(void);
static unsigned (*p_retro_api_version)(void);
static void (*p_retro_get_system_info)(struct retro_system_info *);
static void (*p_retro_get_system_av_info)(struct retro_system_av_info *);
static void (*p_retro_set_environment)(retro_environment_t);
static void (*p_retro_set_video_refresh)(retro_video_refresh_t);
static void (*p_retro_set_audio_sample)(retro_audio_sample_t);
static void (*p_retro_set_audio_sample_batch)(retro_audio_sample_batch_t);
static void (*p_retro_set_input_poll)(retro_input_poll_t);
static void (*p_retro_set_input_state)(retro_input_state_t);
static void (*p_retro_run)(void);
static bool (*p_retro_load_game)(const struct retro_game_info *);
static void (*p_retro_unload_game)(void);
static void (*p_retro_reset)(void);
static void *(*p_retro_get_memory_data)(unsigned);
static size_t (*p_retro_get_memory_size)(unsigned);
static size_t (*p_retro_serialize_size)(void);
static bool (*p_retro_serialize)(void *, size_t);
static bool (*p_retro_unserialize)(const void *, size_t);
/* 金手指（cheat-codes）：可选能力——核心不支持时保持 NULL，金手指禁用而不阻塞加载 */
static void (*p_retro_cheat_reset)(void);
static void (*p_retro_cheat_set)(unsigned, bool, const char *);

/* --- 状态 --- */
static JavaVM *vm;
static jobject cb_obj;              /* LibretroCore 实例（全局引用） */
static jmethodID mid_input_mask;    /* getInputMaskFromNative(I)I */
static JNIEnv *game_env;            /* 仅游戏线程（见文件头纪律） */

static jobject video_buf;           /* jintArray（全局引用），Kotlin 持有同一对象 */
static jsize   video_cap;
static jobject audio_buf;           /* jshortArray（全局引用） */
static jsize   audio_cap;

static char *system_dir;
static char *save_dir;

static int video_w = 256, video_h = 240;
static double timing_fps = 60.0;
static double timing_rate = 48000.0;
static jint last_audio_shorts;      /* 上一帧写入 audio_buf 的 short 数 */
static char *rom_path;              /* 由核心在 load_game 期间持有，下次加载前不释放 */
static void *rom_data;              /* need_fullpath=false 的核心要求前端读入内存（gambatte） */
static size_t rom_size;
static int core_need_fullpath;      /* 由 retro_get_system_info 捕获（load_core 时） */

static void shim_log(enum retro_log_level level, const char *fmt, ...) {
    char line[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(line, sizeof(line), fmt, ap);
    va_end(ap);
    if (level == RETRO_LOG_ERROR) LOGE("%s", line);
    else LOGI("%s", line);
}

static size_t audio_sample_batch_cb(const int16_t *data, size_t frames);

static bool environment_cb(unsigned cmd, void *data) {
    switch (cmd) {
    case RETRO_ENVIRONMENT_GET_CAN_DUPE:
        *(bool *)data = true;
        return true;
    case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
        ((struct retro_log_callback *)data)->log = shim_log;
        return true;
    case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
        unsigned fmt = *(unsigned *)data;
        if (fmt == RETRO_PIXEL_FORMAT_XRGB8888) {
            pixel_format = 0;
            return true;
        }
        if (fmt == RETRO_PIXEL_FORMAT_RGB565) {
            /* gb-gbc-platform:Gambatte 输出 RGB565,刷新回调按 2 字节/像素转换 */
            pixel_format = 1;
            return true;
        }
        LOGE("核心请求了不支持像素格式 %u（拒绝，核心应回退）", fmt);
        return false;
    }
    case 21: /* RETRO_ENVIRONMENT_SET_SAMPLE_RATE(cbs 头未定义该常量) */
        timing_rate = *(double *)data;
        return true;
    case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
        if (!system_dir) return false;
        *(const char **)data = system_dir;
        return true;
    case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
        if (!save_dir) return false;
        *(const char **)data = save_dir;
        return true;
    case RETRO_ENVIRONMENT_SET_VARIABLES:
        return true; /* 全部走核心默认值 */
    case RETRO_ENVIRONMENT_GET_VARIABLE:
        return false; /* 未处理 → 核心取默认值（返回值是 ABI 语义，不可为垃圾） */
    default:
        return false;
    }
}

static void video_refresh_cb(const void *data, unsigned w, unsigned h, size_t pitch) {
    if (!data || !game_env || !video_buf)
        return;
    JNIEnv *env = game_env;
    video_w = (int)w;
    video_h = (int)h;
    if ((jsize)(w * h) > video_cap) {
        LOGE("视频缓冲不足以容纳 %ux%u", w, h);
        return;
    }
    void *dst = (*env)->GetPrimitiveArrayCritical(env, video_buf, NULL);
    if (!dst)
        return;
    uint32_t *d = dst;
    if (pixel_format == 1) {
        /* RGB565 → XRGB8888(2 字节/像素,pitch 按 2 字节步进) */
        for (unsigned y = 0; y < h; y++) {
            const uint16_t *s = (const uint16_t *)((const uint8_t *)data + y * pitch);
            uint32_t *row = d + (size_t)y * w;
            for (unsigned x = 0; x < w; x++) {
                unsigned v = s[x];
                unsigned r5 = (v >> 11) & 0x1F, g6 = (v >> 5) & 0x3F, b5 = v & 0x1F;
                row[x] = 0xFF000000u | ((r5 * 255 / 31) << 16) | ((g6 * 255 / 63) << 8) | (b5 * 255 / 31);
            }
        }
    } else {
        const uint32_t *src = data;
        if (pitch == (size_t)w * 4) {
            const size_t n = (size_t)w * h;
            for (size_t i = 0; i < n; i++)
                d[i] = src[i] | 0xFF000000u;
        } else {
            for (unsigned y = 0; y < h; y++) {
                const uint32_t *s = (const uint32_t *)((const uint8_t *)data + y * pitch);
                uint32_t *row = d + (size_t)y * w;
                for (unsigned x = 0; x < w; x++)
                    row[x] = s[x] | 0xFF000000u;
            }
        }
    }
    (*env)->ReleasePrimitiveArrayCritical(env, video_buf, dst, 0);
}

static void audio_sample_cb(int16_t left, int16_t right) {
    /* fceumm 走 batch 接口，此单采样回调兜底为一次伪 batch */
    int16_t pair[2] = { left, right };
    audio_sample_batch_cb(pair, 1);
}

static size_t audio_sample_batch_cb(const int16_t *data, size_t frames) {
    if (!game_env || !audio_buf)
        return frames;
    JNIEnv *env = game_env;
    jsize need = (jsize)(frames * 2);
    if (need > audio_cap) {
        LOGE("音频缓冲不足：需要 %d", need);
        return frames;
    }
    void *dst = (*env)->GetPrimitiveArrayCritical(env, audio_buf, NULL);
    if (!dst)
        return frames;
    memcpy(dst, data, (size_t)need * sizeof(int16_t));
    (*env)->ReleasePrimitiveArrayCritical(env, audio_buf, dst, 0);
    last_audio_shorts = (jint)need;
    return frames;
}

static void input_poll_cb(void) {}

static int16_t input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (device != RETRO_DEVICE_JOYPAD || index != 0 || !game_env || !cb_obj)
        return 0;
    jint mask = (*game_env)->CallIntMethod(game_env, cb_obj, mid_input_mask, (jint)port);
    if ((*game_env)->ExceptionCheck(game_env)) {
        (*game_env)->ExceptionDescribe(game_env);
        (*game_env)->ExceptionClear(game_env);
        return 0;
    }
    return (int16_t)((mask >> id) & 1);
}

/* --- JNI 入口（com.huffcart.core.native.LibretroCore） --- */

JNIEXPORT jboolean JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeLoadCore(JNIEnv *env, jobject thiz, jstring libPath) {
    const char *path = (*env)->GetStringUTFChars(env, libPath, NULL);
    core_lib = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    (*env)->ReleaseStringUTFChars(env, libPath, path);
    if (!core_lib) {
        /* extractNativeLibs=false 时库不打盘（留在 APK 内），
           全路径不存在，按 SONAME（传入路径的基名）走应用 linker 命名空间解析
           —— gb-gbc-platform:多核心后 SONAME 由平台决定,不得硬编码 */
        const char *base = strrchr(path, '/');
        base = base ? base + 1 : path;
        LOGI("全路径 dlopen 失败（%s），改按 SONAME 解析: %s", dlerror(), base);
        core_lib = dlopen(base, RTLD_NOW | RTLD_LOCAL);
    }
    if (!core_lib) {
        LOGE("dlopen 失败: %s", dlerror());
        return JNI_FALSE;
    }
#define SYM(f) do { \
    *(void **)(&p_##f) = dlsym(core_lib, #f); \
    if (!p_##f) { LOGE("缺少符号 " #f); dlclose(core_lib); core_lib = NULL; return JNI_FALSE; } \
} while (0)
    SYM(retro_init); SYM(retro_deinit); SYM(retro_api_version);
    SYM(retro_get_system_info); SYM(retro_get_system_av_info);
    SYM(retro_set_environment); SYM(retro_set_video_refresh);
    SYM(retro_set_audio_sample); SYM(retro_set_audio_sample_batch);
    SYM(retro_set_input_poll); SYM(retro_set_input_state);
    SYM(retro_run); SYM(retro_load_game); SYM(retro_unload_game);
    SYM(retro_reset); SYM(retro_get_memory_data); SYM(retro_get_memory_size);
    SYM(retro_serialize_size); SYM(retro_serialize); SYM(retro_unserialize);
#undef SYM
    /* 捕获 ROM 传递模式:need_fullpath=true 走路径,false 要求前端读入内存 */
    struct retro_system_info si = {0};
    p_retro_get_system_info(&si);
    core_need_fullpath = si.need_fullpath ? 1 : 0;
    LOGI("核心: %s (need_fullpath=%d)", si.library_name ? si.library_name : "?", core_need_fullpath);
    /* cheat 符号软解析：缺失仅禁用金手指（不进硬失败 SYM） */
    *(void **)(&p_retro_cheat_reset) = dlsym(core_lib, "retro_cheat_reset");
    *(void **)(&p_retro_cheat_set) = dlsym(core_lib, "retro_cheat_set");
    if (!p_retro_cheat_reset || !p_retro_cheat_set) {
        LOGI("核心不支持金手指（retro_cheat_* 缺失），能力禁用");
    }

    p_retro_set_environment(environment_cb);
    p_retro_set_video_refresh(video_refresh_cb);
    p_retro_set_audio_sample(audio_sample_cb);
    p_retro_set_audio_sample_batch(audio_sample_batch_cb);
    p_retro_set_input_poll(input_poll_cb);
    p_retro_set_input_state(input_state_cb);

    /* 缓存回调宿主对象与输入掩码方法 */
    if (cb_obj) (*env)->DeleteGlobalRef(env, cb_obj);
    cb_obj = (*env)->NewGlobalRef(env, thiz);
    jclass cls = (*env)->GetObjectClass(env, thiz);
    mid_input_mask = (*env)->GetMethodID(env, cls, "getInputMaskFromNative", "(I)I");
    (*env)->DeleteLocalRef(env, cls);
    if (!mid_input_mask) {
        LOGE("未找到 getInputMaskFromNative");
        return JNI_FALSE;
    }

    LOGI("核心加载成功, API v%u", p_retro_api_version());
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeInit(JNIEnv *env, jobject thiz,
                                                      jstring systemDir, jstring saveDir) {
    free(system_dir); free(save_dir);
    const char *s = (*env)->GetStringUTFChars(env, systemDir, NULL);
    const char *v = (*env)->GetStringUTFChars(env, saveDir, NULL);
    system_dir = strdup(s);
    save_dir = strdup(v);
    (*env)->ReleaseStringUTFChars(env, systemDir, s);
    (*env)->ReleaseStringUTFChars(env, saveDir, v);
    p_retro_init();
}

JNIEXPORT void JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeSetBuffers(JNIEnv *env, jobject thiz,
                                                            jintArray video, jintArray audio) {
    if (video_buf) (*env)->DeleteGlobalRef(env, video_buf);
    if (audio_buf) (*env)->DeleteGlobalRef(env, audio_buf);
    video_buf = (*env)->NewGlobalRef(env, video);
    audio_buf = (*env)->NewGlobalRef(env, audio);
    video_cap = (*env)->GetArrayLength(env, video);
    audio_cap = (*env)->GetArrayLength(env, audio);
}

JNIEXPORT jboolean JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeLoadGame(JNIEnv *env, jobject thiz,
                                                          jstring romPath) {
    /* need_fullpath：核心在 load_game 期间（含 SRAM 命名）可能持有 path，
       保留 UTF-8 串到下一次加载/卸载时再释放 */
    free(rom_path);
    rom_path = strdup((*env)->GetStringUTFChars(env, romPath, NULL));
    free(rom_data); rom_data = NULL; rom_size = 0;
    struct retro_game_info info = {0};
    info.path = rom_path;
    if (core_need_fullpath) {
        info.data = NULL;
        info.size = 0;
    } else {
        /* need_fullpath=false:读 ROM 进内存交给核心(gambatte) */
        FILE *f = fopen(rom_path, "rb");
        if (!f) { LOGE("无法打开 ROM 文件: %s", rom_path); return JNI_FALSE; }
        fseek(f, 0, SEEK_END);
        long n = ftell(f);
        fseek(f, 0, SEEK_SET);
        rom_data = malloc(n > 0 ? (size_t)n : 1);
        if (fread(rom_data, 1, (size_t)n, f) != (size_t)n) {
            fclose(f); free(rom_data); rom_data = NULL;
            LOGE("ROM 读取失败: %s", rom_path);
            return JNI_FALSE;
        }
        fclose(f);
        rom_size = (size_t)n;
        info.data = rom_data;
        info.size = rom_size;
    }
    jboolean ok = p_retro_load_game(&info) ? JNI_TRUE : JNI_FALSE;
    if (ok == JNI_TRUE) {
        struct retro_system_av_info av = {0};
        p_retro_get_system_av_info(&av);
        timing_fps = av.timing.fps;
        timing_rate = av.timing.sample_rate;
        LOGI("游戏加载成功: %ux%u @%.3ffps, %.1fHz",
             av.geometry.base_width, av.geometry.base_height,
             av.timing.fps, av.timing.sample_rate);
    } else {
        LOGE("游戏加载失败");
    }
    return ok;
}

JNIEXPORT jint JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeRunFrame(JNIEnv *env, jobject thiz,
                                                          jintArray infoOut) {
    game_env = env; /* 回调使用本线程 env（见文件头纪律） */
    p_retro_run();
    jint dims[2] = { video_w, video_h };
    (*env)->SetIntArrayRegion(env, infoOut, 0, 2, dims);
    /* 上一帧音频采样数通过 audio_batch_cb 传回：
       这里返回本帧写入 audio_buf 的 short 数（存于静态计数） */
    return last_audio_shorts;
}

JNIEXPORT void JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeReset(JNIEnv *env, jobject thiz) {
    p_retro_reset();
}

JNIEXPORT void JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeUnloadGame(JNIEnv *env, jobject thiz) {
    p_retro_unload_game();
}

JNIEXPORT void JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeDeinit(JNIEnv *env, jobject thiz) {
    game_env = NULL;
    if (p_retro_deinit) p_retro_deinit();
    free(rom_path); rom_path = NULL;
    free(rom_data); rom_data = NULL; rom_size = 0;
    if (core_lib) { dlclose(core_lib); core_lib = NULL; }
    if (cb_obj) { (*env)->DeleteGlobalRef(env, cb_obj); cb_obj = NULL; }
    if (video_buf) { (*env)->DeleteGlobalRef(env, video_buf); video_buf = NULL; }
    video_cap = 0;
    if (audio_buf) { (*env)->DeleteGlobalRef(env, audio_buf); audio_buf = NULL; }
    audio_cap = 0;
}

JNIEXPORT jdoubleArray JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeGetTiming(JNIEnv *env, jobject thiz) {
    jdoubleArray out = (*env)->NewDoubleArray(env, 2);
    jdouble v[2] = { timing_fps, timing_rate };
    (*env)->SetDoubleArrayRegion(env, out, 0, 2, v);
    return out;
}

/* region 取 libretro 的 RETRO_MEMORY_*（SRAM = 0） */
JNIEXPORT jbyteArray JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeGetMemory(JNIEnv *env, jobject thiz, jint region) {
    if (!p_retro_get_memory_data || !p_retro_get_memory_size)
        return NULL;
    void *p = p_retro_get_memory_data((unsigned)region);
    size_t n = p_retro_get_memory_size((unsigned)region);
    if (!p || n == 0)
        return NULL;
    jbyteArray arr = (*env)->NewByteArray(env, (jsize)n);
    (*env)->SetByteArrayRegion(env, arr, 0, (jsize)n, (const jbyte *)p);
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeSetMemory(JNIEnv *env, jobject thiz,
                                                             jint region, jbyteArray data) {
    if (!p_retro_get_memory_data || !p_retro_get_memory_size)
        return JNI_FALSE;
    void *p = p_retro_get_memory_data((unsigned)region);
    size_t n = p_retro_get_memory_size((unsigned)region);
    if (!p || n == 0)
        return JNI_FALSE;
    jsize len = (*env)->GetArrayLength(env, data);
    if ((size_t)len > n)
        len = (jsize)n;
    (*env)->GetByteArrayRegion(env, data, 0, len, (jbyte *)p);
    return JNI_TRUE;
}

/* 金手指批应用（cheat-codes）：reset 清空码集后逐条 set(enabled)。
   libretro 惯例：核心每帧自行应用码集；须在游戏线程调用（与 retro_run 同线程）。 */
JNIEXPORT jboolean JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeApplyCheats(JNIEnv *env, jobject thiz,
                                                               jobjectArray codes) {
    if (!p_retro_cheat_reset || !p_retro_cheat_set)
        return JNI_FALSE;
    p_retro_cheat_reset();
    jsize n = (*env)->GetArrayLength(env, codes);
    for (jsize i = 0; i < n; i++) {
        jstring js = (*env)->GetObjectArrayElement(env, codes, i);
        if (!js) continue;
        const char *code = (*env)->GetStringUTFChars(env, js, NULL);
        if (code) {
            p_retro_cheat_set((unsigned)i, true, code);
            (*env)->ReleaseStringUTFChars(env, js, code);
        }
        (*env)->DeleteLocalRef(env, js);
    }
    return JNI_TRUE;
}

/* 即时存档：完整模拟状态快照（核心私有格式）。须在游戏线程调用（与 retro_run 同线程）。 */
JNIEXPORT jbyteArray JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeSaveState(JNIEnv *env, jobject thiz) {
    if (!p_retro_serialize || !p_retro_serialize_size)
        return NULL;
    size_t n = p_retro_serialize_size();
    if (n == 0 || n > (size_t)64 * 1024 * 1024)
        return NULL;
    void *buf = malloc(n);
    if (!buf)
        return NULL;
    jbyteArray arr = NULL;
    if (p_retro_serialize(buf, n)) {
        arr = (*env)->NewByteArray(env, (jsize)n);
        if (arr)
            (*env)->SetByteArrayRegion(env, arr, 0, (jsize)n, (const jbyte *)buf);
    }
    free(buf);
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_huffcart_core_libretro_LibretroCore_nativeLoadState(JNIEnv *env, jobject thiz,
                                                             jbyteArray data) {
    if (!p_retro_unserialize || !data)
        return JNI_FALSE;
    jsize n = (*env)->GetArrayLength(env, data);
    if (n <= 0)
        return JNI_FALSE;
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    if (!buf)
        return JNI_FALSE;
    jboolean ok = p_retro_unserialize(buf, (size_t)n) ? JNI_TRUE : JNI_FALSE;
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
    return ok;
}
