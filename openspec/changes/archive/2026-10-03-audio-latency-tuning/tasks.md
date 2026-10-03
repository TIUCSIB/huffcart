# Tasks

## 1. 档位值对象与持久化（AudioSettings）

- [x] 1.1 在 `AudioSettings.kt` 新增 `AudioLatency` 三档枚举（LOW「低延迟」≈40ms / BALANCED「平衡」≈70ms / STABLE「稳定」≈120ms，携带缓冲毫秒值与中文标签）；`AudioSettings` 增加 `latencyTier` 字段（默认 `BALANCED`）；`AudioSettingsStore` 在现有 prefs 增加 `latency_tier` 键，读取 `runCatching { valueOf }` 回落默认。补 JVM 单测：默认档位为平衡、非法存储值回落平衡（audio-latency-tuning 决策 2/6）。验证：`gradlew :app:testDebugUnitTest --tests "*AudioSettingsTest*"` 全绿。（在 C:\huffcart-test ASCII 路径镜像上执行——F: 中文路径下 Gradle 测试 worker 类加载损坏，与本变更无关，见环境说明）

## 2. 游戏会话按档位构造音频（GameSession）

- [x] 2.1 `GameSession.start()` 的 AudioTrack 构造改为按档位取缓冲：字节数 = `tier.bufferMs × rate × 4` 且始终 ≥ `getMinBufferSize`（audio-latency-tuning 决策 3）；联机加入端（`netplay?.role` 为 P2–P4）强制按 `STABLE` 取值，房主与单机照常走用户档位（决策 5）；`LOW` 档追加 `setPerformanceMode(PERFORMANCE_MODE_LOW_LATENCY)`，其余档不设置（决策 4）。验证：`gradlew :app:assembleDebug` 编译通过，代码走查确认三处分支（单机/房主=档位、加入端=稳定）。
- [ ] 2.2 游戏循环既有 5s 节拍日志追加 `getUnderrunCount()` 计数（audio-latency-tuning 决策 8）。验证：真机运行游戏，logcat 的 `GameLoop` 节拍日志出现 underrun 计数字段。（代码已实装并随全量单测/构建通过；真机自动化点按在 MuMu 多屏实例上失效，未能观察到游戏运行日志——需人工启动一局游戏核对）

## 3. 声音设置页档位选择（AudioSettingsScreen）

- [x] 3.1 在「快进静音」之后新增「音频延迟」行：左标签 + 当前档位值，点按弹 `FcOptionDialog` 三档单选（低延迟 / 平衡 / 稳定，默认平衡），点选即时持久化；行样式与 note 文案对齐既有行，描述如实标注延迟与稳健的取舍（audio-latency-tuning 决策 7）。验证：进入声音设置页可见该行并可切换，杀掉应用重进页面档位保持。（MuMu 真机实测：行、三档弹窗、点选生效、进程重启后保持均通过）

## 4. 真机验收（对照 spec 场景）

- [ ] 4.1 单机三档验证：默认（未改过设置）为平衡档且操作到出声延迟低于既往、无持续爆音；低延迟档最跟手；稳定档在后台负载抖动下不断续；改档重进游戏生效、重启应用保持。验证：对照 `specs/game-playback/spec.md`「音频输出」场景逐条核对，underrun 日志无持续增长异常。
- [ ] 4.2 联机加入端验证：选择「低延迟」档后作为加入端进入联机对局，加入端音频按稳定缓冲、网络抖动下无持续爆音，对局节拍与同步行为不变。验证：对照 spec「联机加入端固定稳定缓冲」场景，双机联机实测。
