# Design

## Context

现有联机为 2 人协议：Input 消息只携带 p1Mask/p2Mask；建房即入房（无名称/容量/游戏预选），房主在房间屏现选游戏；`NetplayServer` 链路 `readTimeoutMs = 30_000` 且大厅握手完成后无任何流量——房间闲置 30 秒必死（netplay-tab 回归 3.1 确诊，4 次断开均为精确 +30s）。UI 层 NetplayScreen 为表单式列表，无房间卡片/状态条/搜索。用户已确认四项决策：真 4 人输入同步、参考图布局 + 本 app 米白红主题、省略连接方式开关、建房时选游戏。

## Goals / Non-Goals

**Goals:**

- 联机大厅 v2：状态条 + 搜索 + 房间卡片 + 建房页（名称/2-4 人/选游戏/开放）
- 房间页组队化：房间码/状态/人数信息行 + 游戏卡 + 席位头像 + 准备/开始按钮
- 协议 v2：4 席位输入同步、房间信息广播、入座时 ROM 校验
- 房间长驻：心跳保活，等待加入不限时长
- 视觉：米白 + 任天堂红 + 像素字，与全局主题一致

**Non-Goals:**

- 互联网联机 / 中转服务器（仅局域网；建房页不放连接方式开关）
- 旁观者（观战）席位
- 4 人分屏等本地多人形态（4 人 = 4 台设备各一席位）
- 联机期间存/读/快进（维持「联机期间限制」禁用不变）

## Decisions

1. **协议 v2 = 版本号升级 + 增量消息**：`NETPLAY_PROTOCOL_VERSION` v1→v2；`Input` 增加 `p3Mask`/`p4Mask`（Kotlinx 序列化按字段名兼容，v1 客户端收 v2 消息因版本握手先行拒绝，无需向后兼容）；新增 `Ping`/`Pong` 心跳与房间信息字段。旧版本加入被既有「版本一致性握手」拒绝，符合预期。
2. **席位分配：加入端自动递补首个空位**。房主固定 P1；`JoinSession` 入座时按房间当前占用取 P2→P3→P4；席位随会话固定，中途不复排。`GameScreen.onLocalButton` 按 `Seat` 注入 `core.setButton(port)`，fceumm 支持 0–3 四手柄口（Four Score 类游戏消费 P3/P4）；普通双人游戏只用 P1/P2 掩码，P3/P4 全零无副作用。CRC 校验和机制不变（房主单源广播）。
3. **心跳：双端各自每 10 秒发 `Ping`、对端回 `Pong`**。读超时维持 30s（容忍丢 2 拍）；连续 3 拍无任何入站消息即触发既有断线路径。房间等待加入时长因此不受限（spec「房间长驻保活」）。
4. **广播负载扩展**：NSD service name 与 UDP beacon 携带 JSON `{code, name, capacity, count, game}`（NSD txt ≤ 255B，房间名建议 ≤ 12 字，超长截断；UDP 包无此顾虑）。封面不进广播——卡片用 `CoverResolver` 以游戏名就地解析封面，无封面回退占位图（复用既有规则）。
5. **ROM 校验提前到入座时**：广播含游戏名 → 加入端入座前本地查库做哈希比对（复用既有校验函数），不一致即拒绝入座（spec 场景「ROM 不一致阻止开局」语义随之从"阻止开局"变为"阻止入座"，场景名保留）。校验通过后开局阶段不再重复校验。
6. **UI 结构**：`NetplayScreen` 改版为大厅（状态条 + 搜索 + 卡片列表 + FAB）；新建 `CreateRoomScreen`（路由 `netplay/create`，HuffcartApp 注册）；`RoomScreen` 改版为组队房间页。三屏均用 `AppTopBar`/米白底/红色主按钮/像素字标题；房间卡片与游戏卡复用 `CoverCard` 的封面解析。状态条在线态用 `NetplayManager.wifiAvailable` + 本机 IP（房间码推导已有同源逻辑）。
7. **准备按钮语义**：加入端「准备」= 复用既有 `joinerReady` 状态位（现由 ROM 校验自动置位，改为「校验通过自动置位 + 可手动取消/重备」UI 化）；房主「开始联机」按钮在 `joinerReady` 后可用（全部入座者就绪才亮）。

## Risks / Trade-offs

- [协议 v2 使旧版本互不可联] → 版本握手既有提示兜底；发布节奏上 v2 与 UI 改版同包发布，双端同时升级
- [NSD 负载 255B 限制截断房间名/游戏名] → 超长截断 + 房间码兜底加入路径不受影响；房间名输入框限长
- [四人游戏 P3/P4 无法在真机上凑齐验证] → 双机 + 模拟器凑 3 端验证席位递补与 P1/P2 路径；P3/P4 输入通路用单元测试验证掩码映射（fceumm 端口注入逻辑已有测试惯例）
- [心跳与既有 3s 加入端等待读超时竞争] → 心跳仅在房间/大厅阶段发送，`PLAYING` 阶段由输入流天然保活（60fps），避免叠加
- [本 change 与并行 change（pad-feedback-and-display-settings）同仓] → 触达文件不相交（netplay/*、NetplayScreen/RoomScreen/CreateRoomScreen/GameScreen 局部、HuffcartApp 路由段），无合并冲突面

## Migration Plan

无数据迁移。协议 v2 与 UI 同包发布，双端升级后互联；旧版本按既有握手提示升级。

## Open Questions

（无——四项决策均已经用户确认）
