# Proposal

## Why

骨架已归档，`app-shell` 能力就位，但 app 还没有任何"玩"的能力——`RetroCore` 接口只有假实现，游戏页是占位空壳。grill 共识（Q7 方案 C）确定核心走嵌入 libretro FCEUmm 路线，本 change 兑现它：让一部真实游戏能在模拟器/手机上加载、运行、出声、可操作，即项目从"壳"到"能玩"的跃迁。

## What Changes

- 在 `:core-bridge` 新增 JNI 层：薄 C shim 直连 libretro API（初始化/加载/逐帧/视频/音频/输入回调），Kotlin 侧提供 `LibretroCore : RetroCore` 实装
- 新增核心编译链：`scripts/build-core.sh` 克隆 libretro/libretro-fceumm（apply 时 pin 到当日 master commit 并锁定记录）→ NDK 编译产出 `libfceumm_libretro.so` → 注入 `:app` 的 `jniLibs`（arm64-v8a + x86_64，后者供模拟器验收）
- 新增游戏运行屏：`SurfaceView` 逐帧渲染（软件 Canvas + 整数缩放 letterbox）、`AudioTrack` PCM 流输出、60fps 游戏线程（帧率取自 libretro av_info）
- 新增基础虚拟手柄浮层：十字键 + A/B + Start/Select 固定布局（尺寸/透明度自定义、震动等按 Q6 留给后续 change）
- 游戏库「导入 ROM」入口转正：SAF 文件选择器导入 `.nes` 到应用私有目录（无存储权限），当前以文件列表形式可选中启动；封面墙等完整游戏库仍属后续 change
- 替换 `GamePlaceholderScreen` 占位为真实运行屏；`app-shell` spec 中"导入入口为占位"的描述由本 change 的新能力自然覆盖（入口行为升级，不破坏既有需求）

**不包含**：即时存档/读档、快进、手柄自定义与震动、蓝牙手柄映射、金手指、滤镜（AGSL 扫描线）——均为后续 change。

## Capabilities

### New Capabilities

- `game-playback`: 游戏播放——ROM 导入（SAF 最小实现）、核心加载运行、画面呈现（整数缩放 letterbox）、音频输出、虚拟手柄输入、退出与资源释放。

### Modified Capabilities

（无——`app-shell` 的需求不变；其 spec 中"导入入口为占位"是对该期实现的注记而非长期契约，本次将占位升级为真实能力，不构成 spec 级需求变更。）

## Impact

- **许可**：`libfceumm_libretro.so` 为 GPL-2.0，随 app 分发时整个应用需遵循 GPL（自用与 GitHub Releases 分享无碍；README 将注明来源与许可）
- **构建**：`:core-bridge` 引入 NDK/CMake 外部原生构建（新增 `src/main/c/` shim 与 CMakeLists）；`:app` 引入 jniLibs 产物（两个 ABI，合计约 2-4MB）；NDK 27 已就位
- **验证依赖**：验收需可自由分发的 NES homebrew ROM（如 240p Test Suite，GPL）用于模拟器自动化验证；用户自有 ROM（超级玛丽等五款验收游戏）由用户自行导入测试
- **既有代码**：`RetroCore` 接口签名按骨架 KDoc 承诺保持不变（如实现期需微调，须回溯更新骨架归档中的契约说明并在 design 记录）
- **环境**：构建/测试仍须经 `H:` 盘 ASCII 映射执行（见骨架 design Risks，非 ASCII 路径会破坏 NDB/测试子进程）
