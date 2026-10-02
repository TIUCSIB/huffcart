# Proposal

## Why

虚拟手柄目前只有十字键一种方向控制形态。部分玩家更习惯现代手柄的推杆操作，需要提供模拟摇杆形态供选择；而十字键仍是熟悉 FC 的主流操作方式，应保留为默认。NES 核心输入为 8 向数字键（无模拟轴），摇杆以「推杆角度 → 方向键组合」映射实现，无需触碰核心。

## What Changes

- **新增虚拟摇杆控件（代码绘制）**：`JoystickControl`——Canvas 绘制底座 + 摇杆帽，配色跟随现有皮肤（深灰凹槽 + 红色方向高亮）；推杆偏移按角度映射为方向键组合（支持斜向，最多同时两个方向），带死区与迟滞防抖；松手弹簧回中；触觉反馈与现有按键一致。
- **控制形态可切换**：按键设置页新增「虚拟手柄样式」选择（十字键 / 摇杆），SharedPreferences 持久化，默认十字键；游戏屏进入时读取该设置。
- **竖屏与横屏都遵循该设置**：选摇杆时，竖屏皮肤面板在十字键挖孔区域呈现摇杆（底座遮蔽底图挖孔，其余按键精灵不动），横屏浮层在画面左侧原十字键位置呈现半透明摇杆。
- **范围裁剪**：不做模拟量输出（核心无模拟轴，摇杆仍映射为数字方向键）；不做摇杆灵敏度 / 死区自定义；不改 A/B/SELECT/START 呈现与映射；不触碰皮肤素材与切片；不在游戏内加切换入口（设置页只能从主界面到达）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `game-playback`: 「虚拟手柄输入」——在既有十字键形态与弹簧手感要求之上，新增摇杆形态（视觉、方向映射含死区与迟滞、松手回中）与控制形态的设置切换及持久化；增量文本将合并在途 change `pad-feel` 对该 requirement 的修改，形成合并后的最终文本。

## Impact

- `:app` UI 层：`app/src/main/java/com/huffcart/app/ui/game/PadControls.kt`（新增 `JoystickControl` 与摇杆方向判定纯函数；`SkinPadPanel` / `PadControlsOverlay` 按形态切换）、`app/src/main/java/com/huffcart/app/ui/screens/KeyMappingScreen.kt`（顶部样式选择行）、新增 `ControlSchemeStore`（SharedPreferences，沿用 `KeyMappingStore` 的轻量约定）
- 无新依赖；不触碰 `:core-bridge` / `:core-native`、皮肤素材、netplay 在途代码
