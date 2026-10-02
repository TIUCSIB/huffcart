# Design

## Context

现状（见 proposal.md · Why）：`RetroCore` 已具备联机所需的全部核心原语——`setButton(player, …)` 按席位注入输入、`saveState()/loadState()` 状态快照；游戏循环是 `GameScreen.kt` 中 `GameSession` 的单游戏线程，以 **AudioTrack 阻塞写为节拍**；两端跑同一 APK 内的同一 FCEUmm .so。网络层为空白，无任何网络依赖。

关键约束：retro_* 全部回调必须留在单一游戏线程（core-native 线程纪律）；游戏库就是 `filesDir/roms` 下的 `.nes` 文件列表，播种可以零模型改动接入。

## Goals / Non-Goals

**Goals:**

- 同一 Wi-Fi（含房主热点）双人联机：建/加房间、玩家列表、同款游戏实时同屏对战
- 失同步可检测可提示（用户能区分"游戏 bug"与"联机失同步"）
- 协议层与传输层解耦，为蓝牙等后续传输留门
- 协议编解码与房间状态机为纯 Kotlin，可 JVM 单测

**Non-Goals:**

- 互联网联机（中继/NAT 穿透）、蓝牙传输实现、观战席位、断线重连、rollback
- 联机中的存读档/快进（入口禁用而非实现联机版存档）
- 多于 2 人的控制器席位（FC 原生只有 P1/P2）

## Decisions

### 1. 同步模型：主机权威 + 输入中继，加入端镜像运行

三选一：视频串流（房主推画面）、逐帧输入镜像（双方各跑核心）、rollback（GGPO 式重模拟）。

选**逐帧输入镜像**：画面不进网络（256×240@60fps 原始帧约 15MB/s，串流不可行且毫无必要）；输入掩码每帧几字节；双方跑同一 .so + 同一 ROM + 同一输入流 ⇒ 确定性保证画面一致。rollback 对模拟器是重写节拍模型（每帧可能多次回退重跑），与 AudioTrack 节拍架构冲突，v1 明确不做。

角色与节拍（本设计最重要的决定）：

```
房主（权威端）                          加入端（跟随端）
+---------------------------+          +---------------------------+
| 游戏线程：AudioTrack 节拍    |          | 游戏线程：改为以「收到主机   |
| runFrame → 广播 INPUT       |  TCP     | INPUT」为节拍              |
| (帧号+P1+P2 掩码, 每帧)     | -------> | 取到第 N 帧输入 → runFrame  |
| 取最新远端 P2 输入注入       | <------- | （AUDIO 写改非阻塞，容忍     |
|                            | P2 掩码   |   underrun，短促杂音可接受）  |
+---------------------------+          +---------------------------+
```

为什么不双端各自音频节拍：两台设备的音频时钟存在 ppm 级偏差，各自 60fps 自由跑，几分钟内必然错帧 ⇒ 慢性失同步。加入端必须**跟随主机的帧时钟**（阻塞在输入队列上），房主保持既有音频节拍不动。

时延特征：加入端按键从按下到出现在双方屏幕 ≈ 1×RTT（LAN 下 <20ms，无感）；这是 RetroArch netplay 同款延迟模型（delay-based）。

### 2. 开局同步：房主状态快照

房主加载 ROM（注入其 SRAM）后 `saveState()` 快照随 START 发给加入端，加入端加载 ROM 后 `loadState` 对齐。虽然"双方全新开机"理论上也确定，但**双方本地电池存档（SRAM）内容不同**，开机注入即分叉；快照一发消灭全部歧义，且 LAN 下数百 KB 无感。快照也天然是后续"观战者中途加入"的复用点。

### 3. 传输与发现：TCP + NsdManager，传输层接口化

- `NetplayTransport` 接口（host 侧：`start/discover 接纳连接/send/close`；joiner 侧：`browse/connect/send/close`），v1 唯一实现 `LanTransport`：`java.net` TCP（`ServerSocket`/`Socket`）+ `NsdManager` 注册/浏览 `_huffcart._tcp`。
- NSD 兼容性在某 OEM 上不稳 ⇒ 保留手动兜底：**房间码**（探索反馈 3，替代手输 IP）——
  局域网内 IPv4 唯一，取房主地址末段作 3 位码（`192.168.31.66` → `066`），
  加入端用自身 /24 前缀还原房主 IP（`RoomCode` 纯函数 + 单测）。常见家庭
  Wi-Fi 与热点（/24）无歧义；非 /24 子网为已知边界，主路径仍是 NSD 发现。
- **UDP 广播发现**（实机联调反馈：mdnssd 在模拟器上注册/浏览直接报错，
  mDNS lost 事件也经常丢失 ⇒ "搜不到房间"、"退出后条目残留"）：房主每
  1.5s 广播房间通告 `HUFFCART1|<tcpPort>|<房间名>`（255.255.255.255 与 /24
  定向广播双发），加入端联机屏期间监听（持 MulticastLock），与 NSD 结果
  按 host:port 合并。条目生命周期双保险：房主退出时连发 3 个关闭通告
  （tcpPort=0）→ 对端**即时移除**；未收到告别（进程被杀）则由对端 1s 周期
  清扫按 4s TTL 过期。应用 onStop/窗口焦点丢失超 10s 也视为退出并关房
  （MuMu 多虚拟屏按 HOME 不触发 onStop 的特例由此覆盖）。
- 权限：Android 12+ 起 `NsdService` 强制校验 `INTERNET`，TCP socket 同理；
  Wi-Fi 可用性检查需 `ACCESS_NETWORK_STATE` ⇒ manifest 增加这两个**安装期**
  权限（不改"零运行时权限"的既有承诺）。
- TCP vs UDP：TCP 保序可靠，输入流极小，LAN 丢包罕见，队头阻塞风险可忽略（RetroArch 默认同样基于 TCP）；UDP 需自建可靠层，v1 不值得。
- 房主开热点即"同一网段"，走完全相同的 TCP 路径，无需单独实现。
- 蓝牙不实现，但接口已隔离其复杂度（配对、发现）于未来传输插件。

### 4. 协议：长度前缀二进制帧 + 纯 Kotlin 编解码

帧格式 `u32 payload 长度 + u8 类型 + payload`（多字节一律大端；输入掩码含 A 键 bit8，
与 LibretroCore 位布局一致，线上为 u16）。消息集：

| 消息 | 方向 | 载荷 |
|---|---|---|
| HELLO | J→H | 协议版本、应用版本、昵称 |
| WELCOME / REJECT | H→J | 席位 / 拒绝原因（版本不符、满员） |
| PLAYER_LIST | H→J | 成员（席位、昵称） |
| PICK_GAME | H→J | ROM 名、大小、CRC32 |
| READY | J→H | ROM 校验结果（ok + 本地提示文案） |
| START + STATE | H→J | 状态快照（数百 KB，长度前缀复用） |
| GAME_READY | J→H | 加入端装载完快照，房主收到后才进入帧循环 |
| INPUT | H→J | u32 帧号 + u16 P1 掩码 + u16 P2 掩码（每帧） |
| CLIENT_INPUT | J→H | u16 P2 掩码（按键变化即发，另 0.5s 级保活） |
| LEAVE | 双向 | — |

失同步校验搭 INPUT 便车：每 64 帧 payload 追加 u32 = 该帧视频缓冲 CRC32，加入端比对自己同帧画面，不一致即提示（spec「失同步提示」）。编解码为纯函数（`ByteBuffer`），房间/对局状态机不持有 Android 类，直接进 JVM 单测。

### 5. 线程纪律：网络不碰游戏线程

- 房主：游戏线程每帧把 INPUT 交给 writer 线程队列；远端 P2 输入经 `AtomicReference`（最新值槽）在帧首读取。retro_* 单线程纪律零改动。
- 加入端：reader 线程收 INPUT 入队；游戏线程带超时阻塞取队（超时 ⇒ 断线判定）；音频写改 `WRITE_NON_BLOCKING`。
- 断线检测统一：socket 读写异常 / 读超时 / LEAVE 三路归一为断线事件上抛 UI。

### 6. GameSession 挂接与席位映射

`GameSession` 增加可选 `NetplayController`（空实现 = 单机，行为零变化）：

- 席位映射：本机物理输入写"本机席位"（房主→P1、加入端→P2，`handleButton` 改经 controller 路由），对端输入注入另一席位；
- 节拍开关：加入端循环的取帧来源由 AudioTrack 节拍换为输入队列（决策 1）；
- 联机中 `ffFactor` 钳制为 1，UI 隐藏存/读/快进 chips（spec「联机期间限制」），顶部落联机横幅（对方昵称/席位、连接状态、失同步提示复用 FeedbackPill）；
- 退出游戏屏 = LEAVE + 走断线路径（房主续玩单机、加入端退出）。

### 7. UI 与导航

- 首页顶栏（`AppTopBar`）加联机入口；新路由 `netplay`（联机屏：昵称/创建/房间列表/手输 IP）与 `room`（房间屏：玩家列表 + 房主选游戏 + 开始；两端共用，加入端只读房主的选择）。
- 版本号经 `PackageManager` 读取（工程未启用 BuildConfig）；协议版本为代码常量。
- 全部置于 `:app` 模块 `netplay` 包（不新建 Gradle 模块）：单一消费方，保持构建图简单。

### 8. 内置 ROM 播种

- 构建期：本机 ROM 文件夹放入 `app/src/main/assets/roms/`（**不入 git**，`.gitignore` + scripts 内放一个拷贝脚本说明来源）；FC ROM 体积（128KB~1MB/个）对 APK 无压力。
- 首启播种：`MainActivity` 装配前检查一次性标记（SharedPreferences），把 assets/roms 复制进 `filesDir/roms`，沿用 `RomLibrary` 的 iNES 校验；同名校验跳过。仅执行一次 ⇒ 用户移除后不回填（与「移除游戏」需求自洽）。

## Risks / Trade-offs

- [对局中断开时的核心生命周期（真机复现的闪退）] → 对端离开必须"停输入流 + 清积压帧 +
  清 room 态"三件事同步做：加入端若保留 startRequested 状态，返回房间屏会二次导航、
  在刚 deinit 完的共享 FCEUmm 全局态上开新会话 → retro_run SIGSEGV。stop() 的
  join 等待也要 ≥ 加入端 poll 超时（3s），确保游戏线程死后才 deinit。
- [FCEUmm 确定性边界（个别游戏/边缘输入路径）] → 每 64 帧画面 CRC 校验兜底，失同步有明确提示而非静默错乱；实测主流游戏（超级玛丽/坦克大战/魂斗罗）RetroArch 长期验证为可镜像。
- [NSD 在部分 OEM 不稳/不广播] → 手输 IP 兜底为 spec 行为；房间列表刷新按钮。
- [加入端音频 underrun（非阻塞写 + 跟随主机时钟）] → LAN 下时钟差极小，表现为偶发短促杂音，可接受；互联网场景本就超出 v1 范围。
- [TCP 慢消费者拖住房主 writer] → LAN 不可达该状态；writer 队列异常即断线路径，房主回单机续玩。
- [内置 ROM 的版权形态] → 定位为个人/朋友间分发的 APK，明确不可上架（proposal 已声明）；ROM 不入 git。
- [Activity 被系统回收 = 断线] → 走断线提示路径，不静默；进程内旋转不受影响（app 已声明 configChanges 稳定性）。

## Migration Plan

纯增量特性，无数据迁移。回滚 = 还原代码（播种文件是普通库文件，留存无害）。

## Open Questions

（无——探索会话已定：同一 Wi-Fi 含热点、内置 ROM、双人席位、禁快进/存读档。）
