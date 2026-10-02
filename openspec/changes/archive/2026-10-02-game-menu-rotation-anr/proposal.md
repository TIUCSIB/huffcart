# Proposal

## Why

游戏内快捷菜单展开时旋转设备，DropdownMenu 的弹出窗口在新布局下等待焦点事件 5 秒超时触发 ANR（logcat 实证：「Pop-Up Window is not responding」），用户可感知卡死。冒烟中已复现，属播放体验的稳定性收尾，修复成本极低。

## What Changes

- 游戏内快捷菜单在竖屏/横屏布局形态切换（旋转）时 SHALL 自动收起，消除 ANR；旋转后菜单可重新打开
- game-playback「画面呈现」需求补菜单在旋转时的行为与验收场景
- 非目标：菜单跨旋转保持展开；游戏屏之外页面的菜单行为

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `game-playback`: 「画面呈现」——新增游戏内快捷菜单在布局形态切换（旋转）时自动收起的要求与场景

## Impact

- app/ui/screens/GameScreen.kt：menuOpen 状态在 isPortrait 变化时复位（一处小改）
- 无存储、无核心层、无 netplay 影响
