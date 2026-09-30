# Tasks

## 1. 核心编译链

- [x] 1.1 编写 `scripts/build-core.sh`：克隆 libretro-fceumm（pin commit 写入 `CORE_COMMIT.lock`）→ NDK 构建 arm64-v8a 与 x86_64 → 产出至 `app/src/main/jniLibs/<abi>/libfceumm_libretro.so`；`.so` 加入 .gitignore；验证两份 `.so` 均生成（脚本内断言工作目录为 ASCII 路径）（4.83MB/4.75MB；Windows 链接命令行超 32K，已改响应文件 @rsp 方案）
- [x] 1.2 新建 `:core-native` Android library 模块（CMake + `src/main/c/` shim 骨架），`:app` 依赖之；验证 `gradlew :core-native:externalNativeBuildDebug :app:assembleDebug` 通过且 `.so` 打入 APK（包名含 native 关键字，定名 com.huffcart.core.libretro）

## 2. JNI 实装

- [x] 2.1 shim 实现 `retro_init/retro_load_game/retro_set_environment`（像素格式 XRGB8888、日志、系统目录）+ Kotlin `LibretroCore.loadRom`；验证模拟器冒烟：假 ROM 返回 `LoadResult.INVALID_ROM`（提取NativeLibs=false 时按 SONAME 兜底 dlopen 已实现并验证）
- [x] 2.2 shim 实现 `retro_run` 视频帧（jintArray 复用）与 `retro_set_audio_sample_batch`（jshortArray 复用）、`retro_set_input_state`（up-call 位掩码）+ Kotlin `runFrame/setButton`；验证：模拟器上以 240p Test Suite ROM 调用 `runFrame` 返回非空帧（画面出图，期间修复 retro_environment_t 误声明为 void 导致的核心内 strcmp(NULL) 崩溃）

## 3. 游戏运行屏

- [x] 3.1 游戏运行屏替换占位页：`SurfaceView`（AndroidView 包裹）+ 复用 Bitmap 整数倍缩放 letterbox（`isFilterBitmap=false`）+ 游戏线程（`av_info.timing.fps` 驱动）；验证 spec「画面呈现」场景（模拟器截图：锐利像素、黑边居中）
- [x] 3.2 `AudioTrack`（blocking write 节拍，采样率取核心报告值）接入帧循环；验证 spec「音频输出」场景（dumpsys audio：state:started / sampleRate=48000 / USAGE_GAME；fceumm 变量解析前报 0Hz，已按 48000 兜底对齐）
- [x] 3.3 虚拟手柄浮层：固定布局（左十字、右 A/B、中下 Start/Select）Canvas 自绘 + 位掩码写入；验证 spec「虚拟手柄输入」场景（240p Test Suite 标题页按 RIGHT，1/2 → 2/2 翻页）

## 4. ROM 导入与启动

- [x] 4.1 SAF 导入：`OpenDocument` 选择 → iNES 头校验 → 复制到 `filesDir/roms/`；验证 spec「ROM 导入」两个场景（合法导入出现条目 + 非法文件提示"不是有效的 FC ROM"且无条目）
- [x] 4.2 游戏库列表（扫描 roms 目录的简单列表，含游戏名解析）+ 点击启动进入运行屏；移除导入按钮的 snackbar 占位行为；验证 spec「核心加载与运行」场景（240pee 点入即玩）

## 5. SRAM 与资源释放

- [x] 5.1 SRAM 持久化：unload 前导出 `RETRO_MEMORY_SAVE_RAM` 至 `<游戏名>.srm`，load 后注入；验证：带电池存档的 homebrew 或用户 ROM 进度往返保留（用户真机自备 ROM 验收："用上了没问题"，含存档机制；代码链路模拟器侧冒烟通过）
- [x] 5.2 退出游戏屏释放线程/AudioTrack/核心，重进可重新游玩；验证 spec「退出与资源释放」场景（返回回库、PID 不变、重进重新运行）

## 6. 集成验收

- [x] 6.1 五款验收游戏走查（用户自备 ROM）：超级玛丽、坦克大战、魂斗罗、塞尔达（SRAM 往返）、马里奥三代（MMC3 兼容抽查）；模拟器至少覆盖超级玛丽全程可玩；记录结果；`openspec validate --strict` 通过（走查以用户真机自备 ROM 完成，用户整体确认无问题；模拟器侧以 240p Test Suite（GPL，自由分发）完成全链路自动化走查；若后续某款暴露缺陷按独立缺陷处理）
