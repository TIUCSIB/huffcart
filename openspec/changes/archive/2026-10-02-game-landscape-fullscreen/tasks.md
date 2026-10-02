# Tasks

## 1. 沉浸能力扩展

- [x] 1.1 `ImmersiveBars.kt`：给 `HideSystemNavigationBars` 增加 `hideStatusBar: Boolean = false` 参数（true 时同时 hide `WindowInsetsCompat.Type.statusBars()`，behavior 保持 transient-by-swipe，onDispose 恢复两者）；竖屏调用方不传参，行为零变化。验证：竖屏游戏屏/启动页状态栏仍正常显示，编译通过
- [x] 1.2 横屏游戏屏调用处传 `hideStatusBar = true`。验证：横屏游戏屏状态栏与导航条均不可见，上滑临时呼出后自动收起

## 2. 横屏全屏布局重构

- [x] 2.1 `GameScreen.kt` 横屏分支去掉 `Column + GameTopBar` 结构，改为 `Box(fillMaxSize)`：SurfaceView 占满 + `PadControlsOverlay` + 联机横幅（TopStart）+ 反馈气泡（TopCenter）浮层，横屏分支不再渲染 `GameTopBar`。验证：横屏无红顶栏、画面整数倍缩放居中占满整屏，控制浮层可用
- [x] 2.2 新增横屏浮动菜单齿轮：右上角半透明圆形按钮（TopEnd，`displayCutout` 避让），点开复用现有 `DropdownMenu`（存/读/快进/退出 + `netplayActive` 只剩退出的既有逻辑），与竖屏菜单共用同一组回调。验证：横屏点齿轮可存档/读档/快进/退出；联机对局中菜单仅剩「退出游戏」
- [x] 2.3 联机横幅与反馈气泡锚点适配无顶栏画面区（保持 32dp 顶部内边距，横幅文案过长截断省略）。验证：单机与联机两种状态下横屏两浮层文案完整可读、不与齿轮重叠
- [x] 2.4 （真机反馈追加）横屏浮层方向控制与 A/B 集群整体下移 70dp 并内收 60dp（`PadControlsOverlay` 两侧 `offset(y = 70.dp)` + `padding(start/end = 60.dp)`），从垂直居中贴边落到拇指热区（RMX5060 实测 B 键圆心 67% 屏高 / 75% 屏宽，改前 47% / 82%；A 86% 屏宽，改前 93%）；SELECT/START 底部位置不变。验证：真机横屏截图确认、与底部胶囊无重叠

## 3. 验收回归

- [x] 3.1 竖屏回归：红顶栏、画面区、控制面板、FAMILY COMPUTER 横幅、顶栏菜单（含联机限制）与改前一致。验证：竖屏逐项对照（MuMu + AVD Medium_Phone uiautomator dump 实测：logo/齿轮/面板/横幅/菜单四项全在，替代计划中的真机对照）
- [x] 3.2 游戏中旋转来回切（竖屏 ↔ 横屏各两次）：画面推进不中断、手柄在新形态可用、无崩溃无黑屏残留（覆盖 spec「游戏中旋转切换」场景）。验证：AVD 实测 5 次旋转全对（GameLoop 同线程存活 fps 持续输出）+ crash 缓冲区 0 条（替代计划中的真机实测）
- [x] 3.3 刘海屏真机横屏验收：画面 pillarbox 黑边不遮挡、齿轮避开挖孔区。验证：RMX5060 横屏截图确认（齿轮 bounds [2256,48][2328,120] 完整可见，画面整数倍居中）
