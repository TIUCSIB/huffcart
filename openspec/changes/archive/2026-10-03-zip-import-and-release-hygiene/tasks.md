# Tasks

## 1. zip 压缩包导入

- [x] 1.1 `RomLibrary` 抽出 iNES 头校验公共函数,单文件导入行为回归不变(现有测试与手测口径),验证:`app/src/test` 全绿
- [x] 1.2 实现 zip 分支:`ZipInputStream` 流式遍历,跳过目录/非 `.nes` 条目,单条目 8MB 上限,条目名沿用 `safe` 清洗与同名覆盖语义;验证:新建单测覆盖单 ROM / 多 ROM / 无有效 ROM / 损坏 zip / 超 8MB 条目五类输入
- [x] 1.3 `ImportResult` 演进为 `Ok(imported: Int)` / `Invalid(message)` / `Error(reason)`,`HomeScreen` 两处调用点反馈文案跟进(「已导入 N 个游戏」/「压缩包内没有有效的 FC ROM」);验证:单测 + 空库页导入入口手测
- [x] 1.4 `HomeScreen` 两处 `picker.launch` MIME 数组扩展为 `application/octet-stream + application/zip + application/x-zip-compressed`;验证:真机文件管理器可见并可选 zip

## 2. 备份与设备迁移规则

- [x] 2.1 新增 `res/xml/backup_rules.xml`(API 26–30:排除 `file` 域全部)并在 `AndroidManifest` 声明 `android:fullBackupContent`;验证:manifest 合并产物含该属性
- [x] 2.2 新增 `res/xml/data_extraction_rules.xml`(API 31+:cloud-backup 排除 `file` 域全部;device-transfer 仅 include `romsaves/`)并声明 `android:dataExtractionRules`;验证:manifest 合并产物含该属性
- [ ] 2.3 真机验证(可并入 3.x 冒烟):换机迁移流程后 `romsaves/` 存档可读档;验证:adb 或实机迁移记录写入 tasks 勾选备注

> 2026-10-03 进度:规则文件与 manifest 声明已验证(合并产物含三属性);换机迁移需两台实机,待用户手测。

## 3. release 构建加固

- [x] 3.1 `app/build.gradle.kts` release 块开启 `isMinifyEnabled` + `isShrinkResources` + `proguard-android-optimize.txt`,新增 `app/proguard-rules.pro` 保留 `LibretroCore.getInputMaskFromNative` 回调;验证:debug 与 release 单测全绿,`assembleRelease` 成功
- [x] 3.2 签名接入:构建脚本读根目录 `keystore.properties`(存在则注册 release signingConfig,缺失回退 debug),`.gitignore` 增加 `keystore.properties`,新增 `keystore.properties.example` 样例;验证:无凭据机器 `assembleRelease` 产出已签名可安装 APK,`git status` 不含凭据文件
- [ ] 3.3 release 真机冒烟(spec「release 产物可用」):安装 release 产物,依次执行启动、导入 zip、游玩、存档读档、建房联机;验证:全部与 debug 行为一致,无缺类崩溃

> 2026-10-03 进度:真机(192.168.31.41)已验证——安装成功、首页/详情/游戏运行(1942)、START 长按输入生效(即 JNI 回调 `getInputMaskFromNative` 在 R8 后存活)、logcat 无 FATAL;**导入 zip(SAF 交互)、存读档、建房联机待用户手测**。
- [x] 3.4 design 决策 5 版本号约定复核后即视为成文(首次对外分发时递增 versionCode);验证:design.md 决策 5 与发布约定一致,本次无版本号改动
