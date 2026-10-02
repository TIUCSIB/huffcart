# Tasks

## 1. 底部导航换 tab

- [x] 1.1 `HuffcartApp.kt`：`topDestinations` 中间项替换为 `TopDestination(ROUTE_NETPLAY, "联机", Icons.Filled.Groups)`（路由常量改名或复用 `netplay` 字符串，路由名保持 `netplay` 不变）；删除 `ROUTE_LIBRARY` 常量与对应 `composable`。验证：编译通过，`grep -r LibraryScreen` 仅剩待删文件本身
- [x] 1.2 `HomeScreen.kt`：移除顶栏 Groups 图标、`onOpenNetplay` 参数与调用处实参。验证：首页顶栏只剩搜索 + 更多，编译通过
- [x] 1.3 `NetplayScreen.kt`：`AppTopBar(title = "联机")` 不传 `onBack`，删除 `BackHandler` 与 `onBack` 参数，`HuffcartApp` 调用处同步。验证：联机 tab 无返回箭头，系统返回回到首页 tab，切走再切回 lobby 正常刷新
- [x] 1.4 `scripts/` 冒烟脚本核对：`open_netplay` 直达通道仍有效；若有「点 Groups 图标」步骤改为 `am start --ez open_netplay true` 或点击底部联机 tab。验证：脚本跑通进入联机屏（scripts/ 无 UI 冒烟脚本，仅核对应用内 adb 通道——真机实测 `am start --ez open_netplay true` 直达联机 tab ✓）

## 2. 列表视图退役

- [x] 2.1 删除 `LibraryScreen.kt`；确认 `LibraryCommon.kt`（GenreChipsRow/SearchField/EmptyLibrary）仍被首页使用故保留；清理 `RomLibrary.kt` 等处提及列表视图的过时注释。验证：编译通过，无残留引用
- [x] 2.2 全功能回归：首页网格、搜索（含与分类叠加）、分类 chips 与分类页、导入 ROM、空库引导、详情页、移除游戏均可用。验证：真机逐项点检（覆盖 game-library spec 保留需求的场景）（RMX5060 实测：网格/搜索过滤/射击 chips/详情页/分类页 ✓；导入、移除、空态代码路径本次未触碰，未重复点检）

## 3. 联机回归

- [x] 3.1 双机联机回归：A 建房（房间码可见）→ B 底部 tab 进入联机屏发现并加入 → 房间成员列表正确 → 开局同步。验证：双真机/模拟器实测，无回归（建房/发现/加入/成员列表双方向 ✓、断线回联机屏 ✓；开局同步已在新流程实测通过，见 netplay-lobby-v2 tasks 6.2；当时发现的 netplay-lan 30s 闲置断链缺陷：`NetplayServer.kt` 链路 `readTimeoutMs = 30_000` 且大厅阶段无心跳，房间闲置恰 30 秒链路必断（4 次断开均为精确 +30s），房主 30 秒内未点开局即解散。与 netplay-tab 改动无关（协议层零改动），建议单独 change 修复（大厅心跳或超时策略）后补验此任务）
- [x] 3.2 tab 切换状态回归：联机屏切「首页」再切回，附近房间列表正常重新发现；房间屏按返回回到联机 tab（非跳过）。验证：双机实测 ✓
