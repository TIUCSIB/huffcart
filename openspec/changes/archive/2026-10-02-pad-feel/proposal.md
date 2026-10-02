# Proposal

## Why

素材手柄皮肤（SkinPadPanel）已实装，但按压反馈仍是"瞬移"：下沉与弹回没有动画过程，行程仅 3-4dp 不够可见，用户实机体验反馈"没有那种真实的感觉、没有起伏"。按压的运动感是虚拟手柄手感的核心，需要一轮专门的迭代。

## What Changes

- **弹簧回弹**：所有可按部件（皮肤面板的十字键 / B / A / SELECT / START，横屏浮层的十字键 / A/B / 胶囊）的下沉与复位改用弹簧动画（低阻尼 spring，松开带轻微回弹过冲）——按下压入、松手弹回并过冲一点点再落定，这是"手感"的核心缺口。
- **加大行程**：下沉行程从 3-4dp 提高到肉眼可辨：皮肤面板 A/B 7dp、十字键 6dp、SELECT/START 5dp；横屏浮层 A/B 5dp、十字键整体下压约 3% 尺寸、胶囊 2dp。
- **按下变暗**：皮肤面板按键按住期间叠加约 12% 黑色蒙层（alpha 动画淡入淡出），增强"按住"状态感知；横屏浮层保留按下红色高亮，另加弹簧下压。

**范围裁剪**：不做按键音（SoundPool，可选增强，用户未要求）；不改输入映射、命中区几何、触觉反馈逻辑；不触碰皮肤素材与切片脚本；不改横竖屏布局结构。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `game-playback`: 「虚拟手柄输入」——在既有布局与映射要求之上，新增按压运动反馈：下沉弹簧动画与松开回弹、可见的行程下限、按住变暗蒙层（皮肤面板）。

## Impact

- `:app` UI 层：仅 `app/src/main/java/com/huffcart/app/ui/game/PadControls.kt`（SkinSprite、SkinPadPanel 十字键、RoundPadButton、DpadControl、Pill 增加弹簧动画与变暗蒙层）
- 无新依赖（compose animation 随 foundation 已引入）；不触碰 `:core-bridge` / `:core-native`、皮肤素材、netplay 在途代码
