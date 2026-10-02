# Proposal

## Why

单人模拟器链路（播放、存档、快进）已稳定落地，下一个量级的价值是"和一起长大的小伙伴重新打一局魂斗罗"。本 change 落地局域网双人联机：一方创建房间、另一方加入、房间内显示玩家、开同一款游戏实时对战/合作。范围与用户探索共识：仅同一 Wi-Fi（含热点直连）、ROM 内置进应用（同 APK 天然同款，免去传输与配对）、v1 双人席位。

## What Changes

- 新增 `netplay` 包：`NetplayTransport` 接口 + 局域网 TCP 实现（NSD 服务发现 + 手输 IP 兜底）
- 房间协议：建/加/玩家列表/版本与 ROM 哈希握手/开局状态快照/逐帧输入流/失同步校验
- 同步模型：房主为权威端（音频节拍不变），每帧广播 P1/P2 输入；加入端以收到主机帧输入为节拍镜像运行（两台设备音频时钟有差，必须跟随主机时钟）
- 联机 UI：联机屏（昵称、创建、附近房间列表、手输 IP）、房间屏（玩家列表 + 房主选游戏 + 开始）、游戏中联机状态标识与断线提示
- 联机中禁用快进与即时存/读档入口（节拍失配与状态失同步）
- 内置 ROM 播种：构建期将 ROM 放入 assets，首次启动解压播种进现有游戏库目录（沿用 iNES 校验），库中与导入游戏无差别呈现
- **不包含**（后续 change）：互联网联机（中继/服务器）、蓝牙传输、观战席位、房间内 ROM 传输、断线重连、rollback、联机中的存读档/快进

## Capabilities

### New Capabilities

- `netplay`: 同一 Wi-Fi 双人联机——房间创建/发现/加入、玩家列表、一致性握手、开局状态同步、逐帧输入流与失同步提示、联机期间的限制

### Modified Capabilities

- `game-library`: 新增"内置游戏播种"需求——首次启动将随应用打包的内置 ROM 播种进游戏库，呈现与导入游戏无差别

## Impact

- `:app` 新增 `netplay` 包（transport / 协议编解码 / 会话状态机）与联机、房间两个 Compose 屏
- `GameScreen.kt` 的 `GameSession` 增加联机挂接点：加入端节拍改为跟随主机输入流、输入按席位注入、联机中隐藏存/读/快进 chips、顶部联机横幅
- `HuffcartApp` 首页顶栏加联机入口 + 两条新路由（联机屏、房间屏）
- assets/roms 内置 ROM（构建期放入，不入 git）；首次启动播种逻辑
- 无新第三方依赖：NSD 用平台 `NsdManager`，传输用 `java.net` TCP；协程已在依赖中
- 不触碰 `core-bridge` / `core-native`（`setButton(player, …)` 与 `saveState/loadState` 原语已具备）
