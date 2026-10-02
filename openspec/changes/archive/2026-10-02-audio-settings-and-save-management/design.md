# Design

## Context

音频链路已完整：FCEUmm → shim.c → GameScreen 的 AudioTrack（MODE_STREAM，阻塞写兼作帧节拍，见 GameScreen.kt 头注「design 决策 4」）。设置页两个占位行在 SettingsScreen.kt，画面设置页（DisplaySettingsScreen + VideoSettingsStore）是现成的页面与存储模式参照。即时存档目前单槽：`saves/<romName>.state0`，保存时已顺手截屏（CoverStore.saveScreenshotAsync）。快捷菜单在竖屏顶栏与横屏浮层共用同一菜单项集合；联机对局中菜单仅剩「退出」。

约束：音频阻塞写是帧时钟——任何静音/音量方案都不得停写 AudioTrack；核心层（core-bridge / core-native）不动；联机相关代码不动。

## Goals / Non-Goals

**Goals:**

- 声音设置三项（音量 / 静音 / 快进静音）持久化并作用到游戏音频输出
- 每游戏 4 存档槽，槽位面板支撑存 / 读档，缩略图 + 时间戳可视化
- 存档管理页按游戏浏览与删除
- 历史单槽存档零迁移沿用

**Non-Goals:**

- 存档导出 / 导入、云备份
- 触觉反馈 / 按键音效开关迁移（留在按键设置页）
- 游戏屏内实时改音量（设置页只在「我的」，进入游戏时生效）
- 倒带回溯（已否决）

## Decisions

### 1. 音量 / 静音作用点：AudioTrack.setVolume，绝不停写

- **决策**：会话启动时从存储读音量，`audioTrack.setVolume(v)`（0f–1f）一次性设定；静音 = `setVolume(0f)`。
- **理由**：AudioTrack 阻塞写是帧节拍，`pause()` / 停写会让游戏卡住；`setVolume` 只改输出增益，帧时钟不受影响。替代方案「静音时跳过写音频」会破坏帧推进节奏，「核心侧静音」需要动 core 层，均否决。
- 快进静音复用同一点：会话持当前速度与开关，进入快进帧循环前 `setVolume(0f)`，切回 1x 恢复设定音量。

### 2. AudioSettingsStore 沿用 VideoSettingsStore 模式

SharedPreferences，键：音量（Int 0–100，默认 100）、静音（Bool，默认 false）、快进静音（Bool，默认 false）。不做 DataStore 迁移——与项目现有存储惯例保持一致，收益不抵 churn。

### 3. 槽位布局：状态文件 + PNG 侧车，时间戳取文件 mtime

```
files/saves/
  <romName>.state0        # 槽 1（旧单槽文件原地沿用，零迁移）
  <romName>.state0.png    # 缩略图侧车（可选，缺图用占位兜底）
  <romName>.state1 .. .state3
```

- **理由**：无索引文件 → 无索引损坏问题；删除 = 删两个文件；时间戳用 `File.lastModified()` 免维护元数据。替代方案「JSON 索引 + 二进制打包」引入格式版本与损坏恢复复杂度，对 4 槽 × 百 KB 级数据不划算。
- 旧 `.state0` 无侧车图 → 槽 1 显示占位图（spec 已有「无缩略图兜底」场景）。

### 4. 槽位面板：一套 Compose 组件两处复用

菜单「存档」「读档」分别打开同一槽位面板（横排 4 槽卡片：槽号 / 缩略图 / 时间戳 / 空槽态），竖屏与横屏共用，遵循现有代码绘制控件「两形态一套实现」的惯例。存档流程复用现有截屏逻辑（保存时刻抓帧 → 写状态文件 → 异步写 PNG 侧车）；读档面板空槽置灰不可点。

### 5. 存档管理页：扫描 saves 目录 + 游戏库联表

进入时扫描 `saves/` 下 `.state0–3`，按 romName 分组，与游戏库条目联表取封面；无任何存档显示空状态引导。删除直接删文件（含侧车 PNG），不改库数据。不做「全部清除」类批量操作，保持页面最小面。

### 6. 路由与入口

HuffcartApp NavHost 按既有 `display_settings` 模式加 `audio_settings`、`save_management` 两条路由；SettingsScreen 两行 `enabled = true` 并接跳转，移除「即将推出」标注。

## Risks / Trade-offs

- [保存瞬间截屏异步写盘，面板即刻刷新可能无图] → 面板渲染时文件不存在则显示占位图，下次打开自然补全
- [mtime 精度在部分文件系统仅秒级] → 时间戳仅展示用途，可接受
- [快进静音实现误伤帧时钟] → 明确约束：只允许 setVolume，禁 pause/停写；任务单列验证项
- [磁盘占用增长（4 槽 × 状态 + PNG）] → NES 状态量级为百 KB 内，总量可忽略；本 change 不做容量管理
- [管理页删除与游戏内保存并发] → 管理页与游戏屏分属不同前台会话，并发窗口极小；删除仅作用文件，行为自然收敛

## Migration Plan

无数据迁移：`.state0` 即 1 号槽。回滚 = 还原提交，多出的 `.state1–3` 与 PNG 侧车文件不参与旧逻辑、无害残留。

## Open Questions

（无——RAM 码 / 金手指相关未知项属于后续 cheat-codes change，不在本 change 范围。）
