# Spec Delta

## ADDED Requirements

### Requirement: 连发键
游戏内快捷菜单 SHALL 提供「连发 A/B」开关，默认关闭；开关在会话内生效（不跨会话持久化）。开启后，按住 A 或 B SHALL 以约 15Hz 自动交替按下/抬起（连发），未按住时 SHALL 不产生输入；A/B 之外的按键不受影响；开关切换 SHALL 即时生效并给出可见反馈。联机对局中该开关 SHALL 不可用（菜单不呈现该项）。

#### Scenario: 按住连发
- **WHEN** 连发开启后按住 A 键
- **THEN** 游戏内以约 15Hz 节奏连续触发 A（如射击类武器连续发射），松开即停

#### Scenario: 关闭恢复
- **WHEN** 连发开启且按住 A 的状态下将开关关闭
- **THEN** A 恢复为按住即持续按下的常规行为，无自动交替

#### Scenario: 默认关闭
- **WHEN** 未开启连发时按住 A
- **THEN** 行为与既往一致（按下持续生效）

#### Scenario: 联机不可用
- **WHEN** 联机对局中打开游戏内快捷菜单
- **THEN** 无「连发 A/B」开关项
