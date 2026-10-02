# Design

## 上下文

`PadControls.kt` 现状：皮肤面板（`SkinPadPanel` + `SkinSprite`）与横屏 Canvas 组件（`DpadControl` / `RoundPadButton` / `Pill`）的按压下沉都是 `if (pressed) Xdp else 0.dp` 的瞬时切换；横屏已有红色高亮 / 提亮，皮肤面板无按住状态视觉（仅位移）。

## 决策

1. **弹簧参数统一**：实装时发现皮肤面板已有一版在途弹簧实现（`DampingRatioMediumBouncy` + `StiffnessMediumLow`，更软、回弹更明显），本 change 沿用该参数作为全局统一规格；动画以组件内联 `animateDpAsState` / `animateFloatAsState` 表达（各组件目标值不同，抽公共助手反而增加间接层）。低阻尼保证松开回弹过冲，`StiffnessMediumLow` 保证按下压入干脆且可见。
2. **行程取值**（spec 下限之上留一点余量）：皮肤 A/B `4→7dp`、十字键 `3→6dp`、SELECT/START `3→5dp`；横屏 A/B `3→5dp`、胶囊新增 `2dp`、Canvas 十字键以绘制平移实现（下压量 = 尺寸 × 3%，随弹簧分数动画驱动，凹槽与底影不动、十字/箭头/高亮随之下压，形成"压进凹槽"的层次）。
3. **变暗蒙层**：皮肤精灵在素材 `Image` 之上叠加 `Box` 黑色蒙层，alpha 由 `pressDim` 驱动（按住 ≈0.12，松开回 0，动画过渡避免闪烁）；十字键精灵同样适用。横屏 Canvas 组件不加蒙层（红色高亮已是 spec 要求的按住指示），仅把"按下提亮"的 0/0.22 切换改为 alpha 动画。
4. **触觉与输入逻辑零改动**：`HitArea` / `pointerInput` 手势块、滑动换向、`onButton` 时机全部保持原样——只动视觉层，输入行为不受动画影响（按下瞬间即生效，不等动画）。
5. **不新增依赖**：`animateDpAsState` / `animateFloatAsState` / `spring` 来自 `androidx.compose.animation.core`，随现有 compose 依赖可用。
6. **二轮（撕裂感修复）**：用户实测反馈"按方向键任何方向都往下坠""SELECT/START 撕裂"。两处都不需要新素材：
   - **十字键素材只有一张整体精灵，做不了单臂独立下沉**（素材限制）；用"十字朝按压方向偏移 4dp + 竖直下压 2dp + 既有半臂红色高亮"近似真实十字键的跷跷板倾斜，这是单图 skin 的业界常规做法。若日后要真·单臂下沉，可用 `_skin_slice.py` 把十字源图切成 4 臂 + 中心再做独立位移。
   - **SELECT/START 撕裂根因**是精灵整体下沉时下缘越出底板挖孔、盖在面板表面上。修法：精灵外层固定为"挖孔窗口"（`clipToBounds`），下沉发生在窗口内部——上缘露出孔内暗部、下缘被孔边界裁掉，物理上即"按键缩进槽里"。A/B 圆钮观感已被用户接受，保持整体下沉不变。
   - **变暗用 `ColorFilter.tint(黑, SrcAtop)`** 直接染精灵像素：跟随精灵 alpha 形状，替代矩形/圆形蒙层 Box（蒙层在精灵位移时会露边）。**勿用 Multiply**——其 alpha 合成 `out.a = sa + da − sa·da` 会对精灵的透明背景像素输出 ≈sa 的半透明黑，按下时整个精灵矩形显形为"方形边框"（用户实测踩坑）；SrcAtop 只染不透明像素，透明区域保持透明。

## 风险

- 弹簧过冲会让 offset 短暂越过 0（负值），`offset(y = ...)` 接受负 dp，视觉即"回弹过头再落定"，符合预期；Canvas 分数动画同理（分数可 <0 或 >1，乘以 3% 后过冲量 <1% 尺寸，可忽略）。
- 快速连打时动画频繁反向：spring 天然可中断重定向，无需处理。
