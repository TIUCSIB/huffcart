# Proposal

## Why

金手指是探索共识中优先级最高的缺失功能（用户点名）：核心为 FCEUmm（libretro），原生导出 retro_cheat_reset / retro_cheat_set，具备 Game Genie 码即时生效能力；接入成本为 shim 层补 2 个符号 + 管理界面，属高性价比功能。

## What Changes

- shim / LibretroCore 暴露金手指注入能力（retro_cheat_reset / retro_cheat_set，批应用）
- 新增金手指管理面板：游戏内快捷菜单「金手指」入口打开，支持添加 / 逐条开关 / 删除，列表按游戏持久化
- 码格式：Game Genie（6 位或 8 位字母码，连字符可选），非法码拒绝并提示
- 联机对局中金手指入口隐藏、不向核心注入任何码（与存/读档同等的联机限制）
- 非目标：RAM 码（地址:值，待 spike 后另立）、码库搜索/下载、金手指导入导出

## Capabilities

### New Capabilities

- `cheats`: 金手指码的管理、按游戏持久化与运行中即时生效

### Modified Capabilities

（无）

## Impact

- core-native：shim.c 补 dlsym 2 个 cheat 符号与 JNI 批应用转发；LibretroCore.kt 新增 applyCheats API（游戏线程调用）
- app：CheatStore（按游戏 JSON 持久化 + 格式校验）、金手指面板 Compose 组件、GameScreen 菜单加「金手指」入口与会话命令扩展（ApplyCheatsCmd）
- 验收：魂斗罗等经典码（SXIOPO 30 条命）真机冒烟
