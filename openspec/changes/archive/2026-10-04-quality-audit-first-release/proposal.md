# Proposal

## Why

HuffCart(Android libretro 前端,支持 FC/GB/GBC)功能面已铺开(游玩、联机、金手指、倒带、物理手柄、音频延迟档位),但从未做过系统性质量审计,也没有任何对外发布痕迹:仓库无 remote、无 tag、无 README。在首次公开发布之前,需要对性能(卡顿/掉帧)、稳定性(死机/ANR)、内存泄漏、代码冗余与安全做一次全面检测并修复发现的问题,保证首发版本拿得出手。

## What Changes

- **全项目质量审计**:覆盖五个维度——性能(帧率/掉帧、帧间间隔抖动、音频欠载)、稳定性(ANR、崩溃、死机风险点)、内存泄漏(倒带环形缓冲、GameSession 生命周期、封面缓存、网络资源)、代码冗余(重复逻辑、死代码)、安全(网络报文校验、zip 解压路径穿越、文件权限、JNI 边界)。产出审计报告并落盘。
- **修复审计发现的 P0/P1 问题**(崩溃、泄漏、明显卡顿、安全漏洞);P2 及以下记录为 backlog,不在本变更内做高风险大重构。
- **GitHub 首发发布**:创建远端仓库(gh CLI 已登录 TIUCSIB)、推送 main、打 tag `v0.1.0`、创建 GitHub Release 附 changelog 与已签名 release APK。
- **公开分发物不含内置 ROM**:发布到 Release 的 APK 为"干净构建"(不执行 `sync-bundled-roms.sh`);内置 ROM 仅本地构建使用,避免公开分发版权 ROM。
- **README 从零创建并美化**:按 `beautify-github-readme` 技能产出项目原生 SVG 资产、徽章与完整文档(特性/构建/安装/说明)。
- **收尾关机**:全部步骤验证完成后,经用户最终确认执行关机(人工步骤,不由代理自动执行)。

**关键假设**(可评审时推翻):首发 tag 取 `v0.1.0` 与当前 versionName 一致;仓库可见性默认 public(README 美化面向公开展示);发布 APK 不带内置 ROM,ROM 版 APK 仅本地留存。

## Capabilities

### New Capabilities

- `quality-audit`: 质量审计与加固——五大审计维度的覆盖要求、审计报告落盘要求、P0/P1 修复与回归验证要求、发布前性能/稳定性门槛。
- `project-readme`: 项目 README 文档——仓库根 README 的内容结构、美化资产(SVG/徽章/截图)与准确性要求。

### Modified Capabilities

- `release-packaging`: 新增两条 requirement——"GitHub 首发发布"(远端、tag、Release 与产物)与"公开分发产物无版权内容"(公开 APK 不含内置 ROM);原有云备份排除/设备迁移/release 产物可用/发布签名要求不变。

## Impact

- **代码**: `app/src/main/java/com/huffcart/app/`(62 个 Kotlin 文件,重点 `GameSession.kt`、`PadControls.kt`、`GameScreen.kt`、`netplay/NetplayManager.kt`)、`core-bridge/`、`core-native/`(`shim.c`、`LibretroCore.kt` 的 JNI 边界)。修复以小步、低风险为主。
- **构建/发布**: `scripts/`(build-core.sh、sync-bundled-roms.sh)、`app/build.gradle.kts`(版本号)、`.gitignore`;新增 git remote 与 GitHub 仓库 `TIUCSIB/huffcart`。
- **文档**: 新增 `README.md` 与 README 资产目录;新增审计报告文档。
- **无 API 或依赖层面的 breaking 变更**;现有 spec 行为(游玩、存档、联机等)不得回归。
