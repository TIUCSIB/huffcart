# Design

## Context

骨架已交付：`:core-bridge`（纯 Kotlin，`RetroCore` 接口 + 假实现测试）、`:app`（游戏页占位 `GamePlaceholderScreen`、游戏库空状态含导入占位按钮、`ndk { abiFilters }` 注释位）。构建环境：NDK 27、JBR 21、minSdk 26；构建必须经 `H:` 盘 ASCII 映射执行。许可基线：FCEUmm 为 GPL-2.0，随 app 分发需开源（自用/分享场景已由 grill 共识接受）。

## Goals / Non-Goals

**Goals:**

- FCEUmm 核心可复现编译（脚本 + 版本锁定），产出 arm64-v8a 与 x86_64 两份 `.so`
- `RetroCore` 接口的真实实现打通加载/逐帧/视频/音频/输入全链路
- 游戏运行屏达到 spec 的观感标准（整数缩放、锐利像素、letterbox）与手感底线（虚拟手柄可操作）
- 最小可用的 ROM 导入（SAF），让验收闭环不依赖任何权限
- 电池存档（SRAM）持久化，保证塞尔达类游戏进度不丢

**Non-Goals:**

- 即时存档/读档、快进、慢放
- 手柄自定义布局、震动、蓝牙手柄映射
- 封面墙/游戏信息页（本期为简单列表）
- CRT 滤镜（AGSL）——游戏屏预留特性检测点即可，不实现
- 周期级精确模拟与兼容性调优（FCEUmm 自带）

## Decisions

1. **新增 `:core-native` Android library 模块承载 JNI 层，`:core-bridge` 保持纯 Kotlin**：接口模块（含假实现）继续可在桌面 JVM 测试；C shim（`retro_*` 回调转接）+ `LibretroCore`（JNI 声明与实现）全部隔离在 core-native，经 CMake 由 AGP 外部原生构建编译。备选"把 NDK 塞进 core-bridge"会破坏零 Android 依赖的桌面调试彩蛋（骨架 design 决策 2），否决。
2. **薄 C shim 策略**：C 侧实现全部 `retro_set_*` 回调；视频帧按 XRGB8888 请求，`memcpy` 进复用的 `jintArray`（补 `0xFF` alpha 后即 Bitmap 可用的 ARGB）；音频 `memcpy` 进复用 `jshortArray`；输入 `input_state` 回调在游戏线程上 JNI up-call Kotlin 的按键位掩码（60Hz 一次，开销可忽略）。不引入 jniHelper 重型封装。
3. **软件 Canvas 渲染**：`SurfaceView` + 复用 `Bitmap`（`copyPixelsFromBuffer`）+ `Canvas.drawBitmap` 整数倍缩放（`isFilterBitmap = false` 保锐利），letterbox 居中；256×240@60fps 走软件路径绰绰有余。备选 GL/Vulkan 纹理路径对本期是过度设计，留作滤镜 change 的演化方向。
4. **以音频为节拍器**：游戏线程按 `av_info.timing.fps` 驱动，`AudioTrack.write`（blocking）天然节流音频产出节奏，视频帧随行，避免双时钟漂移；`AudioTrack` 采样率直接采用核心报告值（≈48049.6Hz，AudioTrack 支持任意率）。
5. **核心编译脚本化**：`scripts/build-core.sh` — `git clone --depth 1` libretro/libretro-fceumm（pin 到 apply 时 master commit，写入 `scripts/CORE_COMMIT.lock`）→ `make platform=android`（NDK 独立工具链）分别构建 arm64-v8a 与 x86_64 → 拷贝 `libfceumm_libretro.so` 至 `app/src/main/jniLibs/<abi>/`。`.so` 不入 git（gitignore），clone 后跑一次脚本即可再生，仓库不背二进制。
6. **ROM 导入走 SAF**：`ActivityResultContracts.OpenDocument`（mime `application/octet-stream` + `.nes`），复制进 `filesDir/roms/`，导入时校验 iNES 头（`NES\x1a`）；游戏库列表即扫描该目录。全程零权限、零 ContentProvider。
7. **SRAM 持久化**：unload 前经 `retro_get_memory(RETRO_MEMORY_SAVE_RAM)` 读出写入 `filesDir/roms/<同名>.srm`；load 成功后注入回核心。这是 FC 游戏的原生存档机制，不与"即时存档"（后续 change）混淆。
8. **游戏屏结构**：Compose `AndroidView` 包 `SurfaceView` 全屏沉浸，虚拟手柄为 Compose Canvas 浮层（半透明、固定布局：左十字、右 A/B、中下 Start/Select），返回键先于系统返回被游戏屏拦截用于退出确认-free 直接返回。

## Risks / Trade-offs

- [fceumm 的 x86_64 Android 构建若编译失败] → 降级为仅 arm64-v8a，模拟器改用支持 ARM 翻译的镜像验证或直接真机验收；脚本按 ABI 独立构建，失败互不阻塞
- [音频 blocking write 作为节拍器若遇设备缓冲抖动导致卡顿] → 缓冲取 ~50ms 并在验收时实测；备用方案（Choreographer 双时钟）仅在后问题时启用
- [.so 不入库导致新 clone 构建前必须跑脚本] → scripts 一键化 + README 注明；换来仓库无 2-4MB 二进制
- [JNI up-call 输入查询的线程归属错误会崩溃] → retro_run 与注册回调发生在同一游戏线程，shim 仅在该线程缓存 JNIEnv，纪律写入 shim 头注释
- [非 ASCII 路径破坏 NDK/测试子进程] → 沿用 `H:` 盘映射纪律（骨架 design Risks），脚本内显式断言工作目录为 ASCII

## Open Questions

- 无阻塞项。验收用自由分发 homebrew ROM（240p Test Suite，GPL）在 apply 期下载；五款用户 ROM 的走查依赖用户自备文件。
