# Proposal

## Why

确定性像素占位封面看不出游戏内容，用户明确需要真实封面。开独立 change 实现"手动导入 + 联网自动抓取"双通道封面，占位图降级为兜底。

## What Changes

- **封面优先级**：用户导入的封面 > 联网抓取的封面 > 确定性像素占位图（兜底，永不失败）
- **手动导入**：详情页/卡片菜单新增「更换封面」，系统文件选择器选图（SAF，零权限），按游戏名存入私有 `covers/` 目录
- **联网抓取**：申请 `INTERNET` 权限；接入 libretro 官方缩略图库（thumbnails.libretro.com，按规范化游戏名匹配 Named Boxes），命中后缓存到 `covers/`，失败静默回退占位图（不阻塞 UI）
- **封面加载器统一**：新增 `CoverImage` 组件替换三处 `PlaceholderCover` 调用点（首页卡片/列表缩略/详情 hero），内部按优先级解析
- **不包含**：封面裁剪编辑、多封面管理、按 CRC 精确匹配（中文汉化名命中率有限，注明）、删除单个封面

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `game-library`: 「封面墙网格」——封面来源从"仅确定性占位图"扩展为"导入/联网封面优先，占位图兜底"。

## Impact

- `AndroidManifest.xml`：新增 `INTERNET` 权限（首次引入网络）
- `:app` 数据层：`CoverStore`（covers 目录管理 + libretro 抓取，HttpURLConnection 零新依赖）；UI 层 `CoverImage` 组件与三处调用点
- 风险：中文名 ROM 与 libretro 英文库名匹配率有限——命中不了就回退占位图，用户始终可用手动导入兜底
