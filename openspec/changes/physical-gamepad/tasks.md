# Tasks

## 1. 手柄输入纯逻辑(JVM 可测先行)

- [x] 1.1 新建 `GamepadInput.kt`:固定映射表(`BUTTON_A→A、BUTTON_B→B、BUTTON_START→START、BUTTON_SELECT→SELECT、DPAD_*→方向`)与解析函数(手柄表先行、键盘映射表兜底);验证:单测覆盖手柄键命中/键盘键兜底/两表同键同义/未映射键返回空

> 实现期修正(design 决策 1 已同步):固定表收窄为 BUTTON_* 四键,十字键(DPAD_*)落用户键盘映射表——否则用户重绑方向键后键盘方向键仍会经固定表命中,击穿「重绑后原默认键不再驱动」既有契约;默认值相同,手柄十字键开箱即用不变。

- [x] 1.2 新建 `AnalogStickState` 纯状态机:死区 0.35、触发 0.55、释放 0.40,处理 AXIS_X/Y 与 AXIS_HAT_X/Y,输出当前方向集;验证:单测覆盖死区不触发/越阈值触发/回中复位/迟滞带不抖动/帽轴数字值直通

## 2. 接线:轴事件到输入漏斗

- [x] 2.1 新增轻量输入枢纽(伴随对象):游戏屏注册/注销接收器;`MainActivity.dispatchGenericMotionEvent` 摇杆类事件转发并消费,其余透传 super;验证:编译 + 单测(枢纽转发/无接收器不拦截)
- [x] 2.2 `GameScreen` 接入:注册接收器,每个轴事件经 `AnalogStickState` 得方向集,与上次做差增量调 `onLocalButton`(单机/联机分派复用);手柄 KeyEvent 解析切换为 1.1 的解析函数;验证:编译 + `:app:testDebugUnitTest` 全绿
- [x] 2.3 `GameScreen` 退出时注销接收器并复位摇杆方向(松开全部摇杆驱动键);验证:单测 + 代码走查

## 3. 真机验收与回归

- [ ] 3.1 蓝牙手柄真机验收(spec 全场景):默认映射即玩、摇杆走位与回中、未映射键无副作用、与键盘/触屏共存、联机房主手柄即 P1、热插拔;验证:真机走查记录逐条对应 specs 场景

> 2026-10-03 进度(MuMu 云机,无实体手柄):键码级验证已在 debug 构建真机完成——固定表 BUTTON_A/B/START/SELECT→A/B/START/SELECT(adb keyevent 96/97/108/109 → GameKeys 日志)、键盘兜底(DPAD_DOWN→DOWN)、未映射键(BUTTON_X)静默无副作用,全部通过。**剩余:实体蓝牙手柄的按键+摇杆轴+热插拔手测**(轴事件无法用 adb 注入)。

- [ ] 3.2 USB 手柄(或有线模式)复验默认映射与摇杆;验证:真机走查记录
- [x] 3.3 回归:触屏/键盘/快进/倒带/存读档行为不变,release 构建冒烟(确认 R8 不破坏新路径);验证:真机走查记录

> 2026-10-03:键盘解析路径 debug 真机回归通过;release clean 重建+安装+启动+进游戏验证通过(首页渲染完好),R8 未破坏新路径经 mapping.txt 证实(resolve/AnalogStickState/GamepadAxisHub 全部存在且正确内联,release 与 debug 输入代码路径一致)。**release 上的按键级复验被设备故障阻断**:MuMu 云机桌面进程反复 OOM、显示/焦点错位(设备自身问题,非应用崩溃,logcat 零 FATAL),该项并入 3.1 实体手柄手测时顺带完成。
