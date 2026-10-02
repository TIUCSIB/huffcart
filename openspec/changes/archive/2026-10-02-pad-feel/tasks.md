# Tasks

## 1. 弹簧动画基建

- [x] 1.1 `PadControls.kt` 引入 compose animation 导入（`animateDpAsState` / `animateFloatAsState` / `spring`），清理重复的「横屏全屏浮层装配」注释行；皮肤面板在途弹簧实现（MediumBouncy + StiffnessMediumLow）沿用为统一参数

## 2. 皮肤面板（竖屏）

- [x] 2.1 `SkinSprite`：下沉 offset 弹簧化，叠加按住变暗蒙层并随弹簧淡入淡出
- [x] 2.2 `SkinPadPanel`：十字键精灵下沉弹簧化（6dp），同样叠加变暗蒙层
- [x] 2.3 行程调整：B/A `pressY` → 7dp，SELECT/START 5dp

## 3. 横屏 Canvas 浮层

- [x] 3.1 `RoundPadButton`：下沉 5dp 弹簧化（横屏浮层同样生效）；「按下提亮」改为 alpha 动画（0↔0.22）
- [x] 3.2 `DpadControl`：十字/箭头/高亮的绘制以弹簧分数动画整体下压（尺寸 × 3%），凹槽与底影固定
- [x] 3.3 `Pill`：新增 2dp 弹簧下压

## 5. 二轮：撕裂感修复（用户实测反馈）

- [x] 5.1 十字键改方向性按压：`pressedDpad: Boolean` → `pressedDir: RetroButton?`，十字朝按压方向偏移 4dp + 竖直下压 2dp（弹簧），配合半臂红色高亮
- [x] 5.2 SELECT/START 挖孔内下沉：`SkinSprite` 增加 `sinkInHole`，外层 `clipToBounds` 窗口固定在孔位，精灵在窗口内下沉（下缘被孔边界裁剪）
- [x] 5.3 变暗改 `ColorFilter.tint(黑, SrcAtop)` 染精灵像素（跟随形状，替代蒙层 Box；Multiply 会在透明背景上产生方形黑幕，已踩坑弃用），十字键与皮肤单键统一
- [x] 5.4 构建 + 单测通过；MuMu 截图验收：按「右」十字右偏、SELECT 下沉不越孔

## 4. 验证

- [x] 4.1 `cd X:/ && ./gradlew :app:assembleDebug :app:testDebugUnitTest` 通过
- [x] 4.2 MuMu（192.168.31.41:5555，注意 16384 为旧端口）安装启动，1942 游戏屏静置/按住 B/按住十字键各截一张：B 下沉 + 变暗可见，十字键整体压进凹槽且变暗；按 START 正常开局（输入链路无损）；`openspec validate pad-feel` 通过
