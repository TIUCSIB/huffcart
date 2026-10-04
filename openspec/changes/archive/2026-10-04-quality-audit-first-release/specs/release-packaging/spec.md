# Spec Delta

## ADDED Requirements

### Requirement: GitHub 首发发布
项目 SHALL 发布到 GitHub 远端仓库:推送 main 分支、创建首版 tag `v0.1.0`、发布 GitHub Release 并附中文 changelog 与已签名 release APK;Release 资产 SHALL 可下载,且与该 tag 源码的构建一致;发布 SHALL 在仓库内容复核(无 ROM、无签名凭据、无 local.properties 入库)之后进行。

#### Scenario: Release 就绪
- **WHEN** 发布流程完成
- **THEN** GitHub 上存在 tag `v0.1.0` 与对应 Release,Release 页含中文 changelog 与可下载的已签名 APK,且该 APK 与 tag 源码构建产物一致

#### Scenario: 远端历史干净
- **WHEN** 检查远端仓库 main 分支的全部入库文件
- **THEN** 不包含任何 ROM 文件、签名密钥库、口令文件与 local.properties

### Requirement: 公开分发产物无版权内容
公开发布的 APK SHALL 不包含内置 ROM(assets/roms 为空);内置 ROM 版构建 SHALL 仅限本地使用,SHALL 不作为 Release 资产上传,SHALL 不出现在公开分发渠道。

#### Scenario: 公开 APK 为干净构建
- **WHEN** 解包用于公开发布的 release APK 并检查其 assets
- **THEN** 不含任何 .nes/.gb/.gbc ROM 资源文件

#### Scenario: 上传资产不含 ROM 版
- **WHEN** 创建 GitHub Release 并上传资产
- **THEN** 上传清单中不存在含内置 ROM 的 APK
