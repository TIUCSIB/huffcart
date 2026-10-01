# Tasks

## 1. 素材切片

- [x] 1.1 脚本切片 8 件素材到 `app/src/main/res/drawable-nodpi/`（logo/logo_sub/console/banner_red/landscape/btn_start/gamepad/cartridge），逐件目测边缘与内容完整性；验证：文件存在且尺寸合理，总量 < 300KB

## 2. 接入

- [x] 2.1 启动页换素材：logo 组合 + 副标徽章 + 主机插画 + 底部云纹横幅条，删除 `drawConsole`/`drawBricks` Canvas 代码；验证：构建通过，模拟器截图启动页观感达标
- [x] 2.2 空状态加像素场景条（`LibraryCommon.EmptyLibrary`）；验证：空库页截图出现场景装饰
- [x] 2.3 详情页 CTA 换「开始游戏」素材按钮（可点击 Image + contentDescription）；验证：详情页截图按钮为像素描边样式，点击仍可进游戏
- [x] 2.4 按键设置页预览面板换手柄套件图（删 `PadPreview` Canvas）；验证：页面截图预览为素材图
- [x] 2.5 游戏屏横幅底图换云纹条（保留 FAMILY COMPUTER 标签）；验证：游戏屏截图横幅样式更新，存/读/快进 chips 不受影响

## 3. 收尾

- [x] 3.1 构建 + 单测 + 模拟器整链截图复验（启动页/空状态/详情/按键设置/游戏屏）；验证：全部通过，无回归

## 4. 游戏屏手柄视觉适配（追加）

- [x] 4.1 色板对齐素材实测值（面板/凹槽/十字键/A-B 红/胶囊），更新 Color.kt 并清旧常量；验证：grep 无旧色残留，构建通过
- [x] 4.2 `PadControls` 重制：十字键凹槽圆 + 连续十字底座（命中几何不变）、A/B 立体底影与按压下沉、胶囊描边；验证：模拟器截图常态与按住态
- [x] 4.3 横屏半透明浮层同步新样式（同一组件自动生效）；验证：代码复用确认，旋转复验同前待真机
