# Proposal

## Why

项目目标已通过 grill 访谈定案：做一个**自用为主**的 Android FC（NES）模拟器 app，产品体验优先，模拟核心采用嵌入 libretro FCEUmm（Kotlin JNI 调用）而非自研。当前工作目录为空，一切后续功能（核心接入、游戏库、存档、手柄）都必须先有一块地基——一个模块划分正确、主题与导航定型的工程骨架。

## What Changes

- 新建 Kotlin + Jetpack Compose 多模块 Android 工程：`:app`（UI 与应用逻辑）、`:core-bridge`（纯 Kotlin/JNI 桥接层，本期仅建骨架与接口占位，不含真实核心）
- 落地"现代骨架 + 复古点缀"混合主题：Material 3、深色默认、任天堂红主色（#E60012）、标题像素字体（Press Start 2P + 融合像素中文）
- 底部导航双页：**游戏库**（空状态页，本期为占位）、**设置**（占位页）
- 产物要求：APK 可安装到真机并正常启动、导航可切换、主题生效
- 确定技术基线：minSdk 26 / targetSdk 36、JDK 17、AGP 8.x、Compose BOM

**不包含**（属后续 change）：libretro 核心编译与 JNI 实绑、游戏可玩屏、ROM 扫描与封面墙、存档、快进、手柄。

## Capabilities

### New Capabilities

- `app-shell`: 应用外壳——进程启动、底部导航结构（游戏库/设置）、全局主题（深色 + FC 配色 + 像素字体标题）、游戏库空状态展示。这是后续所有能力的宿主。

### Modified Capabilities

（无——全新工程，尚无既有 spec。）

## Impact

- 全新代码库，无存量系统受影响
- 新增依赖：AndroidX Compose 全家桶、Navigation Compose、像素字体资源（Press Start 2P 为 OFL 许可、融合像素字体为开源可商用）
- `:core-bridge` 的模块边界与 `RetroCore` 接口签名是后续 `libretro-playback` change 的契约，本期定形后应保持稳定
- 构建环境依赖已核查：JDK 17（Temurin）、Android SDK（D:\sdk，platform 34–36）、NDK 27 均已就位
