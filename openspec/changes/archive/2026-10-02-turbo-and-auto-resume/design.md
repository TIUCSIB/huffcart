# Design

## Context

存 / 读档的会话命令机制（pendingCommand → 游戏线程帧末执行）与 4 槽文件布局（SaveSlotStore）现成。连发本质是输入合成问题——本机输入经 core.setButton 写掩码，连发需在掩码写入前按帧相位调制 A/B。退出序列现有约束：stop() 置 running=false 后 join 游戏线程，核心资源在线程退出后释放。

## Goals / Non-Goals

**Goals:** A/B 15Hz 连发（会话开关，默认关）；退出自动挂起 + 重进询问续玩。

**Non-Goals:** 速率可调、方向键连发、连发开关持久化、挂起档缩略图与管理入口。

## Decisions

1. **连发实现点在会话帧循环**：GameSession 持 turboEnabled 与用户按住镜像（userA/userB）；连发开启时 A/B 掩码每帧由会话直接驱动（effective = userPressed && phase，相位以 frameNo/2 交替，60fps 下 15Hz），关闭时恢复原有事件直写路径。不在 core.setButton 事件路径里做相位——否则半周期置位无法撤回。
2. **连发开关走快捷菜单**：与快进同为游戏途中随手扳的开关（小霸王 TURBO 语义），不持久化、默认关；netplayActive 时不渲染。
3. **挂起档文件**：saves/<romBase>.resume（核心私有状态格式，同槽位文件），不经 SaveSlotStore 槽位枚举（面板与管理页自然扫不到）。
4. **挂起保存时机**：stop() 置挂起命令；命令处理时机调整到 loop 顶部，保证 surface 缺失等提前 continue 路径也能处理；与 SRAM 落盘共存（互不干扰，均幂等）。
5. **续玩询问**：GameScreen 组合期检测挂起档存在（且 netplay == null）→ Compose Dialog「继续上次 / 重新开始」；继续 = 启动命令恢复挂起档（游戏线程执行）；重新开始 = 删除文件照常起跑。询问期间游戏已从开机状态起跑为可接受体验。
6. **联机排除**：netplay != null 时 stop() 不置挂起命令、start() 不询问，与 spec 一致。

## Risks / Trade-offs

- [挂起档与槽位档混淆] → 独立文件名 .resume，互不干扰
- [重进询问打断「就想重新玩」的用户] → 一次点击即达重新开始；仅在有挂起档时出现
- [loop 命令时机调整触及退出序列] → stop() 现有 join(5s) 时序不变；冒烟覆盖「退出即重进」路径
- [连发与联机回归] → 连发在本机掩码合成层注入；联机中开关不可用，不存在开启状态跨入联机

## Migration Plan

无数据迁移。回滚 = 还原提交（.resume 文件对旧代码无害）。

## Open Questions

（无）
