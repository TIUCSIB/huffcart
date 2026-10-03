# Proposal

## Why

吹卡带目前只能玩 FC:libretro 架构的"万能插座"红利尚未兑现——播放层(画面/声音/输入/存档/倒带/快进/手柄)全部平台无关,真正绑定主机的只有核心库。接入 Gambatte(GB/GBC 公认高精度核心)即可让游戏库扩展出 Game Boy 平台,覆盖宝可梦、塞尔达梦见岛、超级马里奥大陆等怀旧需求第二梯队。

## What Changes

- **Gambatte 核心接入**:核心编译脚本泛化为按核心构建(repo/产物名/版本锁各自独立),产出 `libgambatte_libretro.so`(arm64 + x86_64)。
- **平台识别**:新增平台维度(FC / GB / GBC),由扩展名与文件头判定(iNES 头 vs GB 的 Nintendo logo + CGB 标志);导入(含 zip 批量)按平台分派校验,游戏库可见 .gb/.gbc。
- **播放适配**:游戏会话按平台选择核心;画面缩放已由核心上报分辨率驱动(160×144 三档缩放天然适用);存档键平台化,FC 历史布局零迁移,跨平台同名游戏互不串档。
- **库呈现适配**:详情页平台标签(FC/GB/GBC)与格式文案按平台;分类筛选沿用内置 FC 词典(GB 未命中词典的既有"仅出现在全部"行为直接适用)。
- **能力边界(v1)**:联机建房选游戏仅列 FC(联机协议按 FC 席位设计);金手指入口仅 FC 呈现(Gambatte 作弊契约未验证);GB 内置播种不加;GB 联机(串联线)不在范围。

## Capabilities

### New Capabilities

（无——多平台是既有能力域上的维度扩展:导入/播放归 game-playback,库呈现归 game-library,联机边界归 netplay,金手指边界归 cheats,不另立能力。）

### Modified Capabilities

- `game-playback`: 「ROM 导入」扩展 .gb/.gbc 与按平台头部校验;「核心加载与运行」按平台选择核心;新增「跨平台数据隔离」需求(存档槽/SRAM/挂起档按平台隔离,FC 历史布局零迁移)。
- `game-library`: 「游戏详情页」平台标签与格式文案按平台(FC/GB/GBC)。
- `netplay`: 「房间创建与发现」建房选游戏仅列出 FC 游戏。
- `cheats`: 「金手指入口」仅对 FC 游戏呈现,非 FC 不注入任何码。

## Impact

- `scripts/build-core.sh`:泛化为多核心构建(新增 gambatte 目标与独立版本锁)。
- 新增 `app/src/main/java/com/huffcart/app/ui/game/RomPlatform.kt`(平台判定纯逻辑,JVM 可测)。
- `RomLibrary.kt`(扩展名/头校验/列表过滤)、`SaveSlotStore.kt`(baseName 平台化)、`GameSession.kt`(核心选择)、`RomLibrary.removeRom`(前缀匹配扩展)。
- `DetailScreen.kt` / `LibraryCommon.kt`(平台标签与格式文案)、`CreateRoomScreen.kt`(建房选游戏过滤 FC)、`GameScreen.kt` 菜单(金手指入口按平台)。
- `core-native` / `core-bridge` / netplay 协议 / 倒带 / 快进 / 手柄 / 音频延迟:零改动(全部平台无关)。
