# Spec Delta

## Purpose

让玩家以 2-3 倍速碾过慢节奏段落（过场、练级、赶路），并可随时一键恢复正常速度。

## ADDED Requirements

### Requirement: 快进调速
游戏屏 SHALL 提供快进控制，在 1x / 2x / 3x 之间循环切换；处于 N 倍速时，单位时间内游戏推进 SHALL 相应约为 N 倍；切回 1x 后速度与声音 SHALL 立即恢复。

#### Scenario: 加速生效
- **WHEN** 切换到 2x
- **THEN** 单位时间内游戏推进明显快于正常速度（可感知加速）

#### Scenario: 恢复正常
- **WHEN** 切回 1x
- **THEN** 游戏恢复常速，音频恢复输出

### Requirement: 快进期间可操作
快进期间，虚拟手柄输入 SHALL 仍实时生效。

#### Scenario: 快进中操作
- **WHEN** 快进中按住十字键方向
- **THEN** 游戏内持续响应（与常速下行为一致）
