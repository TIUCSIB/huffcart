# Proposal

## Why

虚拟触屏是唯一的操作方式:蓝牙/USB 手柄(含电视盒子场景)接上后按键无响应,模拟器最核心的"手感"输入缺失;FC 游戏对输入延迟与可靠性敏感,物理手柄是玩家的自然期待。

## What Changes

- **固定标准手柄映射**:标准 Android 布局按键(面键 A/B、START、SELECT、十字键)开箱即用,无需任何设置;X/Y/肩键/扳机默认不映射。手柄输入作为玩家 1 输入,自动沿用既有席位路径(联机房主即 P1,加入端进本席位)。
- **摇杆→十字键**:左摇杆/方向帽(轴事件)按死区+迟滞转换为十字键输入,纯逻辑可单测;与触屏、键盘输入并存互不干扰。
- **输入解析顺序**:固定手柄映射表先行、用户键盘映射表兜底;现有按键映射页与键盘映射契约不变,手柄不占用键盘映射行。
- 游戏手柄中断开/重连(热插拔)不影响触屏输入,无需用户干预。

## Capabilities

### New Capabilities

（无——物理手柄与键盘同属 game-playback 的输入域,其 Purpose 明确涵盖"虚拟手柄与物理键盘操作",不另立能力。）

### Modified Capabilities

- `game-playback`: 新增「物理手柄输入」需求——固定标准映射、摇杆轴转换、与键盘/触屏共存、席位继承、热插拔;既有「按键映射与物理键盘输入」需求保持不变(手柄映射不占用键盘映射,其全部场景继续成立)。

## Impact

- `app/src/main/java/com/huffcart/app/MainActivity.kt`:重写 `dispatchGenericMotionEvent` 转发轴事件到游戏屏(新增 ~10 行)。
- `app/src/main/java/com/huffcart/app/ui/screens/GameScreen.kt`:注册/注销输入枢纽,摇杆状态接入 `onLocalButton`(现有单机/联机分派不变)。
- 新增 `app/src/main/java/com/huffcart/app/ui/game/GamepadInput.kt`:固定映射表 + 摇杆轴→十字键纯逻辑(JVM 可测)。
- `core-bridge`/`core-native`/`netplay`:零改动——输入经 `onLocalButton` 单一漏斗。
- `KeyMappingScreen`/`KeyMappingStore`:零改动(手柄映射不走用户映射表)。
