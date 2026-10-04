# release-packaging Specification

## Purpose

约束应用的打包与数据备份行为：游戏 ROM、存档与封面不进入云端自动备份，换机时存档经设备迁移保留；release 构建产物可安装、可运行且已签名。它是分发前"最后一公里"的质量契约。

## Requirements

### Requirement: 云备份排除
应用的云端自动备份 SHALL 排除游戏 ROM、即时存档状态、SRAM 电池存档与封面图像；游戏运行数据 SHALL 不上云。设置类偏好（键映射、声音、画面、金手指开关等）不受本约束限制。

#### Scenario: 云备份不含游戏数据
- **WHEN** 系统执行云端自动备份
- **THEN** 备份内容不包含任何 ROM 文件、存档状态文件与封面图像文件，备份不因应用数据体积超限而失败

### Requirement: 设备迁移保留存档
通过设备到设备迁移换机后，应用的即时存档与 SRAM 电池存档 SHALL 保留并可正常读档继续游玩；ROM 本体 SHALL 不经迁移复制（用户在新设备重新导入）。

#### Scenario: 换机后存档仍在
- **WHEN** 用户在旧设备存档后经设备到设备迁移换机并在新设备打开应用
- **THEN** 存档管理中可见原存档（槽位缩略图与时间戳保持），且不会因迁移产生体积超限或失败

### Requirement: release 产物可用
release 构建 SHALL 产出可直接安装运行的 APK；开启代码与资源收缩后，启动、游戏库、导入、游玩、存读档与联机在 release 构建下 SHALL 与 debug 构建行为一致。

#### Scenario: release 构建冒烟
- **WHEN** 将 release 构建产物安装到真机并依次执行导入 ROM、启动游戏、存档读档
- **THEN** 全部操作正常，与 debug 构建行为一致，无因代码收缩导致的崩溃

### Requirement: 发布签名
release 构建 SHALL 使用项目签名密钥产出已签名 APK；签名凭据（密钥库与口令）SHALL 不进入版本库；在未配置正式密钥的环境构建 release 时 SHALL 回退调试密钥完成签名，构建不中断。

#### Scenario: 无正式密钥环境构建
- **WHEN** 在未放置签名凭据文件的机器上执行 release 构建
- **THEN** 构建成功产出已签名的 APK，可安装到设备

#### Scenario: 签名凭据不入库
- **WHEN** 检查版本库内容
- **THEN** 签名密钥库文件与口令文件均不在版本库中

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
