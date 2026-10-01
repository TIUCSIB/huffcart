# Design

## Context

`:app` 已是单 Activity + Compose + NavHost（字符串路由），主题为强制深色（`Theme.kt` 注释明确 dark-only 是当时的刻意选择），像素字体资源（Press Start 2P + 融合像素）已就位。游戏屏 `GameScreen` 为全屏 SurfaceView + 全屏 Canvas 浮层手柄（百分比坐标 hitTest），存/读/快进 chips 在右上角（在途 change `save-states-fastforward` 的实现，已出现在工作区）。ROM 数据源是 `filesDir/roms` 目录扫描，无任何元数据来源（无封面、无分类、无简介）。

参考稿（用户 GPT 生成的"吹卡带"版）确定的视觉：米白浅底 + 红顶栏 + 像素字标题、冷启动红底品牌页、三 tab（首页网格 / 游戏库列表 / 我的=设置）、2 列封面卡片（带 ⋮）、彩色分类卡片页、详情页全宽开始游戏按钮、竖屏分区游戏屏（上画面/下深灰控制面板 + FAMILY COMPUTER 装饰横幅）、按键设置页。相对上一版参考稿的最大变化：品牌定名「吹卡带」、设置从 tab 变为「我的」承载、双视图与分类页恢复。

## Goals / Non-Goals

**Goals:**

- 一个主题层翻转（浅色方案）覆盖全部页面；游戏屏内独立保持黑底，启动页独立保持红底
- 所有装饰性视觉（启动页主机插画、占位封面、分类图标、控制面板、FAMILY COMPUTER 横幅）纯 Compose 代码绘制，零位图资源、零网络
- 键盘映射闭环：按键设置页编辑 → 持久化 → GameScreen 生效
- 库的三个呈现面（首页网格 / 游戏库列表 / 分类页）共用同一份库数据与过滤逻辑

**Non-Goals:**

- 不动 `:core-bridge` / `:core-native` / FCEUmm 构建链；不引入任何新第三方依赖
- 不做真实封面/简介抓取、不做详情页截图缩略图行（无真实截图来源）
- 不做游戏屏顶部品牌条与 ⚙️ 入口（设计稿有，但破坏沉浸且场景怪异；设置从「我的」进入）
- 不实现画面设置/声音设置/存档管理的功能页（占位行）；存/读/快进 chips 的逻辑与位置不动（归 `save-states-fastforward`）

## Decisions

1. **浅色方案与取色纪律**：`Theme.kt` 换 `lightColorScheme`（背景米白 `#F6F1E7`、surface 白、primary 红 `#E60012`）；顶栏用红底白字的 `TopAppBar`。色板增加深红层次色（`HcRedDeep ≈ #8B1E1E`）供启动页底色、装饰横幅与阴影层次使用。现有代码里散落的硬编码色值统一改从 `MaterialTheme` 取色，仅游戏屏黑底与启动页红底例外。推翻原 dark-only 决策，更新相应注释。

2. **品牌与文案**：显示名「吹卡带」（huffcart 的直译梗，与项目名天然对应）；副标「FC 小霸王 · 经典回忆」与标语「按下开始键，回到童年」仅出现在启动页；顶栏品牌位显示「吹卡带」+ FC 角标（代码绘制小色块），不新增字符串资源以外的美术。

3. **启动页实现**：Compose 全屏页 + `LaunchedEffect` 延时约 1.2s 后导航到首页（`popUpTo(home)` 清栈，使启动页不可返回到达），不使用 Android 12+ system splash API——后者只能展示图标，承载不了插画与标语，而 Compose 页零额外成本。返回键在启动页直接退出应用（默认行为即可）。

4. **导航：延续字符串路由**：`home` / `library` / `mine` 三个 tab 路由 + `detail/{rom}` / `categories` / `category/{genre}` / `keymapping` 二级路由；bottom bar 隐藏逻辑（`currentRoute in topDestinations`）天然支持二级页。不上 type-safe Navigation API——现网字符串路由工作正常，本次改动聚焦视觉而非迁移。

5. **双视图与共享库状态**：首页网格（`LazyVerticalGrid` 2 列）与游戏库列表（`LazyColumn`）共享同一个库状态（ROM 列表 + 关键字 + 当前分类 + 移除动作），抽一个轻量 state holder（普通类 + `mutableStateOf`，不引 ViewModel 依赖变化），两屏各自装配。列表行缩略封面复用封面生成器的小尺寸绘制。

6. **分类页**：6 类固定集合（动作 / 射击 / 冒险 / 益智 / 格斗 / 赛车，与词典一致；设计稿 chips 与分类页不一致，统一为此集合，体育不设）。每类一个主题色 + Compose Canvas 绘制的像素小图标（约 8×8 图案，手绘矩阵常量），点卡片进 `category/{genre}` 列表页（复用列表视图）。

7. **移除游戏**：卡片 ⋮ 与详情页菜单触发确认对话框（含游戏名），确认后删除 `roms/<名>.nes` 与 `romsaves/<名>.srm`、`romsaves/<名>.state0`（前缀匹配该游戏的全部存档文件），随后刷新库状态。仅操作应用私有目录，无系统范围影响。

8. **竖屏游戏屏分区**：竖屏改 `Column`：上区 `SurfaceView`（权重 1）、下区固定高度控制面板（约 260-280dp）。`GameSession` 渲染循环的整数倍缩放/letterbox 以 canvas 尺寸计算，canvas 变成上区尺寸后**该逻辑无需改动**——分区是纯布局层变化。

9. **手柄控件重构为组件化 `PadControls`**：弃用全屏 Canvas 自绘 + 百分比 hitTest（斜角误触、代码难读），改为标准 Compose 组件（每个按键独立 `pointerInput` 命中区域），一套组件两处装配：竖屏放深灰圆角面板内（面板内含白色描边十字键、红色圆形 B/A、深灰 SELECT/START 胶囊白字、底部 FAMILY COMPUTER 红色装饰横幅），横屏作半透明浮层（保持现要求）。按下高亮用状态驱动的红色，替换现有红色 alpha 底。

10. **封面与分类/简介词典**：占位封面 = `kotlin.random.Random(hash(name))` 种子生成的对称像素块图案（8×8 半块镜像）+ 固定复古调色板，`Canvas` 绘制——确定性即"同一游戏同一封面"。分类与简介用一个内置 `Map`（常见 FC 游戏约 20 款，键为规范化文件名：小写、去空格与常见后缀），未命中归「全部」/显示文件信息。不建数据库：元数据的意义只是让 UI 有内容可摆，词典是成本最低且可随时替换数据源的方案。

11. **按键映射存储与输入**：`SharedPreferences`（避免为几个 Int 引入 DataStore 依赖）存 `虚拟键 → keyCode(Int)` 的简单格式，解析失败回默认；默认映射按设计稿：上/下/左/右→方向键、A→Z、B→X。GameScreen 在根 `Box` 上加 `onPreviewKeyEvent`：keydown（忽略 repeat）置位、keyup 复位，与触控层各自独立写 `setButton`，互不干扰。

12. **导入入口**：设计稿顶栏只有搜索与 ⋮，未画导入。导入入口放首页/游戏库顶栏溢出菜单（保留 `ActivityResultContracts.OpenDocument` 现有逻辑），空态大按钮照旧——导入功能是 `game-playback` 的硬要求，不能因改版丢失入口。分类入口走 chips 行末尾的「分类」chip。

## Risks / Trade-offs

- [与在途 `save-states-fastforward` 的 GameScreen 改动冲突] → 存/读/快进 chips 的位置与逻辑不动（仍 TopEnd）；实现本 change 前先确认该 change 的代码已合入工作区（现已出现），面板重排时只动布局容器不动其代码路径
- [浅色翻转遗漏硬编码色值，出现"深色残迹"] → 收尾任务专门 grep `Color(0x` 清点，仅允许游戏屏黑底、控制面板色与启动页红底
- [键盘 keyCode 持久化解析失败] → try/catch 回默认映射；重绑 UI 只接受合法 keyCode
- [移除游戏误删] → 确认对话框明确显示游戏名；仅删应用私有目录文件；SRAM/即时存档一并清理避免残留孤儿文件
- [双视图与分类页带来重复代码] → 三处共享同一 state holder 与过滤逻辑，视图层只做装配
- [词典判定与用户认知不符（某游戏分类错误）] → 词典内容属可调数据，不构成 spec 行为变更；记录在代码注释中便于后续增补

## Migration Plan

纯 `:app` UI 层重构，无数据迁移；新 SharedPreferences key 首次读取即默认值。实现按"主题/品牌 → 启动页与导航 → 库三视图与详情 → 游戏屏 → 我的/按键设置"顺序小步提交，每步可构建可运行；回滚 = revert 对应提交，无跨模块影响。

## Open Questions

（无——默认映射、词典内容、面板尺寸、启动页时长等均已定或属实现期可调参数，不影响 spec 与任务拆分。）
