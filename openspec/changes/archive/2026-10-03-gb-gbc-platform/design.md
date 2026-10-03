# Design

## Context

现状(见 proposal.md - Why):

- 编译链:`scripts/build-core.sh` 完全 FCEUmm 硬编码——克隆 libretro-fceumm、单 commit 锁(`CORE_COMMIT.lock`)、逐 ABI 构建(NDK clang + 32K 命令行超限时的响应文件链接方案)、产物拷贝为 `libfceumm_libretro.so`。
- 播放层平台无关已验证:`LibretroCore` 经通用 libretro API 驱动核心,分辨率/帧率/采样率全部由核心上报(`nativeGetTiming` + av_info),`GameScreen` 缩放由 `videoInfo` 驱动——160×144 无需任何渲染改动;倒带/快进/手柄/音频延迟/即时存读档全部只依赖通用序列化与帧循环。
- 平台硬编码散点:`GameSession.coreLib` 写死 `libfceumm_libretro.so`;`RomLibrary` 只认 `.nes` 与 iNES 头;`SaveSlotStore.baseName` 只剥 `.nes`;`DetailScreen` 写死「iNES (.nes)」;`LibraryCommon`/`DetailScreen` 写死「FC」角标;`CreateRoomScreen` 选游戏列出全部库;金手指入口不区分平台,`CheatStore` 是 NES Game Genie 字母表。
- 联机:协议 v2 按 FC 四席位设计(逐帧输入掩码 + CRC),GB 串联线联机是另一种协议,不在本期。

## Goals / Non-Goals

**Goals:**

- GB/GBC 端到端可玩:导入 → 库呈现 → 游玩 → 存读档/倒带/快进/手柄,零特殊操作。
- 平台判定纯逻辑化,导入校验与库元数据按平台分派。
- FC 既有体验零回归:历史存档零迁移、联机与金手指对 FC 行为不变。
- 编译链支持按核心扩展(后续 SFC/GBA 各自一个 change 复用)。

**Non-Goals:**

- GB 串联线联机、金手指对 GB 开放(Gambatte 作弊契约未验证,后续演进)。
- GB 内置游戏播种;GB 封面占位比例适配(占位图维持 FC 比例,装饰性)。
- SFC/GBA/其他平台(各自独立 change)。
- GB 专属外设(Game Boy Printer、红外、震动)。

## Decisions

**决策 1:平台判定为纯函数 `RomPlatform`,扩展名 + 文件头双确认。**
`RomPlatform { FC, GB, GBC }`;判定顺序:扩展名(.nes/.gb/.gbc)→ 文件头(iNES 头 / GB $100 入口指令 + $104–$133 Nintendo logo 48 字节序列;$143==0xC0 → GBC)。扩展名缺失时按头嗅探兜底。判定与头校验为 JVM 纯函数(输入 ByteArray,零 Android 依赖),导入与 zip 逐条目复用同一函数。备选「只看扩展名」被否:改名文件会以错误核心加载,黑屏体验差。

**决策 2:核心选择表驱动——`GameSession` 按 `RomPlatform` 查平台→核心库映射,编译链泛化为按核心构建。**
`build-core.sh` 重构:`build_core <name> <repo> <makefile> <out_so>` 通用函数,FCEUmm 与 Gambatte 各调一次,版本锁按核心独立(`CORE_COMMIT.lock` / `CORE_GAMBATTE_COMMIT.lock`);FCEUmm 构建参数原样保留(含响应文件链接方案),Gambatte 用同一 NDK 链与 `-Wl,--version-script` 模式。`GameSession` 核心库路径改为查表(`FC→libfceumm_libretro.so`、`GB/GBC→libgambatte_libretro.so`);Gambatte 无 BIOS 依赖(HLE 启动),`systemDir`/`saveDir` 沿用。备选「每平台独立 GameSession 子类」被否:差异只有核心路径一行,表驱动即可。

**决策 3:存档键平台化——`.gb/.gbc` 的 baseName 保留完整文件名,FC 历史布局零迁移。**
`SaveSlotStore.baseName`:FC(`.nes`)沿用现状剥后缀(历史文件原地沿用,零迁移契约不变);GB/GBC 保留扩展名(`宝可梦.gb` → 键 `宝可梦.gb`),槽位/SRAM/挂起档/缩略图/金手指存储键全部经此函数派生——跨平台同名游戏天然隔离。`RomLibrary.removeRom` 的前缀匹配同步按平台取键。备选「目录按平台分层」被否:FC 零迁移要求排除;备选「加平台前缀」被否:同理破坏历史文件。

**决策 4:能力边界在呈现层收口,协议与核心零改动。**
联机:`CreateRoomScreen` 选游戏列表过滤为 FC(数据源 `RomLibrary.listRoms` 增加平台过滤参数);协议层零改动——GB 游戏根本进不了房间。金手指:`GameMenuItems` 金手指项按平台呈现(仅 FC),`GameSession` 初始码注入与批应用对非 FC 跳过(与既有联机排除同构)。倒带/快进/手柄/音频延迟对 GB 自动生效(全部平台无关),无需任何代码。

**决策 5:GB 画面沿用既有缩放体系,零渲染改动。**
`videoInfo` 上报 160×144:NATIVE 档整数倍缩放(160×4=640 宽在 1440p 屏上居中)、4:3 与铺满两档按既有算法适用;最近邻绘制与 CRT 接入点不变。封面比例维持 FC 256:240(装饰性,Non-Goal)。

## Risks / Trade-offs

- [Gambatte 的 Makefile 在 Windows/NDK 下可能有 FCEUmm 未踩过的坑] → 同一响应文件链接方案兜底;编译期失败在任务内即刻暴露,不影响已发布的 FC 功能。
- [GB 采样率与 FC 不同(Gambatte 上报值)] → 采样率经核心 timing 上报,AudioTrack 构建已按上报值对齐,音频延迟三档字节数按采样率换算,链路通用。
- [zip 内混多平台条目] → 逐条目独立判定平台,互不干扰;失败提示口径与现有 zip 契约一致。
- [跨平台同名游戏的封面/分类共享] → 封面槽按展示名共享属装饰性共享,可接受;分类词典 FC 专属,GB 天然未分类(spec 既有行为)。
- [Gambatte 大 ROM(512KB+)序列化尺寸大于 FC] → 倒带缓冲 24MB 上限按字节自适应淘汰,GB 状态较大时覆盖时长缩短属可接受降级;真机实测记录于 tasks。
- [ GB 标志位 0xC0 之外的中间值(0x80 仅 CGB 特性)] → 统一按 GBC 呈现(平台标签差异仅装饰),不影响游玩正确性。

## Migration Plan

无数据迁移:FC 布局零迁移,GB 存档自首次使用即新键。回滚 = revert 提交(多出的 .so 不影响旧代码)。 Gambatte .so 入库随 jniLibs(gitignore 现状:构建产物不入库,由脚本再生成)。

## Open Questions

无。

## 实现附记(apply 期发现,2026-10-03)

真机管道级验证挖出三处 shim/构建层多平台欠账,均已修复:

1. **SONAME 回退硬编码**:extractNativeLibs=false 下全路径 dlopen 恒失败,shim 回退硬编码 `dlopen("libfceumm_libretro.so")`——GB 必然加载错核心。改为按传入路径基名解析。
2. **ROM 传递双模式**:Gambatte 声明 `need_fullpath=false`,要求前端读 ROM 进内存(`info.data/size`);shim 由纯路径模式改为按 `retro_get_system_info` 捕获的 need_fullpath 双模式。
3. **像素格式 RGB565**:Gambatte 输出 RGB565(2 字节/像素),shim 原无条件按 XRGB8888 读(越界读崩溃);SET_PIXEL_FORMAT 按格式记录状态,刷新回调按 2 字节步进转换;同时拒绝未知格式而非静默按 XRGB 处理。顺带捕获 SET_SAMPLE_RATE(常量 21)。

构建链修正:Gambatte 仓库名为 `libretro/gambatte-libretro`;C++ 核心链接需 `-static-libstdc++`(make LDFLAGS 与 rsp 双处),否则依赖 APK 未打包的 `libc++_shared.so`;修正后 FCEUmm 产物与历史版本字节一致。
