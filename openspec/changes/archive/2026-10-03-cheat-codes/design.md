# Design

## Context

核心链路 FCEUmm → shim.c（dlsym 解析全部 retro_* 入口）→ LibretroCore。shim.c 未解析 cheat 符号；libretro_cbs.h 亦无 cheat typedef，但签名固定（retro_cheat_reset: `void(*)(void)`、retro_cheat_set: `void(*)(unsigned, bool, const char*)`），shim 直接声明函数指针即可。libretro 金手指语义：核心每帧应用已设置的码；修改码集的惯例是先 retro_cheat_reset() 再逐条 retro_cheat_set(index, enabled, code)。

## Goals / Non-Goals

**Goals:** GG 码添加 / 开关 / 删除、按游戏持久化、运行中即时生效、联机隔离。

**Non-Goals:** RAM 码（地址:值）——FCEUmm 对该格式的解析范围需 spike 后另立 change；码库搜索 / 下载；导入导出。

## Decisions

1. **JNI 面取批应用**：`nativeApplyCheats(String[] codes)` 内部先 reset 再逐条 set(enabled=true)，不做逐条细粒度 JNI——码数量个位数，批应用消除核心侧索引漂移问题，JNI 面最小。
2. **应用时机走会话命令**：扩展 ApplyCheatsCmd 在游戏线程帧末执行（与存 / 读档同线程纪律）；开关切换即置命令，下一帧生效。
3. **存储**：`files/cheats/<romBase>.cheats` 行格式（每行 `CODE\t0|1`），沿用 covers 索引缓存的纯文本行惯例——JVM 单测可测，不引 JSON 依赖；管理面板进出读盘。
4. **校验**：NES GG 字母表 APZLGITYEOXUKSVN、6 或 8 位、连字符可选（归一化去除后校验）；客户端校验为主。
5. **联机隔离**：入口随 netplayActive 隐藏（与存 / 读档一致）；会话启动时 netplay != null 则不应用任何码。
6. **入口与面板**：快捷菜单加「金手指」项（两形态共用 GameMenuItems）；面板为 Dialog（FC 白卡风格，与槽位面板同族）；呈现期间游戏继续运行（cheats 逐帧应用，无需暂停）。

## Risks / Trade-offs

- [个别码在 FCEUmm 下静默不生效] → 码有效性属核心行为；冒烟用社区成熟码（魂斗罗 SXIOPO）验收
- [校验字母表过严] → FCEUmm 遵循标准 GG 字母表；如后续发现放宽需求再调整
- [批应用与用户中途改码并发] → 统一走会话命令串行化，无并发窗口

## Migration Plan

无数据迁移（新目录新格式）。回滚 = 还原提交。

## Open Questions

（无——RAM 码明确列为后续 spike，不阻塞本 change。）
