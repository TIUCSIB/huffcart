# Spec Delta

## MODIFIED Requirements

### Requirement: ROM 导入
用户 SHALL 能通过系统文件选择器选择 `.nes` ROM 文件、包含 ROM 的 `.zip` 压缩包，以及 `.gb` / `.gbc`（Game Boy / Game Boy Color）ROM 文件，应用 SHALL 将其复制到应用私有存储（不申请存储权限），并在游戏库中可见、可启动。校验 SHALL 按平台分派：`.nes` 走 iNES 头（`NES\x1a`）；`.gb` / `.gbc` 走 GB 头（$100 处入口指令与 $104–$133 的 Nintendo logo 序列），$143 标志字节为 0xC0 时识别为 GBC。不符合对应平台格式的文件 SHALL 给出明确的失败提示，且不产生不可用的库条目。选择 `.zip` 压缩包时，应用 SHALL 逐条目按其扩展名与文件头判定平台并导入全部合法条目（可混合多平台）；目录与非 ROM 条目 SHALL 静默跳过；压缩包内不含任何合法 ROM 时 SHALL 给出明确提示且不产生任何库条目；压缩包损坏或无法读取时 SHALL 给出明确的失败提示。

#### Scenario: 导入合法 ROM
- **WHEN** 用户点击「导入 ROM」并在系统选择器中选择一个合法 `.nes` 文件
- **THEN** 游戏库出现该游戏条目，且无需任何运行时权限授予

#### Scenario: 导入非法文件
- **WHEN** 用户选择的文件缺少 iNES 头标识
- **THEN** 应用提示"不是有效的 FC ROM"，游戏库不新增条目

#### Scenario: 导入 GB 游戏
- **WHEN** 用户选择一个带合法 GB 头的 `.gb` 文件（如宝可梦或塞尔达梦见岛）
- **THEN** 游戏库出现该游戏条目（GB 平台），无需任何设置即可游玩

#### Scenario: GBC 游戏识别
- **WHEN** 用户选择 $143 标志为 0xC0 的 `.gbc` 文件
- **THEN** 游戏库以 GBC 平台收录该游戏

#### Scenario: 拒绝伪 GB 文件
- **WHEN** 用户选择的 `.gb` 文件缺少 Nintendo logo 序列
- **THEN** 应用给出明确的格式失败提示，游戏库不新增条目

#### Scenario: 导入含单个 ROM 的压缩包
- **WHEN** 用户选择一个内含单个合法 iNES ROM 的 `.zip` 文件
- **THEN** 游戏库新增该 ROM 条目，可直接启动，无需任何运行时权限授予

#### Scenario: 批量导入多 ROM 压缩包
- **WHEN** 用户选择一个内含 FC 与 GB 等多平台合法 ROM 及若干无关文件的 `.zip` 文件
- **THEN** 全部合法 ROM 逐一进入游戏库并各归其平台，无关文件被跳过，且反馈导入数量

#### Scenario: 压缩包内无有效 ROM
- **WHEN** 用户选择的 `.zip` 内不含任何通过平台校验的 ROM 条目
- **THEN** 应用提示压缩包内没有有效的 ROM，游戏库不新增条目

#### Scenario: 压缩包损坏
- **WHEN** 用户选择的 `.zip` 文件损坏或无法读取
- **THEN** 应用提示导入失败原因，游戏库不新增条目，应用不崩溃

### Requirement: 核心加载与运行
选中游戏后应用 SHALL 按其平台选择模拟核心（FC → FCEUmm；GB/GBC → Gambatte），初始化核心、加载 ROM 并进入运行态，游戏画面持续刷新；核心或文件错误 SHALL 以可见提示呈现，不得静默黑屏。

#### Scenario: 启动游戏
- **WHEN** 用户在游戏库点击一个已导入的游戏
- **THEN** 进入游戏屏，画面出现并持续刷新，无崩溃

#### Scenario: 启动 GB 游戏
- **WHEN** 用户从游戏库启动一个 GB/GBC 游戏
- **THEN** 应用加载 Gambatte 核心并进入运行态，画面按 GB 原生分辨率（160×144）持续刷新

#### Scenario: FC 与 GB 游戏先后游玩
- **WHEN** 用户先游玩一个 FC 游戏退出后，再启动一个 GB 游戏
- **THEN** 两次会话各自加载正确核心，均正常游玩，无跨平台资源残留导致的异常

## ADDED Requirements

### Requirement: 跨平台数据隔离
游戏存档数据 SHALL 按平台隔离：FC 游戏沿用既有存档文件布局（零迁移）；GB/GBC 游戏的存档槽、SRAM 电池存档与挂起档 SHALL 使用带平台区分的键，FC 与 GB/GBC 的同名游戏 SHALL 互不覆盖、互不读取对方数据。移除任一平台游戏时 SHALL 仅清除该游戏自身的存档数据。

#### Scenario: 同名游戏不串档
- **WHEN** 用户同时拥有 FC 与 GB 的同名游戏并分别存档
- **THEN** 两者的存档槽列表、SRAM 与挂起档各自独立，读档恢复到各自平台的存档

#### Scenario: FC 历史存档零迁移
- **WHEN** 从旧版本升级后进入此前存过档的 FC 游戏并打开槽位面板
- **THEN** 原存档槽位照常呈现并可直接读档，无需任何迁移操作
