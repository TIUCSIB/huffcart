# Proposal

## Why

v1 体验底座的最后两块（grill 共识 Q6）：即时存档/读档把玩家从"必须找到游戏内存档点"中解放出来，快进用来碾过慢节奏段落。libretro-playback 已归档、用户真机确认核心链路稳定，现在具备叠加体验层的条件。

## What Changes

- `:core-native` shim 补 libretro 状态序列化三符号（`retro_serialize_size` / `retro_serialize` / `retro_unserialize`），`LibretroCore` 暴露 `saveState()` / `loadState()`
- `GameSession` 增加命令通道：存/读在游戏线程串行执行（维持 retro_* 单线程纪律不破）
- 存档持久化：`romsaves/<游戏名>.state0`（v1 单槽位），跨进程重启有效
- 游戏屏右上角按钮组：存档 / 读档 / 快进（1x→2x→3x 循环），操作即时反馈
- 快进实现：每墙钟帧连跑 N 个模拟帧、仅渲染末帧，期间跳过音频写入（静音），恢复 1x 出声
- **不包含**（后续 change）：多存档槽与槽位管理、存档缩略图、倒带（rewind）、慢放、自动存档

## Capabilities

### New Capabilities

- `save-states`: 即时存档/读档——完整模拟状态的一键封存与恢复，跨应用重启持久有效
- `fast-forward`: 快进——1x/2x/3x 循环调速，期间保持可操作

### Modified Capabilities

（无——`game-playback` 既有需求不变，本 change 纯增量。）

## Impact

- `:core-native`：shim 增 3 个符号绑定与 JNI 入口；`LibretroCore` 增两个公开方法
- `:app`：`GameScreen` 增按钮组；`GameSession` 帧循环支持倍率与命令通道
- 新增存档文件 `romsaves/*.state0`（每个数百 KB，随游戏数线性）
- 无新依赖；FCEUmm 核心不变（`CORE_COMMIT.lock` 不动，构建链零改动）
