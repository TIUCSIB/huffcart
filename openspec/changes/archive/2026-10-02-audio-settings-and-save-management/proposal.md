# Proposal

## Why

「我的」设置里「声音设置」「存档管理」两个条目自首版起就是「即将推出」占位：音频链路已完整但用户没有任何可控项，即时存档只有单槽、无法挑选与清理。两者都是核心游玩体验的收尾件，且实现成本低（不动 native 层）。

## What Changes

- 新增声音设置页：游戏音量滑条、静音开关、快进时静音选项（默认保留声音）；选择即时持久化，再次进入游戏屏生效
- 即时存档从单槽升级为每游戏 4 槽：快捷菜单「存档 / 读档」点开后弹槽位面板（槽号 / 缩略图 / 时间戳），点槽即存 / 即读；覆盖已有槽 SHALL 给可见反馈；竖屏与横屏行为一致
- 新增存档管理页：从「我的」进入，按游戏列出存档槽，支持浏览（缩略图 / 时间戳）与删除
- 「我的」页两个占位条目解锁为可进入页面
- 非目标：存档导出 / 导入分享、云备份；触觉反馈 / 按键音效开关不迁移（留在按键设置页）；倒带回溯不做

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-shell`: 「设置页结构」——声音设置、存档管理从禁用占位改为可进入页面，并新增两页的结构与内容要求
- `game-playback`: 「音频输出」——游戏音频音量与静音 SHALL 跟随声音设置，选择持久化
- `fast-forward`: 「快进调速」——新增快进期间声音行为选项（快进静音开关，默认保留声音）
- `save-states`: 「即时存档」「即时读档」改写为 4 槽槽位制（槽位面板、缩略图、时间戳、覆盖反馈）；新增「存档槽位管理」需求

## Impact

- app 模块 UI 层：SettingsScreen（解锁两行）、HuffcartApp（两条新路由）、新增 AudioSettingsScreen / SaveManagementScreen、GameScreen 快捷菜单存 / 读档改造、AudioTrack 音量 / 静音挂接、快进静音逻辑
- 新增存储类：AudioSettingsStore（沿用 VideoSettingsStore 的 SharedPreferences 模式）；存档槽文件从 `.state0` 扩展为 `.state0`–`.state3`，每槽附缩略图（复用 CoverStore 截图机制）与时间戳元数据
- 兼容：旧单槽 `.state0` 直接作为 1 号槽沿用，无需数据迁移
- 核心层（core-bridge / core-native）零改动；netplay 不受影响（联机对局中存 / 读档入口本就被隐藏）
