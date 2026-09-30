# Tasks

## 1. 核心序列化

- [x] 1.1 shim 接入 `retro_serialize_size` / `retro_serialize` / `retro_unserialize` 三符号 + JNI（nativeSaveState / nativeLoadState）；`LibretroCore` 暴露 `saveState(): ByteArray?` 与 `loadState(ByteArray): Boolean`；验证：模拟器 240pee 冒烟——存→改变状态→读→画面回到存档点（llvm-nm 确认三符号导出；round-trip 见验收截图 32/34）

## 2. 会话层

- [x] 2.1 `GameSession` 命令通道（SAVE/LOAD 原子命令在游戏线程帧末执行，结果回调主线程）+ `<游戏名>.state0` 持久化；验证：存档文件生成（21976 字节）、跨应用重启读档恢复（force-stop 后读档精确回到存档时刻，截图 40）

## 3. UI 与快进

- [x] 3.1 游戏屏右上按钮组（存/读/FF，FF 循环 1→2→3→1 并显示倍率）+ 操作反馈提示；验证 spec save-states 全场景（已存档/已读档/暂无存档三反馈截图 31/32/34；反馈时长 2.5s）
- [x] 3.2 快进帧循环（`repeat(factor)` 连跑、仅渲染末帧、跳过被跳过帧的音频写入）；验证 spec fast-forward 全场景（GameLoop fps 实测：2x≈120、3x≈180、回 1x≈60 且音频 started；快进中输入翻页截图 37）

## 4. 集成验收

- [ ] 4.1 模拟器 240pee 全场景走查；用户真机以自备游戏抽测存/读/FF 各一次；`openspec validate --strict` 通过
