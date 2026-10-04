# Spec Delta

## Purpose

约束仓库根 README 的内容与呈现:让首次到访者能快速理解 HuffCart 是什么、支持什么、如何构建与安装,并以项目原生 SVG 资产呈现统一视觉;内容必须与实际行为一致,不误导、不夸大。

## ADDED Requirements

### Requirement: README 内容完整准确
仓库根 SHALL 存在 README.md,SHALL 说明项目定位、平台支持(FC/GB/GBC)、核心特性列表、本地构建步骤(含 libretro 核心编译脚本与 ASCII 路径前提)、安装与使用方式,以及免责声明(应用不分发 ROM,游戏文件须用户自备)。README 内容 SHALL 与实际行为一致,SHALL 不宣传未实现的特性。

#### Scenario: 按 README 可完成构建
- **WHEN** 新访客按 README 的构建步骤在满足前提的环境上操作
- **THEN** 可完成核心编译与 APK 构建,步骤无缺失、无失效命令

#### Scenario: 特性描述真实
- **WHEN** 逐项核对 README 列出的特性
- **THEN** 每项特性在应用中真实存在并可操作,无夸大或未实现项

### Requirement: README 视觉美化
README SHALL 按 beautify-github-readme 技能要求进行美化:使用项目原生 SVG 资产(标志/横幅/徽章)与清晰的版式层次;图像资产 SHALL 以仓库文件形式入库并在 README 中相对引用,SHALL 不依赖外部图床;引用的图像 SHALL 无死链。

#### Scenario: GitHub 首页呈现
- **WHEN** 在 GitHub 上渲染仓库首页
- **THEN** README 呈现横幅、徽章与特性版式,所有图像正常加载,无死链、无外部图床引用
