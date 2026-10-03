# Tasks

## 1. 平台识别与导入(纯逻辑先行)

- [x] 1.1 新建 `RomPlatform.kt`:`RomPlatform { FC, GB, GBC }` 判定纯函数(扩展名 → 文件头双确认:iNES 头 / GB $100 入口 + $104–$133 Nintendo logo;$143==0xC0 → GBC)与头校验函数;验证:单测覆盖 FC 合法/GB 合法/GBC 标志/伪 GB(缺 logo)/扩展名缺失嗅探

> 实现:头部为唯一真源——改名文件按其真实平台导入并归一扩展名(避免以错误核心加载黑屏);Nintendo logo 字节经 pandocs 查证后硬编码。$143 的 0x80(向下兼容)与 0xC0 统一按 GBC 呈现。

- [x] 1.2 `RomLibrary` 接入:列表过滤扩展 `.gb/.gbc`,单文件与 zip 逐条目按 `RomPlatform` 分派校验(校验消息按平台),`removeRom` 前缀匹配按平台键;验证:单测覆盖 GB/GBC 导入、混合平台 zip、伪 GB 拒绝
- [x] 1.3 `SaveSlotStore.baseName` 平台化(FC 剥 `.nes` 零迁移;GB/GBC 保留扩展名)及下游键(SRAM/挂起档/缩略图/金手指)核对;验证:单测覆盖跨平台同名不串档/FC 历史键不变

## 2. Gambatte 编译链

- [x] 2.1 `scripts/build-core.sh` 泛化为 `build_core <name> <repo> <makefile> <out_so>` 通用函数,FCEUmm 参数原样迁移;验证:重构后 FCEUmm 构建产物与现网一致(字节级或行为级)
- [x] 2.2 新增 Gambatte 构建(libretro-gambatte,独立 commit 锁,arm64 + x86_64 → `libgambatte_libretro.so`);验证:两 ABI 产物就位,`nm` 确认导出 `retro_init`/`retro_run` 等符号

## 3. 播放与呈现接线

- [x] 3.1 `GameSession` 按平台选核心(平台→核心库表驱动),初始金手指注入与非 FC 跳过(与联机排除同构);验证:编译 + 现有单测全绿
- [x] 3.2 呈现适配:`DetailScreen` 平台标签与格式文案按平台,`LibraryCommon` 列表行角标按平台(FC/GB/GBC);验证:编译 + 截图走查
- [x] 3.3 能力边界:`CreateRoomScreen` 选游戏列表过滤 FC;`GameMenuItems` 金手指项仅 FC 呈现;验证:编译 + 真机菜单走查(FC 有、GB 无)

## 4. 真机验收与回归

- [ ] 4.1 GB 端到端真机验收(spec 全场景):导入 .gb/.gbc(含混合 zip)、GB 游戏游玩(画面 160×144 缩放、声音)、即时存读档、倒带、快进、手柄、同名游戏不串档、FC 历史存档零迁移;验证:真机走查记录逐条对应 specs 场景

> 2026-10-03 进度(MuMu 云机 + 合成管道级 ROM):导入 .gb(GB 角标/占位封面)✓、Gambatte 加载+游玩(160×144 渲染、59.7fps)✓、GB 存档(pipe.gb.state0+缩略图)✓、首页 FC/GB 共存✓、JVM 级混合 zip/同名隔离/零迁移✓。**待实体 ROM 手测**:真实游戏画面、倒带/快进/手柄于 GB、.gbc 实文件。

> 实现期发现并修复三处 shim 层多平台欠账(详见 design 实现附记):①SONAME 回退硬编码 fceumm→改为路径基名;②Gambatte need_fullpath=false 要求前端读 ROM 进内存→shim 双模式;③Gambatte 输出 RGB565(原仅 XRGB8888)→刷新回调按格式转换;另 Gambatte av_info 上报采样率 0.0Hz,GB 音频速率失配(underrun 累积、音调偏低)为已知降级,修复留待后续(SET_SAMPLE_RATE 已捕获,核心侧动态对齐未做)。
- [ ] 4.2 性能与边界:GB 状态序列化尺寸与倒带缓冲覆盖时长实测(设计 Risks 记录口径)、大 ROM(512KB)游玩;验证:真机观察记录

> 待实体 ROM(合成 ROM 无真实游戏状态可测);GB 状态大于 FC 时 24MB 环形缓冲覆盖时长按比例缩短,行为契约不变。
- [x] 4.3 回归:FC 全链路(导入/游玩/存读档/联机建房列表仍含 FC)、release 构建冒烟;验证:真机走查记录

> 2026-10-03:FC 游玩回归(1942,fps 57-59)✓;建房列表仍含 FC 且排除 GB(截图)✓;release 构建安装后 GB 游戏 59-60fps 运行、零 FATAL ✓。
