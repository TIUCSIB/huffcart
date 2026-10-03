# Proposal

## Why

游戏音频输出缓冲当前固定 ≈100ms（`rate / 10 * 4` 字节），而 AudioTrack 阻塞写会把缓冲填满后才逐帧消耗——按帧节拍（audio-settings 决策 1）意味着缓冲深度直接决定「按键到出声」的端到端延迟。真机上约百毫秒的声画滞后是操作手感「发闷、不跟手」的主要来源，也是模拟器最直接可感知的体验项之一。

## What Changes

- **声音设置页新增「音频延迟」三档选择**（复用画面比例的 FC 单选弹窗交互）：
  - **低延迟**（缓冲 ≈40ms，约 2.4 帧）：操作到出声最跟手；低端设备或后台负载高时偶发断续（爆音）风险最高；
  - **平衡**（缓冲 ≈70ms，默认）：延迟较现状约减半，日常负载下稳健；
  - **稳定**（缓冲 ≈120ms）：最不易因负载抖动出现断续，适合老旧设备。
- 默认档位取**平衡**——本次变更的目的即降低延迟，既往表现（≈100ms）仍可通过「稳定」档找回；三档毫秒值为初值，实施期以真机 underrun 观察微调。
- **低延迟**档对 AudioTrack 请求 `PERFORMANCE_MODE_LOW_LATENCY`（minSdk 26，可用）；其余档位不请求。
- **联机加入端例外**：加入端音频固定按「稳定」档缓冲（非阻塞写、写满即丢——缓冲小了网络抖动会直接变成爆音；加入端音频本就带网络延迟，稳健性优先于延迟）。
- 游戏循环既有的 5s 节拍日志增加 underrun 计数，便于真机验收对比各档表现。
- 档位选择持久化，并在再次进入游戏屏时生效（沿用声音/画面设置「改完重进游戏即生效」的既有约定）。

## Capabilities

### New Capabilities

（无——均为既有能力的 requirement 变更，不新建能力。）

### Modified Capabilities

- `game-playback`：「音频输出」requirement 新增音频延迟三档 SHALL（三档含义、默认平衡、进游戏生效、联机加入端固定稳定档、低延迟档低延迟通路）。
- `app-shell`：「设置页结构」requirement 的声音设置页枚举增加「音频延迟」三档选择；「声音设置入口」scenario 的可见项同步。

## Impact

- **代码**：
  - `ui/game/AudioSettings.kt`：新增 `AudioLatency` 三档枚举（含目标缓冲毫秒值）、`AudioSettings` 增加档位字段、`AudioSettingsStore` 增加持久化键（缺省回落平衡档，向后兼容）；`AudioSettingsTest` 增补用例。
  - `ui/game/GameSession.kt`：AudioTrack 构造按档位计算缓冲字节（始终 ≥ `getMinBufferSize` 下限），低延迟档设置 performance mode，联机加入端强制稳定档；5s 节拍日志追加 underrun 计数。
  - `ui/screens/AudioSettingsScreen.kt`：新增「音频延迟」选择行 + `FcOptionDialog` 三档弹窗。
- **音频**：仅宿主侧 AudioTrack 缓冲与通路参数；核心（FCEUmm）每帧产样、采样率对齐逻辑不变。
- **联机**：不改动 netplay 协议、节拍与输入路径；仅加入端 AudioTrack 缓冲取值受会话角色影响。
- 无新权限、无新依赖、无数据迁移。
