# Design

## Context

底部导航与路由集中在 `HuffcartApp.kt`：`topDestinations`（首页/游戏库/我的）+ 自绘 bottomBar（`popUpTo(ROUTE_HOME){saveState=true}` + `restoreState` 切换语义已就位）；`netplay` 路由为二级页，`NetplayScreen` 带 `onBack`/`BackHandler`；联机入口是 `HomeScreen` 顶栏 Groups 图标。`LibraryScreen`（列表视图）与首页共用 `LibraryCommon.kt` 的 GenreChipsRow/SearchField/EmptyLibrary。`AppTopBar.onBack` 本就可空参数，tab 化无需改组件。

用户未答复探索阶段的选项，按推荐项执行（决策 0：列表退役、拆双 change）。

## Goals / Non-Goals

**Goals:**

- 三 tab 结构：首页 / 联机 / 我的，联机为顶层 tab（无返回箭头）
- 首页顶栏去联机图标；列表视图整体退役，首页即库
- spec 措辞同步：app-shell / netplay / game-library 三处收敛

**Non-Goals:**

- 不改联机功能本身（发现、房间、开局、断线处理均不动）
- 不做网格/列表双视图切换（决策 1）
- 不调整「我的」页与设置结构
- 不改冒烟测试通道（`open_netplay` 路由名不变）

## Decisions

1. **列表视图退役而非保留双视图**：首页已覆盖网格 + 搜索 + 分类 chips + 导入 + 空态引导（`EmptyLibrary` 已在 HomeScreen 实装），列表无独占功能；保留双视图多一个状态位和两套视图维护成本。备选「首页加视图切换」被否——用户量级与诉求（联机一级化）不支持。
2. **tab 切换语义沿用现有 bottomBar 实现**：`navController.navigate(route){ launchSingleTop; popUpTo(ROUTE_HOME){saveState=true}; restoreState=true }` 对新三 tab 原样成立；联机屏的 lobby 启停（`refreshLobby`/`stopLobby` 在 `DisposableEffect`）在离开组合时停止——与今天离开二级页的行为一致，无新增生命周期问题。
3. **NetplayScreen tab 化最小改动**：`AppTopBar(title = "联机")` 不传 `onBack`（参数可空，返回箭头自动不渲染）、删 `BackHandler` 与 `onBack` 参数；`BackHandler(onBack)` 删除后系统返回走导航默认行为（tab 栈 popUpTo home）。
4. **删除列表页的拆解顺序**：先改 `topDestinations` 与路由，确认编译无 `LibraryScreen` 引用后再删 `LibraryScreen.kt`；`LibraryCommon.kt` 保留（首页共用）。`RomLibrary.kt` 中的文档注释提及随实现顺手清理。
5. **联机横幅/房间流不动**：`room` 路由从联机 tab push，返回回联机 tab（`popBackStack` 语义不变）；smoke 通道 `navigate("netplay")` 直达 tab 依然有效。

## Risks / Trade-offs

- [老用户心智：游戏库 tab 消失] → 首页网格即库，所有原有路径（详情/分类/搜索/导入）在首页可达；发布说明一句话交代
- [adb 冒烟脚本依赖旧导航结构（点顶栏进联机）] → 路由名不变，`open_netplay` 直达通道不受影响；若脚本另有「点 Groups 图标」步骤需同步（实施时核对 scripts/）
- [tab 化后联机屏状态在 tab 间切换时被保存/恢复] → `restoreState` 恢复组合时 `DisposableEffect` 重新 `refreshLobby`，与重新进入二级页等价；双机联机回归覆盖

## Migration Plan

纯 UI 层改动，无数据迁移。回滚 = revert 导航层提交。

## Open Questions

（无）
