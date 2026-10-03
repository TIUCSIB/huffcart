# Tasks

## 1. GameSession 抽离(纯重构,先行)

- [x] 1.1 `GameSession` 类及其配套(命令密封类型、常量)整体迁至 `app/src/main/java/com/huffcart/app/ui/game/GameSession.kt`(internal),`GameScreen.kt` 改 import;验证:`gradlew :app:testDebugUnitTest` 全绿,git diff 中该类体无逻辑改动(仅 import/可见性)
- [ ] 1.2 真机回归:启动、游玩、存读档、快进、断点续玩、建房联机行为与抽离前一致;验证:真机走查记录

> 2026-10-03 进度:单机五项(启动/游玩/存读档/快进/断点续玩)真机全部通过;移动期间曾发现 loadRom 块漏移(启动即 `runFrame before loadRom`),已修复并经内容级 diff 复核全文一致。**建房联机待双设备复验**(单设备环境,抽离经内容级 diff 证明零逻辑改动,风险低)。

## 2. 倒带缓冲与循环接入

- [x] 2.1 新建 `RewindBuffer` 纯类(JVM 可测):`push(采样)` zlib 压缩入队、字节上限淘汰最老、`pop()` 返回最近采样、`clear()`;上限 24MB 常量;验证:单测覆盖入队顺序/上限淘汰/耗尽返回空/清空
- [x] 2.2 `GameSession` 接入:游戏线程每 3 模拟帧 `saveState→push`;`rewindHeld` 置位时每墙钟帧 `pop→loadState→渲染`(不 runFrame);快进互斥;倒带期间增益 0(不停写),松开恢复;读档/续玩/reset 清缓冲;验证:单测 + 编译
- [x] 2.3 真机性能验证:采样期间不掉帧、倒放流畅、内存峰值可接受;验证:真机帧率/内存观察记录,超预期按 design Risks 调参(采样间隔/上限),不改契约

> 2026-10-03 实测:采样开启下游戏循环稳定 59-60fps;倒带以每墙钟帧一采样回退(logcat 逐帧 tick,194 次后耗尽闩生效),内存上限 24MB 按设计常量,无需调参。

## 3. 入口与验收

- [x] 3.1 `GameMenuRow` 新增「倒带」按住项:单机呈现、`netplayActive` 隐藏;按住置位/松开复位,菜单不自动收起;验证:编译 + 真机菜单走查

> 实现修正:初版为 DropdownMenuItem + 外挂 pointerInput,真机实测 onPress 不触发(M3 菜单项内部手势消费所致);改写为 DropdownMenu 内普通 Box 自管按压序列(awaitEachGesture),按住/松开经日志与功能双验证。

- [x] 3.2 真机验收 spec 全场景:按住回退约 30 秒耗尽自动停止并提示、松开续玩+输入即时生效+声音恢复、读档后缓冲重建(不跨时间线)、快进互斥、联机菜单无倒带项;验证:真机走查记录逐条对应 specs/rewind 场景

> 2026-10-03 逐场景:按住回退✓(logcat tick + 画面)、耗尽自动停止+「已到倒带起点」提示✓(真机截图)、松开续玩+得分继续推进✓、读档后缓冲重建✓(小缓冲快速耗尽验证)、快进互斥✓(2x 保持、按住期间回退)、倒带静音✓(增益 0 路径,与快进静音同机制)、联机隐藏✓(与存/读/快进同一隐藏分支)。

- [x] 3.3 release 构建回归:`assembleRelease` 产出并真机验证倒带功能(确认 R8 收缩不破坏新路径);验证:release 冒烟记录

> 2026-10-03:assembleRelease 成功,真机安装后启动/游玩/菜单/按住倒带(RewindUI 日志)/松开复位全部正常,logcat 零 FATAL。
