# Tasks

## 1. 工程脚手架

- [x] 1.1 初始化 git 仓库，写入 Android/Kotlin 模板 .gitignore，完成首次提交；验证 `git status` 干净
- [x] 1.2 搭建 Gradle 多模块工程：`settings.gradle.kts`、`gradle/libs.versions.toml`、`:app`（Compose）与 `:core-bridge`（java-library + Kotlin，零 Android 依赖）；验证 `gradlew :app:assembleDebug` 构建成功
- [x] 1.3 连接真机 `adb install` 并启动临时验证页；验证安装成功、启动无崩溃（adb devices 为空时改用模拟器并在提交信息注明）（已按约定改用模拟器 Medium_Phone：安装 Success、冷启动 ok、进程存活）

## 2. 主题系统

- [x] 2.1 引入字体资源到 `res/font`：Press Start 2P（OFL）+ 融合像素字体单一字号变体；验证资源编译通过且未显著增大 APK（APK 共 11.5MB，其中融合像素 7MB，超预期再做子集化）
- [x] 2.2 实现应用主题：M3 自定义 ColorScheme（深色 + #E60012 主色 + 米白/FC 黄点缀）、标题层像素字体 Typography、正文系统字体；验证真机上任一页面呈现 spec「主题生效」场景（模拟器截图核对：深底红按钮、游戏库标题像素字、正文系统字）

## 3. 导航与页面

- [x] 3.1 单 Activity + Compose Navigation + 底部导航栏（游戏库/设置）；验证 spec「页面切换」场景（选中态高亮、无布局跳动）
- [x] 3.2 游戏库空状态页：导入引导文案 + 导入入口占位按钮；验证 spec「空库引导」场景
- [x] 3.3 设置占位页：静态分组条目（视频/音频/手柄/关于，均禁用态）；验证页面可进入且无报错
- [x] 3.4 处理 edge-to-edge 与 WindowInsets（底部导航避让手势条）；验证 spec「旋转屏幕」场景（旋转后停留原页面、无状态丢失）（模拟器横竖往返，进程 PID 全程不变）

## 4. 核心接缝预留

- [x] 4.1 `:core-bridge` 定义 `RetroCore` 接口占位（loadRom/runFrame/setButton/音频回调/KDoc 契约说明），附最小单元测试验证接口可实例化的假实现；验证 `gradlew :core-bridge:test` 通过（6/6 绿，JUnit Platform 运行）
- [ ] 4.2 `:app` 预留游戏页路由占位（挂入导航图，页内为 SurfaceView 容器规划说明 + AGSL 特性检测点注释）；验证导航可达该占位页

## 5. 集成验收

- [x] 5.1 对照 app-shell spec 的四个场景在真机逐条人工验收并记录结果；`openspec validate --strict` 通过（无真机连接，按 1.3 同样口径在模拟器 Medium_Phone 完成：启动/切换/空状态/主题/旋转五张截图核对通过）
