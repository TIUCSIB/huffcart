# Proposal

## Why

联机是本应用的主推能力（同一 Wi-Fi 双人联机），但入口目前藏在首页顶栏的图标里，可见性差；底部「游戏库」tab 的列表视图与首页封面网格功能高度重叠（搜索、分类、导入、详情入口首页均已具备）。把「游戏库」tab 让位给「联机」，联机升为一级入口，同时收敛冗余的列表形态。

## What Changes

- **BREAKING**（UI 结构）：底部导航由「首页 / 游戏库 / 我的」改为「首页 / 联机 / 我的」
- 联机屏从首页顶栏的二级页变为顶层 tab 页：去掉返回箭头与显式 BackHandler，页面切换走 tab 语义（saveState/restoreState）
- 首页顶栏移除联机图标，搜索与导入菜单保留
- 「游戏库」列表视图（LibraryScreen）退役：首页封面网格承担库浏览，空库引导已在首页实现
- game-library 中涉及「游戏库页」的需求措辞（分类筛选、名称搜索、详情页入口、封面同步范围）收敛到首页
- 冒烟测试通道不变：`am start --ez open_netplay true` 直达联机屏（路由名 `netplay` 不变）

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-shell`: 「应用启动与导航结构」三个顶层 tab 定义改为首页/联机/我的；「游戏库空状态」空态引导从游戏库页收敛到首页
- `netplay`: 「联机入口」从「首页顶栏提供联机入口」改为「底部导航栏提供联机 tab」
- `game-library`: 「游戏库列表视图」需求移除（列表退役）；「分类筛选」「名称搜索」「游戏详情页」「封面墙网格」中涉及游戏库页的措辞收敛到首页

## Impact

- `app/src/main/java/com/huffcart/app/ui/HuffcartApp.kt`：`topDestinations` 中间项替换（label「联机」+ Groups 图标）、`ROUTE_LIBRARY` 路由删除、`NetplayScreen` 调用改为 tab 形态（无 onBack）、`HomeScreen` 去掉 `onOpenNetplay`
- `app/src/main/java/com/huffcart/app/ui/screens/NetplayScreen.kt`：`AppTopBar` 不传 `onBack`（参数本就可空）、移除 `BackHandler`
- `app/src/main/java/com/huffcart/app/ui/screens/HomeScreen.kt`：移除顶栏 Groups 图标与 `onOpenNetplay` 参数
- `app/src/main/java/com/huffcart/app/ui/screens/LibraryScreen.kt`：删除（`LibraryCommon.kt` 的 GenreChipsRow/SearchField/EmptyLibrary 为首页共用，保留）
- 联机行为不受影响：房间发现、房间页路由（`room`）、开局流程均不变
- 决策依据：探索阶段用户未答复选项，按推荐项执行（列表退役；拆分方式取双 change），见 design.md
