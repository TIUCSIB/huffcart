# Design

## Context

虚拟手柄的触觉反馈硬编码在 `PadControls.kt` 的 `rememberPadHaptic()`（四处调用：十字键 / A/B / SELECT·START / 摇杆），无开关。设置持久化已有轻量约定：SharedPreferences 独立 store（`KeyMappingStore`、`ControlSchemeStore`），且「设置页仅主界面可达，游戏屏进入时读取一次」。渲染缩放集中在 `GameScreen.kt` `GameSession.loop()` 的整数倍缩放 letterbox（`isFilterBitmap = false` 最近邻）。设置页「画面设置」为禁用占位；「虚拟手柄样式」已有一套 FC 菜单式单选弹窗（`SchemePickerDialog`，KeyMappingScreen 私有）。

## Goals / Non-Goals

**Goals**
- 两个反馈开关（触觉 / 按键音效）独立、持久化、默认开启，统一接入现有四处触发点。
- 按键音零素材依赖落地（程序合成），保留后续换音频素材的替换点。
- 画面比例三档（原生 8:7 / 4:3 / 铺满），默认档与现状完全一致。
- 画面设置页从占位解锁，页面骨架可容纳后续显示类设置。

**Non-Goals**
- CRT 滤镜 / 扫描线（仅留页面结构位）。
- 游戏内 ⚙️ 菜单切换画面比例（设置页改完再进游戏即生效，足够）。
- 物理键盘通路的触觉/音效反馈。
- 反馈强度分级、每键独立音色。

## Decisions

1. **反馈运行时用进程级单例 `PadFeedback`，不用参数穿透**。两处开关要被 PadControls 四个控件读到，穿透需改 6 个组件签名；反馈是横切关注，单例 + 游戏屏启动 `init(context)` 读一次（加载开关、惰性建音轨）最省。开关在设置页改动后，重进游戏屏 init 重读即生效——与 controlScheme「读一次」模式语义一致。备选：CompositionLocal / 双 boolean 参数穿透——前者对本项目过重，后者签名噪音大。

2. **按键音 = AudioTrack MODE_STATIC + 运行时合成 PCM 短脉冲**。约 6ms 方波（~1.8kHz）快速衰减包络，构造时写入一次缓冲，按压时 `setPlaybackHeadPosition(0) + play()`。为何不用 SoundPool：SoundPool 只吃 resource/file，合成 PCM 还得先落临时 wav，多一道无谓工序；为何不用 ToneGenerator：延迟大、音色生硬。混音走 USAGE_MEDIA + CONTENT_TYPE_SONIFICATION（媒体流，音量随媒体音量，与游戏 AudioTrack 并行混音不冲突）。后续换真素材时只替换该对象内部的播放实现，调用方与开关逻辑不动。

3. **触觉与音效统一入口 `rememberPadFeedback()`**（替代原 `rememberPadHaptic()`）：一个 lambda 内先判开关再震/响，四个触发点一处不改语义；换向不重复触发的约束由现有调用位置天然满足（只在 down 触发）。

4. **画面比例枚举 `DisplayAspect { NATIVE, RATIO_4_3, STRETCH }` + `VideoSettingsStore`**（沿用 ControlSchemeStore 的「枚举 + store 同文件」布局）。`GameSession` 构造时读一次；渲染分支：NATIVE 走原整数倍 letterbox；RATIO_4_3 按 4:3 盒适配居中（浮点缩放）；STRETCH 铺满画面区。三种都走现有最近邻 paint（非整数档位像素宽度轻微不均但保持锐利，主流模拟器同做法）。

5. **抽取共用 FC 单选弹窗**。`SchemePickerDialog` 的白卡 + 红色光标模式在画面比例弹窗完全复用，抽成共享组件（标题 + 选项列表 + 选中态 + 「点选即生效」），按键设置页与画面设置页各传一套选项；避免复制 60 行弹窗代码。

6. **按键设置页开关行用 Material3 Switch**（track/thumb tint 品牌红），行布局对齐现有 `MappingRow` 的左标签右值结构；不为开关再造 FC 风格控件（单选弹窗保持 FC 风格已足够，开关是二态，弹窗交互反而绕）。

## Risks / Trade-offs

- [MODE_STATIC 音轨并发按压重启截断] → 脉冲仅 ~6ms，重启不可感知；播放调用收敛在 UI 线程。
- [4:3 / 铺满打破整数倍缩放像素均匀性] → 最近邻保底锐利；默认档不变，用户显式选择才进入。
- [「画面呈现」requirement 与 game-landscape-fullscreen delta 重叠] → 本 change 的 MODIFIED 文本已并入横屏全屏版本；**归档顺序必须先 game-landscape-fullscreen 再本 change**（前者已 9/9 完成）。
- [旋转 / 进程回收后重读设置] → 游戏屏 manifest 配置 configChanges 旋转不重建会话，不存在中途换值；进程回收后重建会话重读一次即新值，符合「下次进入生效」。

## Migration Plan

无数据迁移：新增 SharedPreferences 键，缺省即默认值（触觉开 / 音效开 / 比例原生），老用户渲染行为与现状一致，仅音效为新增可感变化（可关）。回滚即还原代码，无残留状态。

## Open Questions

- 按键音音色参数（频率 / 时长 / 音量）需真机听感调优——不阻塞结构与任务拆分，真机验证时定稿。
