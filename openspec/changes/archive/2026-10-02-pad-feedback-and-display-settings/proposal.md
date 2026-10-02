# Proposal

## Why

虚拟手柄的触觉反馈目前硬编码开启、无法关闭；「真机手感」还缺一块按键音效（像真 FC 手柄那样按下有声音，目前完全没有）。同时设置页的「画面设置」仍是禁用占位，横屏全屏下 8:7 原生画面两侧黑边大，用户希望可自选画面比例（更大画面或经典 4:3 观感）。两项都是设置页二期最直接的用户诉求。

## What Changes

- **按键设置页**新增两行独立开关：
  - 「触觉反馈」：控制现有虚拟手柄按压震动（默认开启）；
  - 「按键音效」：新增按键按下短促音效（默认开启）——十字键按下、A/B 按下、SELECT/START 按下、摇杆首次按下各响一声，滑动换向与推杆过程不重复触发；音源采用程序合成短脉冲（无素材依赖），后续可换音频素材而不动调用方。
- **画面设置页**从「即将推出」解锁为可进入页：第一项为「画面比例」三档选择（复用虚拟手柄样式的 FC 菜单弹窗）：
  - **原生 8:7**（默认）：维持现状整数倍缩放 + letterbox 黑边，像素完美；
  - **经典 4:3**：老电视比例，水平非整数微拉伸（保持最近邻、禁平滑插值）；
  - **铺满**：无视比例填满画面区（画面变形，画面最大化）。
- 反馈开关与画面比例均持久化（SharedPreferences 轻量约定）；游戏屏启动时读取一次即满足「改完立即生效 + 下次进入仍生效」（设置页仅主界面可达，游戏屏内无改设置入口，无需监听机制）。
- 物理键盘通路不触发音效/震动（范围边界，非目标）。

## Capabilities

### New Capabilities

（无——均为既有能力的 requirement 变更，不新建能力。）

### Modified Capabilities

- `game-playback`：
  - 「虚拟手柄输入」requirement：新增触觉反馈与按键音效 SHALL 可独立开关（默认开启）、触发时机与换向不重复触发约束；
  - 「按键设置页」requirement：页面 SHALL 新增触觉反馈、按键音效两行开关并持久化；
  - 「画面显示」requirement：整数倍缩放 letterbox 由固定行为改为默认档位，画面比例 SHALL 可在画面设置页选择（原生 8:7 / 经典 4:3 / 铺满），非整数档位 SHALL 保持最近邻（禁平滑插值）。
- `app-shell`：
  - 「设置页结构」requirement：画面设置 SHALL 从禁用占位解锁为可进入画面设置页（声音设置、存档管理维持禁用占位）。

## Impact

- **代码**：
  - 新增 `ui/game/PadFeedback.kt`（反馈运行时：开关标志 + 程序合成按键音）与 `ui/game/PadFeedbackStore.kt`（SharedPreferences）；
  - 新增 `ui/game/VideoSettingsStore.kt`（画面比例枚举 + 持久化）；
  - 修改 `ui/game/PadControls.kt`（四处触觉调用改走统一反馈入口）、`ui/screens/KeyMappingScreen.kt`（两行开关）、`ui/screens/SettingsScreen.kt`（画面设置解锁）、`ui/HuffcartApp.kt`（画面设置路由）、`ui/screens/GameScreen.kt`（会话启动读设置 + 渲染缩放按比例分支）；
  - 新增 `ui/screens/DisplaySettingsScreen.kt`（画面设置页 + FC 选择弹窗）。
- **音频**：按键音使用独立 AudioTrack 短脉冲，与游戏 AudioTrack 混音互不干扰；音量跟随媒体音量。
- **渲染**：4:3 / 铺满档位打破「整数倍缩放」的既有保证（仅此两档），最近邻绘制（`isFilterBitmap = false`）保持不变。
- **spec**：`game-playback` 三条 requirement 修改，`app-shell` 一条 requirement 修改；无数据迁移、无新权限、无新依赖。
