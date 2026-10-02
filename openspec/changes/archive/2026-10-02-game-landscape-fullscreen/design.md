# Design

## Context

游戏屏现状（`GameScreen.kt`）：竖屏分支 = GameTopBar + 画面区(weight) + GamepadPanel + FamilyComputerBanner；横屏分支 = GameTopBar + 画面区(weight) + PadControlsOverlay。渲染走 `GameSession.loop()`：整数倍缩放 letterbox 到 surface 实际尺寸（`GameScreen.kt:807`），surface 尺寸变化天然自适应；旋转经 manifest `configChanges` 处理不重建 Activity，`surfaceDestroyed` 与渲染循环有互斥锁（`surfaceMutex`），横竖屏切换时 ANR 防护已就位。沉浸只做了导航条（`ImmersiveBars.kt` 仅 hide `navigationBars()`）。

用户未答复探索阶段的方向选项，按推荐项执行（记录为决策 0）。

## Goals / Non-Goals

**Goals:**

- 横屏 = 全屏沉浸：无红顶栏、状态栏隐藏、画面区占满整屏
- 全屏下菜单可达：右上角半透明浮动齿轮，菜单项与竖屏一致，联机限制沿用
- 竖屏布局与行为零改动

**Non-Goals:**

- 不加横竖屏锁、不加手动「全屏」开关（跟随系统旋转即可）
- 不改渲染层（整数倍缩放、CRC 校验、音频节拍均不动）
- 不改 PadControls 浮层控件的形态与绘制规则（150dp、贴边逻辑照旧）；竖直位置按真机反馈调整（见决策 6）
- 不处理平板/折叠屏多窗口特殊布局

## Decisions

1. **触发方式：跟随系统旋转，不加开关**（用户未答复，取推荐项）。备选是「锁竖屏 + 手动全屏按钮」：多一个持久化状态位和一条 spec 路径，且用户原话「自动翻转后全屏的样式不太对」表明其心智模型就是旋转触发，修好横屏即满足。
2. **菜单入口：右上角半透明浮动齿轮**，浮在画面区顶部对齐状态栏让位处（padding 适配 notch），点开复用现有 `DropdownMenu`（存/读/快进/退出 + 联机中只剩退出的既有逻辑）。备选「SELECT/START 旁加菜单胶囊」要动 `PadControlsOverlay` 装配且与游戏画面贴得近，收益不抵改动。竖屏红顶栏菜单保持原位，两处菜单共用同一组回调。
3. **沉浸实现：参数化 `HideSystemNavigationBars`**（新增 `hideStatusBar: Boolean = false` 参数或独立 composable），横屏游戏屏传 true 同时隐藏状态栏；行为仍为 transient-by-swipe，离开游戏屏在 onDispose 恢复。竖屏只藏导航条，维持现状——避免竖屏顶栏 `statusBarsPadding()` 与隐藏状态栏互相打架。
4. **横屏布局：`Box(fillMaxSize)` 单层**——SurfaceView 占满，浮动齿轮（`Alignment.TopEnd`）、联机横幅（`TopStart`）、反馈气泡（`TopCenter`）作为 overlay 挂上。去掉现有横屏分支里冗余的 `Column + weight`。浮层 Y 锚点从「顶栏下方」改为「状态栏位置」：竖屏时 `FeedbackPill`/`NetplayBanner` 现有的 `padding(top = 32.dp)` 是给顶栏让位的，横屏无顶栏后保持同样 padding 即可（状态栏已隐藏，32dp 内边距天然安全）。
5. **旋转中布局切换**：现有 `isPortrait` 分支已按 `LocalConfiguration` 重组；SurfaceView 随布局销毁重建，`surfaceCallback` + `surfaceMutex` 已保证渲染线程安全。唯一注意点：横竖屏分支各自 `AndroidView` 的 `factory` 都挂同一个 `session.surfaceCallback`，无需额外处理（现状已如此）。
6. **浮层位置：两侧集群整体下移 70dp 并内收 60dp**（真机验收反馈追加）：原垂直居中在横向握持下偏上（RMX5060 实测 B 键圆心约 47% 屏高），`PadControlsOverlay` 两侧加 `offset(y = 70.dp)` 落到拇指热区（实测 67% 屏高）；贴边 20dp 太靠外，左右内边距增至 60dp 向画面靠拢（实测 B 82%→75% 屏宽、A 93%→86%）。SELECT/START 底部居中不动。备选「改为 Bottom 对齐 + 固定 bottom padding」在矮屏上会挤压 SELECT/START 区，弃用。

## Risks / Trade-offs

- [刘海屏横屏挖孔切进画面 pillarbox] → 整数倍缩放居中，画面两侧本就有黑边；浮动齿轮避开 `displayCutout`（用 `windowInsets` 的 displayCutout padding），实机（RMX5060）验收确认
- [全屏后下拉菜单弹出位置贴屏幕右缘] → `DropdownMenu` 由 Material3 自行避让边缘，横屏真机验收确认
- [状态栏隐藏/恢复与竖屏切换竞态] → 恢复逻辑在 `DisposableEffect.onDispose`，随重组方向切换必然触发；验收含「游戏中旋转来回切」场景
- [浮动齿轮与联机横幅同排视觉拥挤] → 齿轮 `TopEnd`、横幅 `TopStart`，两端分离；横幅文案过长时截断省略

## Migration Plan

纯 UI 层改动，无数据迁移、无 API 变化。回滚 = revert 布局层提交。

## Open Questions

（无）
