# Design

## Context

现状(见 proposal.md - Why):

- 输入漏斗:`GameSession.onLocalButton(button, pressed)` 是本机输入唯一入口——单机写 P1,联机按席位(房主 P1 直注、加入端上送本席位掩码);触控(虚拟手柄)与物理键盘(经 `onPreviewKeyEvent` → keyCode 映射表)都已接此漏斗。
- `KeyMappingStore`:`RetroButton → keyCode` **一对一**映射(SharedPreferences),默认仅 DPAD_* + Z/X;映射行重绑捕获任意 KeyDown keyCode(排除 BACK)。
- `GameScreen` 根 Box `.onPreviewKeyEvent`:`repeatCount == 0` 的 KeyDown 驱动、KeyUp 复位——手柄按键(A/B/START/SELECT/十字键)在 Android 上同样以 KeyEvent(emit `KEYCODE_BUTTON_*` / `KEYCODE_DPAD_*`)到达,**映射表里加行即可用**;但摇杆/方向帽是 `MotionEvent` 轴事件(AXIS_X/Y/HAT_X/Y),现有链路完全收不到。
- `MainActivity` 干净(无输入逻辑),是 Activity 级事件分派的天然接入点。

## Goals / Non-Goals

**Goals:**

- 标准手柄开箱即玩:按键即插即用,零设置。
- 摇杆→十字键手感可靠:死区滤漂移,迟滞防边界抖动。
- 与触屏/键盘/联机席位零冲突;核心与网络层零改动。
- 轴转换纯逻辑 JVM 可测。

**Non-Goals:**

- 手柄按键重绑 UI(固定标准映射;用户诉求可后续以「第二张映射表 + 设置页分区」演进)。
- 多手柄多席位(本应用的多人 = 局域网联机,每设备一名玩家)。
- 手柄震动(rumble)、TV launcher(leanback 入口)、XInput/厂商专有协议适配。
- 映射手柄媒体键/系统键。

## Decisions

**决策 1:固定手柄映射表先行、用户键盘映射表兜底的单一查找顺序,不做来源判断。**
游戏屏解析顺序:先查固定手柄表(`BUTTON_A→A、BUTTON_B→B、BUTTON_START→START、BUTTON_SELECT→SELECT`),未命中再查用户键盘映射表;**十字键(DPAD_*)不在固定表**——若在,用户重绑方向键后键盘方向键仍会经固定表命中,击穿既有「重绑后原默认键不再驱动」契约;落键盘表后重绑语义对十字键与方向键统一,且默认值相同,手柄十字键开箱即用不受影响。备选「按 event.source 区分设备类」被否:蓝牙手柄的 source 组合五花八门(GAMEPAD/DPAD/KEYBOARD 混报),来源判断是兼容性泥潭。标签对齐原则:面键 A→A、B→B,键帽即预期,歧义走 Non-Goal 的重绑演进。实现期修正,spec 场景全部保持成立。

**决策 2:轴事件经 `MainActivity.dispatchGenericMotionEvent` 转发,游戏屏注册/注销接收器。**
沿用本会话已验证的回调解耦模式(`NetplayManager.gameListener`、`session.onEvent`):新增轻量输入枢纽(伴随对象),MainActivity 重写 `dispatchGenericMotionEvent` 仅在枢纽有接收器且事件来自摇杆类设备(`SOURCE_CLASS_JOYSTICK` 或含 HAT 轴)时转发并消费,返回 super 兜底其他场景。备选「Compose `Modifier.onGenericMotionEvent`」被否:该 API 在当前 BOM 的可用性未经验证,Activity 分派是框架保证存在的路径;备选「AndroidView + OnGenericMotionListener」被否:需在 Compose 树里插 View,复杂度不值。KeyEvent 仍走 GameScreen 既有 `onPreviewKeyEvent`,零改动。

**决策 3:摇杆→十字键为纯函数状态机 `AnalogStickState`,死区 0.35、触发阈值 0.55、释放阈值 0.40,含 AXIS_HAT_X/Y。**
每个通用运动事件:取轴值 → 逐方向与迟滞带比较 → 得到当前应按下的方向集;GameScreen 持有该状态,与上次方向集做差 → `onLocalButton` 增量调用(与 netplay `ButtonMask.diff` 同思路)。迟滞带(0.40–0.55)防止单方向在阈值附近反复横跳;死区 0.35 滤掉漂移摇杆。AXIS_HAT_X/Y(部分手柄/盒器方向帽以帽轴上报)走同一状态机,通常是 ±1 数字值,天然通过。状态机不区分设备 id——单玩家语义下多设备合流无害(Non-Goal 排除多席位)。方向集状态在游戏屏退出时随会话销毁,无泄漏面。

**决策 4:手柄 KeyDown 沿用现有 `repeatCount == 0` 过滤。**
手柄长按会产生重复 KeyDown,现有键盘路径已忽略重复,手柄自动继承,无需新代码;KeyUp 复位不受影响。

## Risks / Trade-offs

- [非标准手柄键码上报差异(山寨/老式手柄 BUTTON_* 缺失或错位)] → 固定映射覆盖 Android 标准布局,主流大厂手柄(Xbox/PS/Switch Pro/北通等)均遵循;异类手柄的十字键(REQ DPAD_*)仍可用,面键异常属可接受边界,后续可用重绑演进兜底。
- [面键 A/B 语义习惯分歧(部分玩家预期南键=A)] → 标签对齐为最不意外默认;重绑 UI 列入演进方向,本期不做。
- [轴事件在 Activity 分派与 Compose 焦点窗口的到达路径差异] → dispatchGenericMotionEvent 是 Activity 级兜底,不依赖焦点;真机验证覆盖蓝牙+USB 两种连接。
- [摇杆合流多设备时的方向闪烁] → 单玩家语义下极少见;状态机增量 diff 保证每次只发真实变化,闪烁面收敛于迟滞带内。
- [未知 ACTION 类型的轴事件(如 ACTION_HOVER_MOVE 触控笔)] → 枢纽仅在事件含摇杆类轴且非零时处理,其余一律透传 super。

## Migration Plan

无数据迁移、无破坏性变更:新 APK 覆盖安装即可,键盘映射SharedPreferences不动。回滚 = revert 提交。

## Open Questions

无。
