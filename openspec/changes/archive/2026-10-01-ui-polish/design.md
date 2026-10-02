# Design

## Context

对照设计稿与 MuMu 实机截图的四点差距：①底部导航选中态是默认的淡紫胶囊 + 图标标题不同色，背景色与米白主题冲突；②首页卡片把 FC 角标和 ⋮ 叠在封面上、标题孤行，与设计稿的"封面 / 标题行+⋮ / FC 标签"结构不符，且各页图片比例不一（首页 1:1、列表 56×42、详情 4:3）；③详情页 hero 中央的播放浮层与底部「开始游戏」功能重复，且素材 CTA 无按压反馈；④游戏屏的存/读/快进 chips 叠在画面右上角遮挡内容，设计稿要求顶部红 nav（吹卡带 logo + ⚙️），按键布局为"凹槽十字键 / B-A 横排 / SELECT-START 居中"。

## Goals / Non-Goals

**Goals:**

- 四点观感问题逐一对齐设计稿；图片比例全 app 统一为 FC 原生 256:240
- 存/读/快进功能零丢失：入口迁入游戏屏顶栏 ⚙️ 快捷菜单
- 不破坏已归档能力（save-states / fast-forward / game-library）的行为契约

**Non-Goals:**

- 不改三 tab 信息架构、分类/搜索/词典逻辑
- 不做真实游戏截图封面（仍确定性占位图）；不引入新依赖
- 不改存/读/快进的行为语义（仅入口位置）

## Decisions

1. **底部导航**：弃用 `NavigationBar` 默认配色（surfaceContainer 淡紫），自定义——背景白色、顶部 1dp 分隔线（outline 色）；选中项图标+文字均为 `HcRed`、未选中 `HcOnLightVariant`，不加胶囊底。用 `NavigationBar` + `NavigationBarItem.colors` 参数实现（无需自绘 Row）。

2. **图片比例统一 256:240**：新建常量 `CoverAspect = 256f / 240f`，首页卡片封面、游戏库列表缩略、详情 hero 三处共用。首页卡片结构改为：封面（clip 圆角）→ 标题行（名称 weight(1f) + ⋮ IconButton）→ FC 小字标签。FC 红色角标移除（FC 信息由下方标签行承载，与设计稿一致）。

3. **CTA 按压质感**：素材「开始游戏」是静态 PNG，无法换图——按下时叠加黑色 12% 蒙层 + 整体下沉 3dp（与手柄红钮同一手感语言），松开恢复。用 `pointerInput` 检测 press 状态驱动（不用 ripple，像素按钮配 ripple 出戏）。

4. **游戏屏顶栏与快捷菜单**：顶栏复用 `AppTopBar`（红底），左位放吹卡带素材 logo（Image，无文字标题），右侧 ⚙️ IconButton。⚙️ 点击展开 `DropdownMenu`：存档 / 读档 / 快进（1x→2x→3x 循环，菜单项显示当前倍率）/ 退出游戏。原 `SessionChips` 整体移除。功能与反馈逻辑（`GameSession.requestSaveState` 等）原样复用，仅触发入口变化——save-states / fast-forward spec 的"游戏屏 SHALL 提供"由菜单承载，无需修改那两个主 spec。
   备选（否决）：存/读/FF 挪进控制面板空隙——面板空间已被按键占满，且设计稿明确顶栏是唯一功能入口。

5. **控制面板布局**：`ControlPanel` 内部——`DpadControl` 保持 CenterStart（V2 凹槽+箭头已符合）；`AbButtons` 改横排（B 左 A 右、`Row` 底对齐、A 上移 6dp 保留一点错落），替换原 150dp 斜排盒；`MenuPills` 改 `Alignment.Center` + 并排居中、胶囊缩小（72×28dp）；面板底色加深至 `#1E1E1E`（设计稿面板近黑）。横屏浮层沿用同组件（translucent），布局参数同步。

6. **导航栏位置实现**：游戏屏目前是 NavHost 内的 route（bottomBar 仅在三个 tab 显示）——游戏屏顶部导航栏是屏内自绘（Column 首行放 AppTopBar 变体），不是全局 Scaffold 顶栏，返回手势仍由 `BackHandler` 处理。

## Risks / Trade-offs

- [DropdownMenu 弹出时游戏继续运行（音频/画面不停）] → 可接受：存/读在游戏线程帧末执行不受菜单影响；快进切换即时生效
- [图片比例改动影响占位封面图案密度] → CoverPattern 是 16×8 网格拉伸绘制，比例变化仅轻微拉伸纹理，无功能影响
- [顶栏占用竖屏高度，画面区变小] → 顶栏约 56dp，256:240 画面整数倍缩放自适应，可接受；与设计稿一致
- [横屏顶栏压缩画面高度] → 横屏顶栏保留（结构一致性优先）；如真机体验不佳，后续 change 可再裁

## Migration Plan

纯 `:app` UI 层修改，按"导航栏 → 卡片 → 详情页 → 游戏屏 → 面板"顺序小步提交；无数据迁移，回滚即 revert。

## Open Questions

（无——存/读/快进的去向已按"⚙️ 快捷菜单"假设定案并记录；若用户期望挂在别处，apply 前告知即可调整。）
