# Design

## Context

当前封面全部为 `CoverPattern` 确定性占位图（无网络、零位图依赖是当时约束）。用户需要真实封面；素材库时代的"无网络"约束由本 change 显式解除（仅限封面抓取路径）。

## Goals / Non-Goals

**Goals:**

- 双通道封面：SAF 手动导入（确定性可用）+ libretro 缩略图库自动抓取（尽力匹配）
- 三处调用点统一走 `CoverImage` 优先级解析组件；占位图永远兜底
- 零新依赖：抓取用 `HttpURLConnection`，图片解码用 `BitmapFactory`

**Non-Goals:**

- 不做封面裁剪/编辑、不做 CRC 精确匹配、不引入 Coil/Glide 等图片库
- 不做封面管理界面（删除 = 清除缓存目录整体）

## Decisions

1. **封面存储**：`filesDir/covers/<规范化名>.png`；`CoverStore` 单对象管理（存在性检查、保存、libretro 抓取）。规范化复用 `GenreCatalog.normalize`。
2. **libretro 图源**（实施期核实：Named_Boxarts / Named_Snaps 均为平铺完整发布名、无首字母子目录，原设想的 `<首字母>/<规范化名>.png` 直拼不可行）：先抓 `Named_Boxarts/` 目录索引 HTML（13418 文件 / 2867 唯一键，实测图源限速 ~20KB/s、单页数 MB 需分钟级），解析出「裸标题规范化名 → 精确文件名」映射（裸标题 = 发布名去扩展去标记后的部分，规范化复用 `GenreCatalog.normalize`；同名 dump 变体 [b]/[h] 优先干净版），命中后精确抓取该文件，未命中再试 Named_Snaps。超时：连接 5s、索引读 20s（逐块到达不断流）、图片读 15s。索引进程内缓存 + `cacheDir` 落盘缓存（TTL 7 天），避免每次冷启动重拉。英文名直接命中，中文汉化名经 `CoverAliases` 别名表命中，都未命中 → 静默回退。
3. **CoverImage 组件**：`mutableStateOf<Bitmap?>` 初始同步读本地封面（导入或既往抓取落盘，同一槽位 `covers/<规范化名>.png`）→ 无则异步抓取（抓取 Job 按规范化名内存去重）→ 均无则渲染 `PlaceholderCover`。三处调用点（首页卡片/列表缩略/详情 hero）仅换组件名；`coverEpoch` 计数器驱动导入后跨屏刷新。
4. **入口**：首页卡片 ⋮ 菜单与详情页菜单各加「更换封面」（复用 `OpenDocument`，`image/*`）。
5. **名称映射表 + 预抓取**（应用户"全部换上真实封面"要求，落地风险项预留方案）：`CoverAliases` 别名表（中文规范化名 → 英文发布名规范化名，~85 条官方经典；每条值均已对照真实索引核验存在，hack/中文原创不入表防错配）；`CoverStore.prefetchAll` 在库刷新后限流（并发 6）后台逐个补抓，封面落盘持久，期间界面即时占位图不受影响。
6. **运行截图封面**（应用户提议，参考 bobotouo/game-emulator 的自动缩略图思路）：huffcart 渲染栈每帧已把核心视频缓冲写入原生分辨率 Bitmap，无需 PixelCopy——`GameSession` 开局且无任何封面时，第 ~480 帧（≈8s，避开开机黑屏）复制一帧交 IO 线程压缩为 `covershots/<规范化名>.png`，游戏线程仅付一次位图拷贝。独立槽位不干扰导入/联网的覆盖语义；显示优先级变为 导入 > 联网 > 截图 > 占位图（`CoverResolver` 四级）。

## Risks / Trade-offs

- [中文 ROM 名匹配率低] → 手动导入兜底 + 抓取失败静默；后续可加"名称映射表"
- [首屏异步抓取的闪烁] → 占位图先渲染，封面到达后替换（内容渐显即可，不做动画）
- [INTERNET 权限为首次引入] → 仅封面抓取使用，无遥测；设计明示

## Migration Plan

无数据迁移；`covers/` 目录按需创建。回滚 = revert（权限与目录可保留无害）。

## Open Questions

（无——图源与回退策略已定；命中率属可调参数不阻塞实施。）
