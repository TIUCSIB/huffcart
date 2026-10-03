# Proposal

## Why

即时存档救不了"手滑一秒"：魂斗罗跳错、塞尔达摔崖，玩家要么读档回几分钟前，要么重开。倒带让玩家按住一个按钮就把最后几十秒"倒回去"，是模拟器情感价值最高的缺失功能；而倒带要挂进游戏循环心脏(帧节拍/命令队列/序列化)，当前循环深埋在 1300 行的 `GameScreen.kt` 私有类里，先把它抽成独立非 UI 的 `GameSession` 再动刀，重构与功能互为手术时机。

## What Changes

- **倒带 (Rewind)**：游戏运行中，游戏线程自动按固定间隔序列化压缩状态入环形缓冲(约 30 秒覆盖，内存有上限)；快捷菜单提供「按住倒带」项——按住期间游戏可见地回退，松开从该点继续正常游玩；倒带期间静音，松开声音恢复；缓冲耗尽自动停止并提示；读档/续玩/重新开局后缓冲重建；联机对局中入口不呈现(沿用「联机期间限制」契约)。
- **GameSession 抽离**：把 `GameScreen.kt` 内私有 `GameSession` 类(游戏循环、音频节拍、命令队列、联机装配)抽为独立非 UI 文件，纯重构零行为变化，为倒带挂载与后续可测试性铺路。

## Capabilities

### New Capabilities

- `rewind`: 按住倒带能力——自动状态采样环形缓冲、按住回退与松开续玩、缓冲生命周期(耗尽/失效重建)与联机限制。

### Modified Capabilities

（无——GameSession 抽离为纯重构，不产生任何可观察行为变化，不动任何既有需求；倒带的联机限制沿用 netplay 主 spec 既有「联机期间限制」契约，无需改动该能力。）

## Impact

- `app/src/main/java/com/huffcart/app/ui/screens/GameScreen.kt`：GameSession 类整体迁出 + 菜单新增「按住倒带」项(单机呈现、联机隐藏)。
- 新增 `app/src/main/java/com/huffcart/app/ui/game/GameSession.kt`：迁出后的会话类，新增倒带环形缓冲与按住开关。
- `core-native`/`core-bridge`：无改动——`LibretroCore.saveState()/loadState()` 桥接已存在(shim 已接 `retro_serialize/retro_unserialize`)，倒带在应用层组合即可。
- 存档槽位/金手指/快进/联机协议：无契约改动；快进与倒带互斥(会话内切换)。
