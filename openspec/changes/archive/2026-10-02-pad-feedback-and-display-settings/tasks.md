# Tasks

## 1. 反馈开关与按键音效（game-playback「按压反馈」）

- [x] 1.1 新增 `ui/game/PadFeedback.kt`：`PadFeedbackStore`（SharedPreferences，`haptic_enabled` / `key_sound_enabled`，缺省均 true）与单例运行时（开关标志、惰性 MODE_STATIC 音轨、~6ms 方波短脉冲合成、USAGE_MEDIA/SONIFICATION）；`./gradlew :app:compileDebugKotlin` 通过
- [x] 1.2 `PadControls.kt`：`rememberPadHaptic()` 改为 `rememberPadFeedback()`（震前判触觉开关、播放前判音效开关），十字键 / A/B / SELECT·START / 摇杆四处触发点接统一入口，确认换向不重复触发语义不变；编译通过
- [x] 1.3 `GameScreen.kt`：进入游戏屏时调用 `PadFeedback.init(context)`（读开关、惰性建音轨），确认横竖屏与联机态均初始化；编译通过
- [x] 1.4 `KeyMappingScreen.kt`：「虚拟手柄样式」行下新增「触觉反馈」「按键音效」两行 Switch（track/thumb 品牌红，行样式对齐 MappingRow），切换即持久化；编译通过

## 2. 画面比例（game-playback「画面呈现」+ app-shell「设置页结构」）

- [x] 2.1 新增 `ui/game/VideoSettingsStore.kt`：`DisplayAspect { NATIVE, RATIO_4_3, STRETCH }` + SharedPreferences 持久化（缺省 NATIVE）；编译通过
- [x] 2.2 抽取共用 FC 单选弹窗组件（白卡 + 红色光标 + 「点选即生效」），`KeyMappingScreen` 的虚拟手柄样式弹窗改用共享组件，行为不变；编译通过
- [x] 2.3 新增 `ui/screens/DisplaySettingsScreen.kt`（顶栏「画面设置」+ 画面比例行 + 共用弹窗三档选项）并接路由：`HuffcartApp.kt` 加路由、`SettingsScreen.kt`「画面设置」行解锁（声音设置、存档管理维持禁用占位）；编译通过
- [x] 2.4 `GameScreen.kt` `GameSession`：构造时读一次 `DisplayAspect`，渲染分支——NATIVE 整数倍 letterbox（原样）、RATIO_4_3 按 4:3 盒适配居中、STRETCH 铺满画面区，三档均走最近邻 paint；编译通过

## 3. 集成验证

- [x] 3.1 真机/模拟器手测：默认档画面与现状一致；切 4:3 / 铺满重进游戏生效且像素锐利；触觉/音效开关各自独立生效、换向不重复响、持久化跨进程；声音设置与存档管理仍禁用占位
- [x] 3.2 `openspec validate --change pad-feedback-and-display-settings` 通过；tasks 全部勾选；提醒归档顺序——先 `game-landscape-fullscreen` 后本 change
