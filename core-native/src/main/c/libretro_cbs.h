/* libretro API 的最小手写声明——只包含本 shim 用到的常量与结构。
 * 数值是稳定 ABI，不得改动；完整定义见 libretro.h（libretro-fceumm 仓库内）。 */
#ifndef LIBRETRO_CBS_H
#define LIBRETRO_CBS_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* --- retro_device_id：FC 手柄只用到这些 --- */
#define RETRO_DEVICE_NONE     0
#define RETRO_DEVICE_JOYPAD   1
#define RETRO_DEVICE_ID_JOYPAD_B      0
#define RETRO_DEVICE_ID_JOYPAD_Y      1
#define RETRO_DEVICE_ID_JOYPAD_SELECT 2
#define RETRO_DEVICE_ID_JOYPAD_START  3
#define RETRO_DEVICE_ID_JOYPAD_UP     4
#define RETRO_DEVICE_ID_JOYPAD_DOWN   5
#define RETRO_DEVICE_ID_JOYPAD_LEFT   6
#define RETRO_DEVICE_ID_JOYPAD_RIGHT  7
#define RETRO_DEVICE_ID_JOYPAD_A      8

/* --- 内存区域 --- */
#define RETRO_MEMORY_SAVE_RAM 0

/* --- 环境命令（数值即 ABI） --- */
#define RETRO_ENVIRONMENT_EXPERIMENTAL 0x10000
#define RETRO_ENVIRONMENT_PRIVATE      0x20000
#define RETRO_ENVIRONMENT_GET_CAN_DUPE                  3
#define RETRO_ENVIRONMENT_SET_MESSAGE                   6
#define RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY          9
#define RETRO_ENVIRONMENT_SET_PIXEL_FORMAT             10
#define RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS        11
#define RETRO_ENVIRONMENT_SET_VARIABLES                14
#define RETRO_ENVIRONMENT_GET_VARIABLE                 15
#define RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE          17
#define RETRO_ENVIRONMENT_GET_LOG_INTERFACE            27
#define RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY           31
#define RETRO_ENVIRONMENT_GET_CLEAR_ALL_THREAD_WAITS_CB 81 /* 未用，占位 */

#define RETRO_PIXEL_FORMAT_0RGB1555 0
#define RETRO_PIXEL_FORMAT_XRGB8888 1
#define RETRO_PIXEL_FORMAT_RGB565   2

typedef void (*retro_environment_t)(unsigned cmd, void *data);
typedef void (*retro_video_refresh_t)(const void *data, unsigned width,
                                      unsigned height, size_t pitch);
typedef void (*retro_audio_sample_t)(int16_t left, int16_t right);
typedef size_t (*retro_audio_sample_batch_t)(const int16_t *data,
                                             size_t frames);
typedef void (*retro_input_poll_t)(void);
typedef int16_t (*retro_input_state_t)(unsigned port, unsigned device,
                                       unsigned index, unsigned id);

enum retro_log_level {
    RETRO_LOG_DEBUG = 0,
    RETRO_LOG_INFO,
    RETRO_LOG_WARN,
    RETRO_LOG_ERROR,
    RETRO_LOG_DUMMY = 0x7FFF
};
typedef void (*retro_log_printf_t)(enum retro_log_level level,
                                   const char *fmt, ...);

struct retro_log_callback {
    retro_log_printf_t log;
};

struct retro_system_info {
    const char *library_name;
    const char *library_version;
    const char *valid_extensions;
    bool need_fullpath;
    bool block_extract;
};

struct retro_game_geometry {
    unsigned base_width;
    unsigned base_height;
    unsigned max_width;
    unsigned max_height;
    float aspect_ratio;
};

struct retro_system_timing {
    double fps;
    float sample_rate;
};

struct retro_system_av_info {
    struct retro_game_geometry geometry;
    struct retro_system_timing timing;
};

struct retro_game_info {
    const char *path;
    const void *data;
    size_t size;
    char *meta;
};

struct retro_variable {
    const char *key;
    const char *value;
};

#endif /* LIBRETRO_CBS_H */
