# Design

## Context

- 仓库现状:291 个入库文件;62 个 Kotlin 主源码文件(约 1.2 万行),native 侧为 `core-native/src/main/c/shim.c` + `LibretroCore.kt`(JNI 边界),核心 .so 由 `scripts/build-core.sh` 从 libretro 上游按版本锁构建(fceumm/gambatte),内置 ROM 经 `scripts/sync-bundled-roms.sh` 构建期注入且不入 git。
- 已核对的安全基线:`.gitignore` 已排除 `keystore.properties`、`*.jks`、`local.properties`、`assets/roms/`、`jniLibs/`;入库文件中无敏感项(仅 `keystore.properties.example`)。
- 发布现状:无 git remote、无 tag、无 CI、无 README;GitHub 上不存在 `TIUCSIB/huffcart`;`gh` 已登录 TIUCSIB(repo scope)。当前 `versionName 0.1.0 / versionCode 1`。
- 环境约束:`build-core.sh` 要求在 ASCII 路径(如映射盘 H:)下运行;无 CI,发布为本地构建 + gh CLI。
- 动机见 proposal.md 的 Why;行为要求见三份 spec delta。

## Goals / Non-Goals

**Goals:**
- 一套可复核的审计方法:按五维、按热点文件定点检查,结论落盘为审计报告
- P0/P1 修复以小步提交、逐项可验证、不回归为前提
- 一次可复现的首发发布流程:建仓 → 推送 → tag v0.1.0 → Release(干净构建 APK + changelog)
- README 达到可公开展示质量(原生 SVG 资产 + 准确内容)

**Non-Goals:**
- 不引入 CI/CD 流水线(留给后续变更)
- 不做架构级重构、不升级大型依赖
- 不分发内置 ROM,不处理任何 ROM 授权问题
- 不搭建自动化性能基准设施(以手动量化记录为准)

## Decisions

1. **审计方法 = 静态走查 + 定点运行时验证,按热点排序。** 优先热点文件:`GameSession.kt`、`GameScreen.kt`、`PadControls.kt`、`netplay/NetplayManager.kt`、`CoverStore.kt`、`shim.c`/`LibretroCore.kt`。静态查泄漏模式(未反注册的 listener/回调、线程/Socket/流未关闭、Bitmap/ByteBuffer/JNI 全局引用滞留)、ANR 模式(主线程 IO、锁等待、死等网络)、安全模式(网络报文长度/来源校验、Zip 解压路径穿越、文件权限)。运行时验证用真机冒烟 + logcat + 内存曲线记录,不引入重型 APM。
   备选:全量接入 LeakCanary → 采用折中:**仅在 debug 实现可选接入 LeakCanary 辅助泄漏确认,不进 release**;若接入受阻则回退手动 Profiler 流程。
2. **修复策略:小步、可验证、不改行为。** 每个 P0/P1 修复独立提交并附验证记录;P2+ 只进报告 backlog。冗余治理限于死代码删除与明显重复合并,不借机重构。
3. **发布物策略:公开 APK 为"干净构建"。** 发布用 APK 不执行 `sync-bundled-roms.sh`(assets/roms 为空),上传前解包检查确认无 .nes/.gb/.gbc;内置 ROM 版 APK 仅本地留存。
   备选:发布含 ROM 的 APK → 版权风险,否决;仓库设 private → 与 README 公开展示意图冲突,不采用(见 Open Questions)。
4. **发布流程全部走 gh CLI**:`gh repo create TIUCSIB/huffcart --public` → 加 remote → push main → `git tag v0.1.0` → `gh release create v0.1.0` 附 APK 与中文 changelog(changelog 从 git log 按特性归纳)。发布前输出入库文件复核清单。
5. **README 用 beautify-github-readme 技能产出**,资产为项目原生 SVG(横幅/徽章/特性版式)入库引用;真机截图受环境限制,先以 SVG 版式承载,截图位留待补充,不阻塞发布。仓库当前无 LICENSE,README 只写免责声明(ROM 用户自备、项目不含/不分发 ROM)。
6. **关机为最终人工确认步骤。** 全部验证完成后向用户确认,再执行 `shutdown /s /t 60`(可用 `shutdown /a` 取消);未经用户明确确认,不得自动关机。

## Risks / Trade-offs

- [真机性能/稳定性验证依赖设备在手] → 真机冒烟与 30 分钟长测列为发布前硬性门槛;设备不可用时停止在验证步,向用户说明,不发布。
- [核心编译需 ASCII 路径映射] → 沿用 `build-core.sh` 约定,任务中显式包含路径前置检查。
- [公开仓库后历史含敏感文件的风险] → 发布前用入库文件清单复核(.gitignore 已覆盖);若发现先清理再发布。
- [LeakCanary 新依赖] → 仅 debug 实现,release 不受影响;失败则回退手动流程。
- [README 截图资产依赖设备] → 无截图时以 SVG 版式先行,不阻塞发布。
- [推送与发布是不可逆外发动作] → 发布步骤集中为一个任务组,执行前先展示复核清单;一旦公开即视为已发布,回滚手段(删 Release/tag、转 private)仅作补救。

## Migration Plan

不适用(无数据/接口迁移)。执行顺序:审计 → 修复 → 干净构建 → 建仓推送 → tag → Release → README → 复核 → 关机确认。回滚:Release/tag 可删除重建,仓库可转 private。

## Open Questions

- 是否添加开源 LICENSE、选哪种——需用户决定,不阻塞发布(README 先写免责声明)。
- 首发仓库可见性(默认 public)——如需 private 请在评审时提出。
