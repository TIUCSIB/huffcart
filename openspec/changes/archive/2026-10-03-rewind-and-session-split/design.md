# Design

## Context

现状(见 proposal.md - Why):

- `GameScreen.kt`(1302 行)内私有 `GameSession` 类持有:`LibretroCore`、游戏线程、AudioTrack 帧节拍、`pendingCommand` 命令队列(存/读档、金手指、续玩,均在游戏线程帧末执行)、`ffFactor` 快进、联机装配(netplay-lan)、断点续玩、槽位缩略图。
- 序列化桥接已存在:`LibretroCore.saveState()/loadState()` 直通 JNI(`retro_serialize/retro_unserialize`),NES 状态为几十 KB 量级;代码注释已强制「序列化须与 retro_run 同线程」。
- 游戏循环每墙钟帧:联机分支 → 连发相位 → 快进 factor(1–3 个模拟帧) → 帧末命令 → AudioTrack 阻塞写节拍;「只改增益、绝不 pause/停写」是既有音频决策。
- 菜单:竖屏顶栏与横屏浮钮共用菜单项结构,`netplayActive` 时存/读/快进隐藏、仅留退出(spec「联机期间限制」)。

## Goals / Non-Goals

**Goals:**

- 按住倒带约 30 秒覆盖,体感与快进对称(数倍速倒放)。
- 倒带挂入循环不破坏音频帧节拍与联机节拍契约。
- `RewindBuffer` 纯逻辑可 JVM 单测。
- GameSession 抽离后 `GameScreen.kt` 只剩 Compose UI 与装配。

**Non-Goals:**

- 联机对局中的倒带与 rollback 网络同步。
- 倒带时长/开关的用户设置 UI(参数写死,调优留给实现期验证)。
- 慢动作、倒带录像/分享、逐帧精度回退(forward-replay 插值)。
- `RetroCore` 接口扩展(GameSession 直接持 `LibretroCore`,现状即如此)。

## Decisions

**决策 1:采样粒度——每 3 个模拟帧采样一次(20 采样/秒);按住倒带时每墙钟帧回退一个采样。**
回退速率 = 3 模拟帧/墙钟帧 ≈ 3 倍速倒放,与快进 2x/3x 体感对称。按住期间循环不调 `runFrame`,改为弹出采样 → `loadState` → 渲染。备选「restore 后再正向跑 1–2 帧做插值」被否:复杂度高、视觉收益不可辨。

**决策 2:环形缓冲 = `ArrayDeque<ByteArray>` + 字节计数,上限 24MB,zlib(`Deflater`)逐采样压缩。**
30 秒 @20 采样/秒 = 600 采样;NES 状态压缩后约 30–60KB,24MB 足够且对 2GB 内存机型可接受,满则淘汰最老。选 Deflater 而非 GZIP:省每条 16 字节头,缓冲为进程内私有格式无互操作需求。写入在游戏线程(与 `retro_run` 同线程,复用既有命令时序约束)。

**决策 3:倒带开关为 `@Volatile rewindHeld`,UI 按住置位/松开复位;互斥快进;静音走增益。**
按住期间循环强制按 1 墙钟帧节奏回退(快进的 factor 跳过),音频照常阻塞写但增益置 0——沿用「只改增益不停写」决策,帧节拍不破,松开恢复增益即恢复声音。快进互斥天然成立:倒带优先级高,松开后 `ffFactor` 现值自然恢复(spec 场景「与快进互斥」)。

**决策 4:缓冲失效时机——槽位读档、续玩恢复、软复位清空重建;金手指启停不清。**
读档/续玩/复位是时间线切换,旧采样全部作废(spec「读档后缓冲重建」);金手指码是核心状态一部分,序列化已包含,启停后时间线连续,不清缓冲。

**决策 5:入口为快捷菜单「倒带」按住项,横竖屏共用菜单结构。**
`netplayActive` 时与存/读/快进同样隐藏,netplay 主 spec 无需改动。备选「手柄长按 SELECT」被否:与连发组合语义冲突、不可发现。按住期间菜单保持展开,松开不自动收起。

**决策 6:GameSession 抽离为纯移动 + 倒带逻辑独立成可测纯类。**
`GameSession` 整体迁至 `app/src/main/java/com/huffcart/app/ui/game/GameSession.kt`(internal),`GameScreen.kt` 仅留 UI;diff 审查标准=仅移动/import/可见性。倒带的采样与环形缓冲做成 `RewindBuffer` 纯类(无 Android 依赖)供 JVM 单测;循环内的接线保持薄。`RetroCore` 接口不动——倒带用 `LibretroCore.saveState()/loadState()`,契约漂移风险为零。

## Risks / Trade-offs

- [每秒 20 次 saveState 的帧内耗时毛刺] → serialize 为 memcpy 级(几十 KB),摊到每 3 模拟帧一次;真机实测掉帧则采样间隔调至 6 帧(10/s),参数调整不改契约。
- [倒带精度受限于 50ms 采样间隔] → 远低于人类反应阈值,接受;不做 forward-replay。
- [24MB 常驻内存] → 会话级生命周期,退出即释放;低端机超预算则降上限。
- [静音切回正常增益瞬间的爆音] → 松开首帧先写一小段静音再恢复增益;列入真机验收点。
- [700 行移动引入手误] → 纯移动纪律 + 全量单测 + 真机冒烟三重验证。
- [倒放帧率受低端机 loadState 耗时影响] → loadState 同为 memcpy 级;真机若明显卡顿,倒放速率自适应(每墙钟帧回退 2 采样)——体感降级不改行为契约。

## Migration Plan

无数据迁移;回滚 = revert 提交。参数(采样间隔/缓冲上限)集中在 `RewindBuffer`/`GameSession` 顶部常量,调优不改 spec。

## Open Questions

无。
