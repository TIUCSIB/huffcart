# Design

## Context

工作目录为全新空项目（greenfield），无存量代码。开发环境已核查就位：JDK 17（Temurin）、Android SDK（`D:\sdk`，platforms 34–36）、NDK 27、adb。产品方向与约束来自 grill 访谈共识（见 proposal），其中对本设计影响最大的是：产品体验优先、核心走嵌入路线（`:core-bridge` 最终将在后续 change 中通过 JNI 调 libretro FCEUmm）、混合复古 UI、minSdk 26。

## Goals / Non-Goals

**Goals:**

- 定形双模块结构：`:app`（Android UI 层）+ `:core-bridge`（纯 Kotlin 桥接层），后者是后续 libretro 接入的稳定接缝
- 落地混合复古主题系统（M3 + FC 配色 + 像素字体标题），一次做对，后续页面只消费不重构
- 单 Activity + Compose Navigation 的页面骨架，游戏页将来以全屏沉浸模式挂入
- 产物可安装真机，人工验收 spec 的四个场景

**Non-Goals:**

- 不编译 libretro 核心、不写 JNI 实绑（下一个 change：`libretro-playback`）
- 不做 ROM 扫描/导入的真实逻辑（空状态占位即可）
- 不做 KMP/多平台，不做浅色主题（深色 only，见 Risks）
- 不引入 DI 框架与数据库——现阶段没有值得动用它们的复杂度

## Decisions

1. **单 Activity + Compose Navigation**（而非多 Activity）：游戏页必须全屏沉浸 + 自主管理返回键（手柄映射），多 Activity 会让沉浸模式切换与转场变笨重；Compose 导航对底部双页 + 未来全屏游戏页的结构表达最直接。
2. **`:core-bridge` 定位为纯 Kotlin（JVM）模块**，不依赖 AndroidX/Android SDK：JNI 调用本身是纯 Java 能力。这让模拟桥接逻辑可以在桌面 JVM 上被直接实例化和调试——给将来"自研核心实验"彩蛋留的门，成本为零。Android 侧（SurfaceView、AudioTrack）留在 `:app`。
3. **主题用 Compose M3 自定义 `ColorScheme` + `res/font` 字体**：主色 #E60012、深底 #121212 系、米白 #F6F1E7 / FC 黄 #F7B500 点缀；像素字体仅用于标题层（`Typography.titleLarge` 以上），正文用系统 Roboto。备选的"全像素字体"方案因中文可读性差被否（grill 时已排除）。
4. **版本目录 `gradle/libs.versions.toml` 管理依赖 + Compose BOM 锁定**：避免 Compose 各库版本漂移；AGP 用与 Gradle 8.x 兼容的稳定线。
5. **targetSdk 36，直接按 edge-to-edge 设计**：Android 15 起强制 e2e，从骨架期就正确处理 WindowInsets（尤其底部导航避开手势条），避免后期返工。
6. **AGSL/CRT 等渲染能力不进本期**，但在游戏页容器规划中预留 `SurfaceView` 占位与特性检测点（`Build.VERSION.SDK_INT >= 33`），后续 change 直接填充。

## Risks / Trade-offs

- [项目路径含非 ASCII 字符（F:\自己写的项目\…），JVM 子进程参数经系统代码页转换后损坏] → AGP 路径检查用 `android.overridePathCheck=true` 关闭；Gradle 测试 worker 因此无法加载测试类，改用 JUnit Platform + 显式 jupiter 依赖后仍需 **从 ASCII 路径构建**：本机已 `subst H:` 映射项目（重启失效需重建，或改用可用的目录联接）。长期方案是项目迁移至纯 ASCII 路径
- [融合像素字体资源体积较大（7MB）] → 仅打包单一字号变体；当前 APK 11.5MB 可接受，超预期再做字符子集化
- [NDK 27 与后续 gradle 配置的 ABI 过滤细节] → 本期不涉及 NDK 构建，风险推迟到 `libretro-playback`；骨架中先固定 `arm64-v8a` 的 abiFilters 占位注释
- [深色 only 意味着系统浅色下也强制深色] → 刻意的产品选择（游戏画面更突出），spec 已按深色写死，不是遗漏
- [纯 Kotlin 模块在 AS 里默认模板不存在] → 手工配置 `java-library` + Kotlin 插件，构建脚本中注明用途

## Open Questions

- 无阻塞项。用户真机的具体安卓版本待下次插机时 `adb shell getprop` 确认，仅影响验收设备范围，不影响本设计的任何决策。
