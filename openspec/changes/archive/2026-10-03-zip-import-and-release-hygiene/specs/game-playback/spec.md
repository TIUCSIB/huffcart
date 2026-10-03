# Spec Delta

## MODIFIED Requirements

### Requirement: ROM 导入
用户 SHALL 能通过系统文件选择器选择 `.nes` ROM 文件或包含 ROM 的 `.zip` 压缩包，应用 SHALL 将其复制到应用私有存储（不申请存储权限），并在游戏库中可见、可启动。对非 iNES 格式文件（缺少 `NES\x1a` 头）SHALL 给出明确的失败提示，且不产生不可用的库条目。选择 `.zip` 压缩包时，应用 SHALL 解包其中全部以 `.nes` 结尾且通过 iNES 头校验的条目并批量导入游戏库；压缩包内的目录与非 ROM 条目 SHALL 静默跳过；压缩包内不含任何合法 ROM 时 SHALL 给出明确提示且不产生任何库条目；压缩包损坏或无法读取时 SHALL 给出明确的失败提示。

#### Scenario: 导入合法 ROM
- **WHEN** 用户点击「导入 ROM」并在系统选择器中选择一个合法 `.nes` 文件
- **THEN** 游戏库出现该游戏条目，且无需任何运行时权限授予

#### Scenario: 导入非法文件
- **WHEN** 用户选择的文件缺少 iNES 头标识
- **THEN** 应用提示"不是有效的 FC ROM"，游戏库不新增条目

#### Scenario: 导入含单个 ROM 的压缩包
- **WHEN** 用户选择一个内含单个合法 iNES ROM 的 `.zip` 文件
- **THEN** 游戏库新增该 ROM 条目，可直接启动，无需任何运行时权限授予

#### Scenario: 批量导入多 ROM 压缩包
- **WHEN** 用户选择一个内含多个合法 ROM 与若干无关文件的 `.zip` 文件
- **THEN** 全部合法 ROM 逐一进入游戏库，无关文件被跳过，且反馈导入数量

#### Scenario: 压缩包内无有效 ROM
- **WHEN** 用户选择的 `.zip` 内不含任何通过 iNES 校验的 `.nes` 条目
- **THEN** 应用提示压缩包内没有有效的 FC ROM，游戏库不新增条目

#### Scenario: 压缩包损坏
- **WHEN** 用户选择的 `.zip` 文件损坏或无法读取
- **THEN** 应用提示导入失败原因，游戏库不新增条目，应用不崩溃
