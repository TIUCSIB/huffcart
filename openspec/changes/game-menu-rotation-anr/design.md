# Design

## Context

ANR 根因（冒烟实测 logcat）：菜单展开时旋转，DropdownMenu 的弹出窗口在新布局下等待焦点事件 5 秒超时（「Input dispatching timed out … Waited 5001ms for FocusEvent」）。Activity 因 configChanges 不重建，旋转只是重组，弹出窗口跨形态存续是问题根源。

## Goals / Non-Goals

**Goals:** 旋转时菜单自动收起，ANR 消除；竖→横、横→竖两向均覆盖。

**Non-Goals:** 菜单跨旋转保持展开（体验收益小、弹窗跨形态管理复杂）；非游戏屏菜单。

## Decisions

1. **收起而非迁移**：isPortrait 变化时复位 menuOpen（LaunchedEffect），DropdownMenu 随重组移除，弹出窗口不复存续。替代方案「让弹窗跨旋转保持展开」需处理窗口 re-parent 与锚点重算，风险远大于收益，否决。
2. **实现点唯一**：GameScreen 的 menuOpen 状态；GameTopBar / GameFloatingMenu 不感知，不新增参数。

## Risks / Trade-offs

- [旋转瞬间用户正要点菜单项] → 收起即取消本次交互，与主流模拟器行为一致，可接受
- [ANR 非确定性复现] → 冒烟按「菜单开→双向旋转→观察 5s→查 logcat」路径回归

## Migration Plan

无数据迁移。回滚 = 还原提交。

## Open Questions

（无）
