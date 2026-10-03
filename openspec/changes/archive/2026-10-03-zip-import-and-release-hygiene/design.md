# Design

## Context

当前实现(见 proposal.md - Why):

- 导入:`RomLibrary.importRom(context, uri)` 单入口——复制到 `filesDir/roms/<safe>.nes` 后做 iNES 头校验(`NES\x1a`),非法即删;选择器仅以 `application/octet-stream` 拉起(`HomeScreen` 两处)。导入结果为密封类型 `Ok / Invalid / Error`,`HomeScreen` 是唯一调用方,改动签名无兼容负担。
- 私有存储布局(全部在 `filesDir/` 下):`roms/`(ROM 库)、`romsaves/`(即时存档状态 + 缩略图 + SRAM 电池存档 `<名>.srm`)、`covers/`(导入/联网封面)、`covershots/`(运行截图)、`system/`(核心系统目录)。
- 构建:`isMinifyEnabled = false`、无签名配置(release 产物未签名)、`versionCode = 1`。
- JNI:shim.c 经 `NewGlobalRef` 持有 `LibretroCore` 对象,以 `GetMethodID(..., "getInputMaskFromNative", "(I)I")` 回调 Kotlin;Kotlin→C 的 `external fun` 符号名由默认 ProGuard 规则(`-keepclasseswithmembernames class * { native <methods>; }`)保护,但 **C→Kotlin 的回调方法名无任何规则保护**。

## Goals / Non-Goals

**Goals:**

- zip 导入:单入口分派、流式解包、批量导入、逐条目校验,纯逻辑可单测。
- 备份规则:API 26–30 与 API 31+ 两套规则并存且语义一致。
- release 加固:R8 + 资源收缩 + 签名,本地无正式密钥也能 `assembleRelease` 恒成功。
- 版本号维护约定成文。

**Non-Goals:**

- 7z/rar 等其他压缩格式;zip 内嵌套 zip 的递归解包。
- 音频/输入延迟优化、倒带、物理手柄、多平台核心(后续 change)。
- 云同步、多设备存档漫游。
- 本次不递增 versionCode/versionName(首次对外分发时再动,见决策 5)。

## Decisions

**决策 1:zip 导入走 `RomLibrary.importRom` 单入口分派,不新增 UI 入口。**
以所选文件 display name 是否以 `.zip` 结尾分派:否 → 现有单文件分支原样保留;是 → zip 分支用 `java.util.zip.ZipInputStream` 流式遍历:目录与非 `.nes` 条目跳过,逐条目读入内存并复用 iNES 校验后落盘。选择 zip 即兼容「多 ROM 批量」与「单 ROM」两种情形,不引入第二个导入入口。备选「独立『导入压缩包』按钮」被否:多一个入口多一份引导成本,且用户不关心文件形态。
防御:单条目解压上限 8 MB(NES ROM 实际上限约 1 MB,超限视为异常条目跳过),防止 zip 炸弹撑爆内存;条目名沿用现有 `safe` 规则清洗;同名文件沿用现有覆盖语义(与单文件导入一致,不弹确认)。
`ImportResult` 演进:`Ok(imported: Int)`(反馈「已导入 N 个游戏」,N=1 时维持现状静默刷新)、`Invalid(message)`(裸文件仍报「不是有效的 FC ROM」,zip 报「压缩包内没有有效的 FC ROM」)、`Error(reason)` 不变。加密 zip(无解密支持)读出垃圾 → iNES 校验失败 → 计入跳过,整体表现为「无有效 ROM」,可接受。

**决策 2:选择器 MIME 数组扩展为 `application/octet-stream + application/zip + application/x-zip-compressed`。**
`ActivityResultContracts.OpenDocument` 将整个数组塞进 `EXTRA_MIME_TYPES`,文档选择器按任一类型过滤;octet-stream 兜底部分厂商把 zip 标为未知类型的情况。备选 `*/*`(全类型)被否:浏览页噪音太大;备选自定义 Contract 加持 `application/x-zip-compressed` 以外类型不值得复杂度。

**决策 3:两套备份规则并存,云备份排除文件域、设备迁移只留存档。**
`AndroidManifest` 同时声明 `android:fullBackupContent="@xml/backup_rules"`(API 26–30)与 `android:dataExtractionRules="@xml/data_extraction_rules"`(API 31+),`allowBackup` 保持 true(关闭全局开关会连设备迁移一起失去)。
- 云备份:两个规则文件均排除整个 `file` 域(roms/romsaves/covers/covershots/system 一并排除)——版权 ROM 不上云、25MB 配额不再构成风险;SharedPreferences(设置类偏好)保留进云备份,体积可忽略。
- 设备迁移(API 31+ `device-transfer` 段):仅 include `file` 域的 `romsaves/`(即时存档 + 缩略图 + SRAM 同在此目录),仍排除 `roms/`(版权 ROM 迁移慢且有法律敏感性,用户在新机重新导入)。
「迁移保留存档」在 API 26–30 设备上依赖云备份排除前的旧行为不可得——接受:Android 11 以下份额已小,契约按 API 31+ 表述(spec 场景即如此书写)。

**决策 4:R8 全开 + 一条显式 keep,签名经不入库的 `keystore.properties`,缺失回退 debug。**
- `isMinifyEnabled = true` + `isShrinkResources = true` + `proguard-android-optimize.txt`;新增 `app/proguard-rules.pro` 保留被 C 层回调的方法:`-keepclassmembers class com.huffcart.core.libretro.LibretroCore { private int getInputMaskFromNative(int); }`(类名本身可混淆,`GetObjectClass` 按实例取类;但方法签名必须原名)。资源引用全为静态 R 类,收缩风险低。
- 签名:构建脚本尝试读根目录 `keystore.properties`(storeFile/storePassword/keyAlias/keyPassword),存在则注册 release signingConfig,不存在则 `signingConfig = debug`——本地与 CI 的 `assembleRelease` 恒产出可安装 APK。`.gitignore` 增加 `keystore.properties`。备选「密钥入库」被否(凭据泄漏);备选「缺密钥直接构建失败」被否:违反「本地恒可用」目标,且正式分发前放置凭据文件即可。
- 已知语义:debug 回退签名的产物与正式签名产物签名不同,覆盖安装会失败——正式分发一旦开始,更换签名即意味着用户重装丢档,见 Risks。

**决策 5:版本号约定成文,本次不动值。**
约定:每次对外分发递增 `versionCode`(整数 +1),`versionName` 采用 `0.x.y`(破坏性语义变更升 y→x)。本次变更尚未对外分发,保持 `1 / 0.1.0`,首次分发时一并落值。

## Risks / Trade-offs

- [R8 收缩误裁运行时反射/接口] → keep 规则覆盖已知 JNI 回调;tasks 含 release 构建真机冒烟(启动/导入/游玩/存读档/联机)兜底;出现缺类时按崩溃栈补规则。
- [厂商文件管理器 zip MIME 不规范,选择器里选不到 zip] → octet-stream 兜底已在数组中;真机验证任务覆盖主流文件管理器;仍选不到时用户可把文件改扩展名,属极端路径。
- [zip 炸弹/超大压缩包] → 单条目 8MB 上限 + 流式处理,内存峰值单条目级。
- [备份规则写错导致应留的不留] → 规则文件以官方语法最小面书写(整域排除而非逐目录枚举,漏项面最小);迁移 include 单目录一并用真机换机流程验证一次。
- [debug 回退签名与未来正式签名不一致,覆盖安装失败重装丢档] → 约定「首次正式分发前定稿签名」并写入决策 5;发布流程任务中显式校验签名指纹一致。
- [SRAM 中途写盘遇 R8/收缩无关的时序问题] → 本 change 不触碰写盘逻辑,无新增风险。

## Migration Plan

无数据迁移、无破坏性变更:新 APK 覆盖安装即可。备份规则自安装起生效;已上云的旧备份不回收(自动被新备份覆盖)。回滚 = 还原构建配置与规则文件,无残留。

## Open Questions

无。
