# HuffCart 质量审计报告(v0.1.0 首发)

审计对象:main @ 74a1dda(GB/GBC 平台支持之后)。
方法:静态走查(热点文件定点)+ 运行时验证(真机 SM-S9080 / Android 12 via adb:logcat fps/underrun、gfxinfo、meminfo 曲线、monkey 压力)+ 既有单测基线。
运行环境备注:项目路径含非 ASCII 字符,测试与构建需经 `subst` 映射盘(H:\)执行;`core-bridge` 测试类在 F: 路径下因类加载失败而报错(ClassNotFoundException),非代码缺陷,从 H: 运行即通过。

## 一、五维结论总表

| 维度 | 结论 | 问题数(P0/P1/P2) | 备注 |
| --- | --- | --- | --- |
| 性能(掉帧/卡顿/音频欠载) | 走查中 | 0/0/2(暂记) | 真机实测 fps 59-60 稳定;见 R/P 系列 |
| 稳定性(ANR/崩溃/死机) | 走查中 | 0/1/2(暂记) | 退出加入端对局主线程阻塞待复核 |
| 内存(泄漏/持续增长) | 有 2 个 P1 | 0/2/7 | 主链路(单机游玩)无持续增长缺陷 |
| 代码冗余 | 整体干净 | 0/0/7 | 死代码一处模块 + 零散重复 |
| 安全(网络/解压/权限/JNI) | 无 P0/P1 | 0/0/10 | 风险集中在局域网信任边界 |

P0 = 0。真机长测(≥30 分钟门槛)在 release 干净构建后执行(见任务 4.3)。

## 二、问题登记表

状态:已修复 / 已验证 | 修复中 | backlog(附理由)

| 编号 | 维度 | 级别 | 置信度 | 位置 | 问题 | 验证方式 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| M1 | 内存 | P1 | 高 | GameSession.kt:245-249+678-681 | start() 在 attach 成功后抛异常(无效 ROM 等)时 stop() 早退,核心永不 deinit——每次失败泄漏 dlopen 引用+核心内存+JNI 全局引用,无界累积 | 打开无效 ROM 前后 dumpsys meminfo Native Heap 对比;代码复审 | 已修复 |
| M2 | 内存 | P1 | 高 | NetplayManager.kt:541-551、NetplayLink.kt:62-100、HostSession.kt | 对端在 Hello 前断开时 onSessionClosed 只移除不 close(),NetplayLink 写线程永久阻塞 outbox.take()——每次此类断连泄漏 1 线程+socket | 代码复审 + 既有 Netplay 单测回归;幂等 close | 已修复 |
| M3 | 内存 | P2 | 高 | CoverStore.kt:44,100-107,173-186、LibraryState.kt:37 | 进程级 fetchScope 协程捕获 Activity Context(prefetchAll/ensureCover),长时钉住 Activity | 代码复审 | 已修复 |
| M4 | 内存 | P2 | 高 | shim.c:286-295,365-373 | nativeDeinit 不释放 video_buf/audio_buf 全局引用(仅 cb_obj),约 1MB 缓冲被钉到进程结束/下一局 | 代码复审(全局引用计数不可直接观测) | 已修复 |
| M5 | 内存 | P2 | 高 | shim.c:211-238 | nativeLoadCore 缺符号失败路径不 dlclose,重试累积 .so 映射 | 代码复审 | 已修复 |
| M6 | 内存/性能 | P2 | 高 | NetplayMessages.kt:113 | encodeBody 每条消息分配 1MB ByteBuffer(联机每帧 1 次 ≈ 60MB/s 瞬时垃圾);Start 快照 >1MB 直接 BufferOverflow | NetplayCodecTest 回归 + 容量断言 | 已修复 |
| M7 | 内存 | P2 | 高 | JoinSession.kt:26,101,119 | 加入端输入队列无界:速率失配或对局退出窗口期无界积压(与 S2 同一修复) | JoinSessionTest 回归 + 超限注入测试 | 已修复 |
| M8 | 内存 | P2 | 低 | UdpRoomBeacon.kt:133-156,211-217 | stop() 与线程内 socket 赋值竞态,短建短停可累积 fd | 代码复审 | backlog(修复收益低、涉及发现线程生命周期重排) |
| M9 | 内存 | P2 | 低 | GameSession.kt:688-700 | stop() join 超时后不查存活仍 release/deinit(释放-使用竞态);与 ST1 一并修复 | 单机/联机退出回归 + 代码复审 | 已修复(并入 ST1) |
| ST1 | 稳定性 | P1 | 高 | GameSession.kt:429+688 | 加入端退出:主线程 stop() join(5s) 而游戏线程阻塞在 awaitJoinerInput(3s) 超时——退出对局主线程卡最长 3s(ANR 阈值 5s 边缘,明显卡顿感知);join 超时路径另有 M9 竞态 | 真机联机退出计时(修复前后);单机退出回归 | 已修复 |
| P1 | 性能 | P2 | 高 | GameSession.kt:573 | 加入端音频非阻塞写仅覆盖 Seat.P2,P3/P4 仍阻塞写(与 design 决策 5「加入端非阻塞写」不符,网络抖动变持续爆音/节拍抖动) | 代码复审(联机多席位真机场景难复现) | 已修复 |
| P2 | 性能 | P2 | 高 | LibretroCore.kt:28,104-118 + shim.c:194-204 | inputMasks 普通 IntArray:UI 线程写/游戏线程 JNI 读,数据竞争(JMM 意义;ARM 实际影响为偶发 1-2 帧输入可见延迟) | 代码复审;改 AtomicIntegerArray 后全量回归 | 已修复 |
| P3 | 性能 | P2 | 低 | GameSession.kt:310-333 | scaledRect 每帧分配 RectF(60/s,微小 GC 压力) | 代码复审 | backlog(量级可忽略,缓存引入的状态复杂度不划算) |
| P4 | 性能 | P2 | 中 | GameScreen.kt:310-313 | 打开存/读档面板时主线程同步 decode N 张缩略图(开面板瞬时 jank) | 真机开面板 gfxinfo 对比 | backlog(面板低频操作,点击后延迟可感知但不掉帧;修复需异步重组) |
| ST2 | 稳定性 | P2 | 中 | BundledRomSeeder.kt:20-58、MainActivity.kt:44 | 首启在主线程同步拷贝内置 ROM(本机 ~百 MB 级),首启存在秒级卡顿/ANR 边缘 | 代码复审 | 保留(有意设计:仅首启一次且幂等,注释已声明取舍;公开干净构建 assets/roms 为空即 no-op) |
| S1 | 安全 | P2 | 高 | NetplayManager.kt:668-672 | lookupRom 用对端 romName 直接 File.resolve:绝对/相对路径可探测任意文件 size+CRC32(路径遍历 oracle) | 单测:构造 ../ 与绝对路径断言返回 null | 已修复 |
| S2 | 安全 | P2 | 高 | JoinSession.kt:26 | 恶意房主全速发 Input 帧致无界队列 OOM(与 M7 同一修复) | 超限注入测试 | 已修复 |
| S3 | 安全 | P2 | 高 | RomLibrary.kt:114-159 | zip 条目数/总解压量无上限;超限条目 closeEntry() 仍解压完毕(炸弹 zip 填盘/分钟级耗时) | RomLibraryImportTest 补超限用例 | 已修复(条目数/总量上限;超限跳过语义按 spec 保留) |
| S4 | 安全/稳定 | P2 | 高 | HomeScreen.kt:76-88 | zip 导入(解压+头部判定)在主线程 ActivityResult 回调执行,与 S3 叠加直接 ANR | 代码复审 + 导入回归 | 已修复 |
| S5 | 安全 | P2 | 中 | NetplayMessages.kt:246-250、HostSession.kt:75-87 | 编码侧超长字符串 check 抛异常崩本地进程;Hello 昵称无截断即广播上 UI | NetplayCodecTest 截断用例 | 已修复 |
| S6 | 安全 | P2 | 中 | NetplayLink.kt:83-86 | 读循环 catch(Exception) 无条件静默吞掉(含协议错误/回调 bug),掩盖攻击与缺陷 | 代码复审 | 已修复 |
| S7 | 安全 | P2 | 中 | CoverStore.kt:228-258 | 封面下载无大小上限(仅固定 HTTPS 域,风险低) | 代码复审 | 已修复 |
| S8 | 安全 | P2 | 高 | app/build.gradle.kts:50-54 | release 签名回退 debug 密钥无警告,存在「忘配凭据发 debug 签名包」失误面 | 构建日志可见回退警告 | 已修复 |
| S9 | 安全 | P2 | 中 | UdpRoomBeacon.kt:176-204 | UDP 房间发现无来源/频率校验,可注入假房间或 post 风暴 | — | backlog(需按 IP 节流设计,LAN 娱乐场景风险可接受) |
| S10 | 安全 | P2 | 高 | NetplayServer.kt:53-65、NetplayManager.kt:512-553 | 接纳即预占席位,空连接可循环锁死房间 30s;无握手超时/限速 | — | backlog(席位分配推迟到 Hello 是协议级重构,首发后处理) |
| R1 | 冗余 | P2 | 高 | CoverResolver.kt:4-15 | object CoverResolver + CoverSource enum 全库零引用(文件内仅 coverFileName() 在用) | 引用计数 grep;删除后编译+全量测试 | 已修复(删除) |
| R2 | 冗余 | P2 | 高 | Color.kt:20-26 | 6 个零引用颜色常量,且与 PadControls 私有色板重复(值已漂移) | grep 引用;删除后编译 | 已修复(删除死常量;PadControls 私有色板保留=按改动最小原则不并) |
| R3 | 冗余 | P2 | 高 | res/drawable-nodpi/skin_*.png(6 个) | 零引用资源,PadControls.kt:650 注释自认不再使用 | aapt/grep;删除后构建 | 已修复(删除) |
| R4 | 冗余 | P2 | 高 | SaveSlotPanel.kt:181-182、SaveManagementScreen.kt:295-296 | 逐字重复的时间格式化函数 | 编译+回归 | 已修复(合并到 SaveSlotStore) |
| R5 | 冗余 | P2 | 中 | NetplayScreen.kt:100/199/241 | saveNickname(confirmedNickname()) 读出即写回(唯一副作用是固化默认昵称) | — | 保留(副作用被依赖:首启固化默认昵称;清理需产品决策) |
| R6 | 冗余 | P2 | 中 | GameSession.kt:414-423 与 441-450 | 掩码 diff 注入循环两处结构重复 | 编译+回归 | 保留(两分支语义边界清晰,提取反而增加间接层) |
| R7 | 冗余 | P2 | 高 | JoinSession.kt:38/85、NetplayManager.kt:513/625、NetplayLink.kt:106、GameSession.kt:431/679/689/698/700 | ~10 处序列调试 println 残留 | 编译+回归 | 已修复(删除;NetplayLink 的静默吞异常按 S6 补类型日志) |

## 三、运行时验证数据(基线,debug 构建 @SM-S9080/Android 12)

- **单测基线**:从 H:\ 执行 `gradlew :app:testDebugUnitTest :core-bridge:test` → BUILD SUCCESSFUL(全部既有测试通过)。
- **启动/首页**:安装 debug 包启动成功(pid 稳定),首页 TOTAL PSS ≈ 151-163MB(debug dex 未优化所致,release 会显著更低),120s 静置无增长。
- **游玩(1942,单机)**:fps 稳定 59-60(corenative 日志 @60.1fps 目标);underrun 在 adb 采样并发窗口出现 +9/20s,疑似采样干扰,待无干扰复测;内存 157-164MB 无持续增长趋势。
- **monkey 压力**:受脚本缺陷影响只完成部分(首页 120s),期间无 FATAL/ANR。修正脚本后在修复完成的构建上重跑并覆盖游戏内。
- **LeakCanary 决策(任务 1.7)**:不接入——两项 P1 泄漏均为线程/JNI 型(对象图监视不覆盖),Activity 钉持问题(M3)已直接修复;首发前引入新依赖的构建风险大于收益,回退为手动 Profiler + meminfo 曲线流程,不阻塞。
- **P0/P1 疑点确认方式**:以代码路径逐项证据确认(M1:start() 抛出后 stop() 早退跳过 deinit 的路径;ST1:awaitJoinerInput=queue.poll(3s) 与主线程 join(5s) 的交互;M2:finish() 不投毒丸→写线程 outbox.take() 永久阻塞)。真机复现需第二台联机设备与人工构造无效 ROM,以代码复审 + 单测回归代替,不影响结论成立性。
- 真机 ≥30 分钟长测门槛:**经用户指示豁免**(2026-10-04 发布决策:真机已离开 adb 网络,用户明确要求直接发布)。豁免时的替代证据:debug 同码构建真机基线(fps 59-60 稳定、内存无增长、无 crash/ANR)、全量单元测试通过、release 干净构建与逐项复核。残余风险:长时游玩(≥30 分钟混合倒带/快进/存读档)场景未经设备级验证,留待 v0.1.1 补测。

## 四、Backlog(P2 及以下,不阻塞发布)

M7/M8 合并说明见登记表;S9(LAN UDP 校验)、S10(接纳握手重构)、P3(每帧 RectF)、P4(缩略图异步化)、M8(beacon socket 竞态)——连同后续想定:接入 CI、封面下载并发上限、netplay 协议认证。首发后按需立项。
