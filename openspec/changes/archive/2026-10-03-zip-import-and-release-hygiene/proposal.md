# Proposal

## Why

用户拿到的 ROM 几乎都是 zip 压缩包,而当前导入只认裸 `.nes`,必须先自行解压,首次体验断点明显。同时 release 构建目前是裸的:无混淆收缩、无签名配置、无备份规则——`filesDir` 下的 ROM 库、存档与封面会被 Android 自动云备份,既容易吃满 25MB 配额,也不应让版权 ROM 与存档上云;对外分发需要一个可安装、可运行的 release 产物基线。

## What Changes

- **zip 压缩包导入**:文件选择器可选中 `.zip`;导入时解包其中全部合法 iNES ROM(含多 ROM 批量导入),非 ROM 条目静默跳过,压缩包内无一合法 ROM 时给出明确提示;裸 `.nes` 导入行为保持不变。
- **备份与迁移规则**:云备份(Auto Backup)排除 ROM、存档、封面目录;设备到设备迁移(换机)保留即时存档与 SRAM。
- **release 构建加固**:开启 R8 混淆与资源收缩,并补 JNI 回调 keep 规则(原生层经 `GetMethodID` 回调 `LibretroCore.getInputMaskFromNative`);签名经不入库的 `keystore.properties` 配置,缺省回退 debug 密钥保证本地 `assembleRelease` 恒可用;明确版本号(versionCode/versionName)维护约定。

## Capabilities

### New Capabilities

- `release-packaging`: 应用打包与数据备份契约——云备份/设备迁移的目录范围、release 产物可安装可运行、签名与版本管理约定。

### Modified Capabilities

- `game-playback`: 「ROM 导入」需求扩展——除裸 `.nes` 外接受 zip 压缩包并解包导入,批量与失败提示行为入契约。

## Impact

- `app/src/main/java/com/huffcart/app/ui/library/RomLibrary.kt`:导入逻辑扩展 zip 分支(纯逻辑可单测)。
- `app/src/main/java/com/huffcart/app/ui/screens/HomeScreen.kt`:选择器 MIME 扩展(`application/zip` 等)与导入结果反馈。
- `app/build.gradle.kts` + 新增 `app/proguard-rules.pro`、`keystore.properties`(gitignore)+ `.gitignore`。
- `app/src/main/AndroidManifest.xml` + 新增 `app/src/main/res/xml/backup_rules.xml`、`data_extraction_rules.xml`。
- 不涉及核心播放、联机、存档槽位等运行时行为的改动。
