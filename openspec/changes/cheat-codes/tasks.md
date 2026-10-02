# Tasks

## 1. 核心桥接

- [x] 1.1 shim.c 补 retro_cheat_reset / retro_cheat_set 符号解析与 JNI 批应用函数，LibretroCore 暴露 applyCheats(codes: List<String>)；验证：core-native 编译通过
- [ ] 1.2 会话命令 ApplyCheatsCmd（游戏线程帧末应用）；验证：魂斗罗注入 SXIOPO 后生命数变为 30 条（真机/模拟器冒烟）

## 2. 管理面板

- [x] 2.1 CheatStore（files/cheats/<romBase>.json 读写，{code, enabled} 数组）+ GG 格式校验（6/8 位、字母表、连字符归一化）；单测覆盖合法 / 非法 / 归一化
- [x] 2.2 金手指面板 Compose 组件（列表 / 添加 / 逐条开关 / 删除，FC 白卡风格）；验证：非法码被拒并提示，开关与删除即时可见
- [x] 2.3 快捷菜单「金手指」入口（竖屏顶栏 + 横屏浮层，联机中不渲染）+ 开关切换走 ApplyCheatsCmd；验证：运行中启用 / 停用即时生效，完全重启后列表与开关保持

## 3. 验证

- [x] 3.1 联机冒烟：对局中菜单无金手指入口，核心无注入（对局同步不受影响）
- [x] 3.2 `openspec validate cheat-codes` 通过
