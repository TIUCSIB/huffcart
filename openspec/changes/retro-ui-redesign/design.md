# Design

## Context

`:app` 已是单 Activity + Compose + NavHost（字符串路由），主题为强制深色（`Theme.kt` 注释明确 dark-only 是当时的刻意选择），像素字体资源（Press Start 2P + 融合像素）已就位。游戏屏 `GameScreen` 为全屏 SurfaceView + 全屏 Canvas 浮层手柄（百分比坐标 hitTest），存/读/快进 chips 在右上角（在途 change `save-states-fastforward` 的实现，已出现在工作区）。ROM 数据源是 `filesDir/roms` 目录扫描，无任何元数据来源（无封面、无分类、无简介）。

设计稿（用户 GPT 生成）确定的视觉：米白浅底 + 红顶栏 + 像素字标题、三 tab 导航、封面网格库、详情页、竖屏分区游戏屏（上画面/下深色控制面板）、分组设置页、按键设置页。

## Goals / Non-Goals

**Goals:**

- 一个主题层翻转（浅色方案）覆盖全部页面；游戏屏内独立保持黑底
- 所有装饰性视觉（首页插画、占位封面、控制面板）纯 Compose 代码绘制，零位图资源、零网络
- 键盘映射闭环：设置页编辑 → 持久化 → GameScreen 生效

**Non-Goals:**

- 不动 `:core-bridge` / `:core-native` / FCEUmm 构建链；不引入任何新第三方依赖
- 不做真实封面/简介抓取、不做列表-网格双视图、不做专设分类页（chip 就地过滤）
- 不实现画面设置/音频设置/存档管理的功能页（占位行）；存/读/快进 chips 的逻辑与位置不动（归 `save-states-fastforward`）

## Decisions

1. **浅色方案与取色纪律**：`Theme.kt` 换 `lightColorScheme`（背景米白 `#F6F1E7`、surface 白、primary 红 `#E60012`）；顶栏用红底白字的 `TopAppBar`。现有代码里散落的硬编码色值（`GameScreen` 的 `Color(0xFFE60012)` 等）统一改从 `MaterialTheme` 取色，仅游戏屏黑底例外（其内容是游戏画面，浅色主题不适用）。推翻原 dark-only 决策，更新相应注释。

2. **导航：延续字符串路由**：在现有 `NavHost` 上加 `home`、`detail/{rom}`、`keymapping` 三条路由与「首页」tab，bottom bar 隐藏逻辑（`currentRoute in topDestinations`）天然支持二级页。不上 type-safe Navigation API——现网字符串路由工作正常，本次改动应聚焦视觉而非迁移。

3. **竖屏游戏屏分区**：竖屏改 `Column`：上区 `SurfaceView`（权重 1）、下区固定高度控制面板（约 260-280dp）。`GameSession` 渲染循环的整数倍缩放/letterbox 以 canvas 尺寸计算，canvas 变成上区尺寸后**该逻辑无需改动**——分区是纯布局层变化。

4. **手柄控件重构为组件化 `PadControls`**：弃用全屏 Canvas 自绘 + 百分比 hitTest（按住方向斜角误触、代码难读），改为标准 Compose 组件（每个按键独立 `pointerInput` 命中区域），一套组件两处装配：竖屏放深色圆角面板内（不透明、不叠画面），横屏作半透明浮层（保持现要求）。按下高亮用状态驱动的红色（`HcRed`），替换现有红色 alpha 底。

5. **封面与分类/简介词典**：占位封面 = `kotlin.random.Random(hash(name))` 种子生成的对称像素块图案（8×8 半块镜像到 16×8）+ 从固定复古调色板取色，`Canvas` 绘制——确定性即"同一游戏同一封面"。分类与简介用一个内置 `Map`（常见 FC 游戏约 20 款，键为规范化文件名：小写、去空格与常见后缀），未命中归「全部」/显示文件信息。不建数据库：元数据的意义只是让 UI 有内容可摆，词典是成本最低且可随时替换数据源的方案。

6. **按键映射存储与输入**：`SharedPreferences`（避免为几个 Int 引入 DataStore 依赖）存 `虚拟键 → keyCode(Int)` 的简单格式，解析失败回默认；默认映射按设计稿：上/下/左/右→方向键、A→Z、B→X。GameScreen 在根 `Box` 上加 `onPreviewKeyEvent`：keydown（忽略 repeat）置位、keyup 复位，与触控层各自独立写 `setButton`，互不干扰。

7. **首页品牌页装饰**：FC 手柄插画 + 砖块条纹用 `Canvas` 矢量绘制（圆角矩形 + 十字 + 圆钮），标语用现有融合像素字体；设计稿中的马里奥像素小人省略（美术成本高、非必要元素）。

8. **导入入口的新家**：设计稿顶栏只有搜索，未画导入。库非空时导入入口放在顶栏溢出菜单（保留 `ActivityResultContracts.OpenDocument` 现有逻辑），空态大按钮照旧——导入功能是 `game-playback` 的硬要求，不能因改版丢失入口。

## Risks / Trade-offs

- [与在途 `save-states-fastforward` 的 GameScreen 改动冲突] → 存/读/快进 chips 的位置与逻辑不动（仍 TopEnd）；实现本 change 前先确认该 change 的代码已合入工作区（现已出现），面板重排时只动布局容器不动其代码路径
- [浅色翻转遗漏硬编码色值，出现"深色残迹"] → 收尾任务专门 grep `Color(0x` 清点，仅允许游戏屏黑底与面板色
- [键盘 keyCode 持久化解析失败] → try/catch 回默认映射；重绑 UI 只接受合法 keyCode
- [像素字标题在米白底对比度不足] → 红色标题或深灰正文按设计稿配色，验收时真机检查可读性
- [词典判定与用户认知不符（某游戏分类错误）] → 词典内容属可调数据，不构成 spec 行为变更；记录在代码注释中便于后续增补

## Migration Plan

纯 `:app` UI 层重构，无数据迁移；新 SharedPreferences key 首次读取即默认值。实现按"主题 → 导航/首页 → 库/详情 → 游戏屏 → 设置/按键映射"顺序小步提交，每步可构建可运行；回滚 = revert 对应提交，无跨模块影响。

## Open Questions

（无——默认映射、词典内容、面板尺寸等均已定或属实现期可调参数，不影响 spec 与任务拆分。）
