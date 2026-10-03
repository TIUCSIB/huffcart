# Design

## Context

音频管线现状（`GameSession.kt`）：核心每帧产样 → 游戏线程以 `AudioTrack.WRITE_BLOCKING` 写入 AudioTrack，阻塞写即帧节拍（audio-settings-and-save-management 决策 1）——循环被钉在核心采样率的消耗速度上，环形缓冲常满，**缓冲深度直接等于端到端音频延迟**。当前缓冲固定 `maxOf(minBuf, rate / 10 * 4)` ≈ 100ms；联机加入端已改为非阻塞写、写满即丢（netplay-lan 决策 5）。`AudioSettings`（音量/静音/快进静音）走 SharedPreferences、构造时读一次，设置页已为后续声音类设置预留分组骨架。

## Goals / Non-Goals

**Goals:**

- 音频输出延迟可配置（三档），默认档显著低于现状（≈100ms → ≈70ms）；
- 各档取舍明确：低延迟换跟手、稳定换抗抖动，用户可自选；
- 联机加入端音频稳健性不因档位回退；
- 提供 underrun 观测手段，支撑真机验收与档位微调。

**Non-Goals:**

- AAudio / Oboe 迁移（大重构，见决策 1 的替代分析）；
- 动态速率控制 / 时间拉伸（RetroArch 式 DRC）；
- 游戏内热切换档位（沿用「改完重进游戏即生效」约定）；
- 核心侧（FCEUmm）音频缓冲与产样改动；netplay 输入/节拍延迟优化。

## Decisions

1. **延迟杠杆 = AudioTrack 缓冲深度，节拍机制不动。**
   阻塞写帧节拍是全项目音画同步的基石，保持不变；把固定 100ms 缓冲改为按档位取值，延迟即随档位下降。
   *替代*：AAudio/Oboe 低延迟流——迁移成本高，且 48kHz 立体声 + `USAGE_GAME` 的 AudioTrack 在 ≥40ms 缓冲下瓶颈本就在缓冲深度而非框架通路；动态速率控制——为「核心时钟 vs 声卡时钟漂移」设计，本项目的节拍本就锁在音频消耗上，无漂移问题。两者均不成比例。

2. **三档毫秒值：低延迟 40ms / 平衡 70ms（默认）/ 稳定 120ms。**
   FC 帧 = 16.67ms：40ms ≈ 2.4 帧（再低则 GC 停顿余量不足）、70ms ≈ 4.2 帧（延迟减半且仍有 4 帧容错）、120ms ≈ 7.2 帧（比现状再多 2 帧抖动余量，作为「稳定」名副其实）。
   默认取**平衡**：本次变更的目的即降延迟；追求现状的用户可选稳定档。毫秒值是 `AudioLatency` 枚举上的常量，实施期可依真机 underrun 观察微调（spec 以「约 N ms」表述，不约束到具体常数）。

3. **缓冲字节 = 档位毫秒值 × 采样率 × 4（双声道 16bit），且永远 ≥ `getMinBufferSize`。**
   沿用现有 `maxOf(minBuf, …)` 形态。部分机型 `getMinBufferSize` 高于 40ms 目标时，低延迟档自动落到设备最小缓冲——这是该设备可达的下限，不报错、不崩。

4. **仅低延迟档请求 `PERFORMANCE_MODE_LOW_LATENCY`。**
   minSdk 26 满足 API 要求。该标志是向框架申请快速通路的提示（sink 不支持时静默回退，无副作用）；平衡/稳定档保持默认通路——档位语义是「缓冲深度的取舍」，只在用户明确要求最低延迟时附加通路提示。

5. **联机加入端缓冲固定按稳定档。**
   加入端游戏循环的节拍来自网络输入到达（`awaitJoinerInput`），音频只能非阻塞写、写满即丢——LAN/Wi-Fi 抖动常超过 40ms，小缓冲会把抖动直接变成持续爆音；且加入端听到的声音本就叠加了网络延迟，「低延迟档」在其端没有意义。房主端照常走用户档位（房主是音频节拍方，其本地手感由档位决定）。实现上是 `start()` 构造 AudioTrack 时按 `netplay?.role` 的一处分支。

6. **设置读取与持久化沿用既有约定。**
   `AudioSettings` 增加 `latencyTier: AudioLatency` 字段（默认 `BALANCED`），`AudioSettingsStore` 在现有 `audio_settings` prefs 增加键；读取用 `runCatching { valueOf }` 回落默认（`VideoSettingsStore` 既有模式），旧版本无该键自然回落，无迁移。GameSession 构造时读一次，`start()` 建 AudioTrack 时生效。

7. **UI 复用画面比例的交互。**
   声音设置页在「快进静音」之后新增「音频延迟」行（左标签 + 当前档位值，行样式对齐既有行），点按弹 `FcOptionDialog` 三档单选，点选即持久化——与画面设置页的「画面比例」行完全同构。

8. **可观测性：5s 节拍日志追加 underrun 计数。**
   现有 `emulation fps=…` 日志追加 `AudioTrack.getUnderrunCount()`（minSdk 26 可用）。用途：真机验收各档表现、为毫秒值微调提供依据；不弹 UI、不上报。

## Risks / Trade-offs

- [低延迟档遇长 GC 停顿（>40ms 缓冲余量）→ 爆音] → 40ms 仍保留 ≥2 帧余量；设置行描述如实标注取舍（「偶发杂音选平衡/稳定」）；underrun 日志可量化验证；用户可随时改档。
- [部分设备最小缓冲高于 40ms，低延迟档达不到标称延迟] → 决策 3 的下限保护；此时表现等同该设备可达最优，不劣于现状。
- [`PERFORMANCE_MODE_LOW_LATENCY` 在部分机型被忽略] → 仅是附加提示，正确性与节拍不受影响；主要杠杆始终是缓冲深度。
- [「加入端档位无效」对用户是隐藏行为] → spec 已明示该例外；声音设置页描述不提联机（避免 UI 噪音），行为差异记录于本设计与 spec。
- [默认档变化改变老用户的既有听感（延迟变化可感知）] → 方向是变好（更跟手）；追求原表现的稳定档可完整找回（且余量略增）。

## Migration Plan

无数据迁移、无新权限、无新依赖。发布回滚 = 恢复固定 100ms 缓冲构造（`GameSession.start()` 单点），SharedPreferences 多出的键无害。

## Open Questions

（无——档位毫秒值允许在实施期依真机观察微调，不改变 spec 的档位语义、默认档与任务拆分。）
