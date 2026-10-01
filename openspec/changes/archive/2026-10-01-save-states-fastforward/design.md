# Design

## Context

`game-playback` 主 spec 已归档生效：`GameSession` 游戏线程串行调用 attach / loadRom / runFrame / unload / deinit（retro_* 单线程纪律，见 shim.c 文件头）；AudioTrack 阻塞写为帧节拍；FCEUmm pin 于 `CORE_COMMIT.lock`。本 change 在其上做纯增量。

## Goals / Non-Goals

**Goals:**

- 打通 libretro 标准状态序列化，存/读一键完成
- 单槽位存档跨进程重启有效
- 快进 1x/2x/3x 循环，期间保持可操作
- 游戏屏内轻量按钮组，不打断游玩

**Non-Goals:**

- 多存档槽与槽位管理 UI、存档缩略图
- 倒带（rewind）、慢放、自动存档、云同步

## Decisions

1. **状态序列化走 libretro 标准三符号**（`retro_serialize_size` / `retro_serialize` / `retro_unserialize`）：状态格式是核心私有实现，不自设计格式。fceumm 的状态与其核心版本绑定，`CORE_COMMIT.lock` 变更可能使旧档失效（见 Risks）。
2. **存/读命令经原子引用下发到游戏线程执行**：序列化必须与 `retro_run` 同线程（fceumm 状态非线程安全）；在帧循环每帧结束后检查命令。结果经主线程回调更新 Compose 状态展示反馈。备选"加锁并发访问核心"违背既有单线程纪律，否决。
3. **存档文件 `romsaves/<游戏名>.state0`，核心状态裸 dump 直写**：每个数百 KB，IO 无压力；读档失败（核心拒收/大小不符）按"暂无存档"降级提示，不做格式迁移。
4. **快进 = 帧循环内 `repeat(factor) { runFrame() }`、仅渲染末帧、被跳过帧的音频不写入**：AudioTrack underrun 自动补零即静音，退出 FF 恢复出声——实现零风险且行为可预期。备选的重采样变调（加速音效）需要额外音频管线，v2 再议。
5. **UI 为右上角三个半透明小圆钮**（存 / 读 / 快进），FF 点击循环 1→2→3→1 并在钮上显示当前倍率；不引入全屏暂停菜单（v1 保持轻）。

## Risks / Trade-offs

- [核心升级导致旧 `.state0` 失效] → 读档失败降级为"暂无存档"提示；核心版本已由 `CORE_COMMIT.lock` 锁定，风险受控
- [快进期间完全静音] → 刻意取舍：变调音频需重采样管线，超出 v1 体量；v2 可作为增强
- [3x 在低端真机的负载] → FF 只增加模拟帧数（FCEUmm 单帧耗时远小于 5ms），渲染仍每墙钟帧一次，负载增幅有限；验收时实测确认

## Open Questions

- 无阻塞项。
