# Spec Delta

## MODIFIED Requirements

### Requirement: 游戏详情页
点击库条目（首页卡片）SHALL 进入该游戏的详情页，页内 SHALL 包含：hero 封面（无播放按钮浮层）、游戏名、分类与平台标签（FC / GB / GBC，按游戏平台呈现）、全宽「开始游戏」主按钮，以及简介区——命中内置简介词典的游戏 SHALL 显示其简介文案，未命中 SHALL 显示文件信息而不留空白数据块，文件信息中的格式文案 SHALL 按平台呈现（iNES (.nes) / Game Boy (.gb) / Game Boy Color (.gbc)）；点击「开始游戏」SHALL 进入游戏屏，行为与从首页直接点击条目一致，且按钮 SHALL 提供可感知的按压反馈。

#### Scenario: 详情页展示
- **WHEN** 用户点击某个游戏条目
- **THEN** 详情页显示该游戏的 hero 封面、名称、平台标签、简介区与「开始游戏」按钮，封面无播放按钮浮层，无崩溃

#### Scenario: 开始游戏
- **WHEN** 用户在详情页点击「开始游戏」
- **THEN** 进入游戏屏并开始运行该游戏（与首页直接点击等效）

#### Scenario: 简介与文件信息
- **WHEN** 游戏命中内置简介词典 / 未命中词典
- **THEN** 分别显示内置简介文案 / 显示文件大小等信息，页面不留空白块

#### Scenario: 平台标签与格式文案
- **WHEN** 用户分别打开一个 FC 游戏与一个 GB 游戏的详情页
- **THEN** 平台标签分别显示 FC 与 GB，文件信息中的格式文案分别为 iNES (.nes) 与 Game Boy (.gb)
