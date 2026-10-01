# Proposal

## Why

retro-ui-redesign 已完成全 app 改版，但品牌图形（logo、主机插画、装饰）仍是代码绘制的示意版本；用户已提供 GPT 生成的"吹卡带"UI 素材库（RGBA 透明底整图，1536×1024），视觉质量明显高于代码示意。本次把素材库中能对位的美术接入现有页面，让品牌观感一次到位。

## What Changes

- **切片接入**（大件美术，从素材库整图切片为打包 PNG，`res/drawable-nodpi/`）：吹卡带 logo 组合与副标徽章（启动页）、FC 主机插画（启动页，替换 Canvas 绘制）、红色云纹横幅条（启动页底部与游戏屏横幅）、像素场景条（游戏库空状态）、「开始游戏」像素描边按钮（详情页 CTA，文字与现有文案完全一致）、手柄套件图（按键设置页预览面板，替换 Canvas）
- **不接入**：素材库中烤死文字且与现有文案不符的控件（对话框、收藏/下载按钮）、图标集（现有 Material 图标语义可用）、分类 chips 行（体育分类与现有 6 分类集合不符）
- **游戏屏手柄视觉适配（追加）**：十字键 / A/B / SELECT-START 按素材手柄实测色值重制样式（面板 #2C2C2C、十字键 #373737 凹槽圆 + 连续十字、A/B #F22C2E 立体红 + 按压下沉），交互逻辑与命中区不变
- **不改任何行为**：所有交互、文案、布局结构保持现状；spec 层仅「品牌启动页」的装饰来源约束从"代码绘制"放宽为"随应用打包的美术资源"

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-shell`: 「品牌启动页」需求中"装饰元素 SHALL 代码绘制（无位图美术资源依赖）"改为"品牌图形与装饰 SHALL 来自随应用打包的美术资源（无网络依赖）"——启动页改用切片素材呈现。

## Impact

- `:app` 资源：新增 `res/drawable-nodpi/*.png`（8 张左右切片，总量 < 300KB）
- `:app` UI：`SplashScreen`（logo/主机/横幅替换 Canvas）、`LibraryCommon` 空状态（像素场景条）、`DetailScreen`（CTA 换素材按钮）、`KeyMappingScreen`（预览面板换素材图）、`GameScreen`（横幅底图）
- 无新依赖；无行为变更；已验证素材整图为透明底 RGBA、切片边缘干净
