# Tasks

## 1. 摇杆核心逻辑（纯函数 + 设置存储）

- [x] 1.1 新增 `app/src/main/java/com/huffcart/app/ui/game/JoystickMath.kt`：归一化偏移 → `Set<RetroButton>` 纯函数，死区 0.25 / 激活后退出阈值 0.20、8 扇区 ±22.5°（当前方向外扩 5° 迟滞），参数集中为顶部常量；验证：单测覆盖死区内外、4 正向、4 斜向、边界抖动不换向、激活后低于退出阈值复位
- [x] 1.2 新增 `app/src/test/java/com/huffcart/app/ui/game/JoystickMathTest.kt` 上述用例并跑通；验证：`./gradlew :app:testDebugUnitTest --tests "*JoystickMath*"` 通过（注意：须从 subst 盘 `X:/` 运行，中文路径会使 test worker 类加载失败——已踩坑，与 pad-feel 记录一致）
- [x] 1.3 新增 `app/src/main/java/com/huffcart/app/ui/game/ControlSchemeStore.kt`：`enum ControlScheme { DPAD, JOYSTICK }`，SharedPreferences（键 `virtual_pad_style`，默认 `DPAD`），`load/save`，注释说明沿用 KeyMappingStore 轻量约定；验证：编译通过，save→load 往返语义经人工核对（SharedPreferences 依赖 Android 环境，不做 Robolectric 单测）

## 2. 摇杆控件（视觉 + 手势）

- [x] 2.1 `PadControls.kt` 新增 `JoystickControl(size, translucent, onButton)`：Canvas 底座环（DpadRecess 凹槽 + 细描边）与摇杆帽（径向渐变深灰球 + 高光），推向方向时帽缘对应侧红色高亮（HcPadRed）；手势 `awaitEachGesture` 按住追踪首指针位移（帽跟手即时、最大偏移 0.55R），集合变化按差集「先抬旧后按新」发回调，松手弹簧回中（MediumBouncy）+ 首次按下触觉反馈一次；验证：编译通过
- [x] 2.2 横屏浮层验证（随归档由用户确认关闭；真机复核留待横屏使用时进行）：临时将 `PadControlsOverlay` 十字键替换为 `JoystickControl(translucent = true)` 试运行后还原（真实接线在 3.2）；验证：MuMu 横屏截图，半透明摇杆可见、推杆跟手（备注：MuMu Nx 实例强制竖屏，user_rotation/cmd window 均被覆盖，横屏留待用户在真机或模拟器工具栏旋转后复核；代码路径与竖屏同一组件仅布局参数不同）

## 3. 形态切换接线

- [x] 3.1 `SkinPadPanel` 增加 `scheme` 参数：`DPAD` 走现有十字键精灵；`JOYSTICK` 不画 `skin_dpad` 精灵、同孔位放 `JoystickControl`（底座不透明深灰圆盘，直径 ≥ 十字包络 0.63 × 精灵区，完全遮蔽挖孔），B/A/SELECT/START 精灵不动；验证：编译通过 + MuMu 竖屏截图两形态各一张，摇杆观感无挖孔穿帮
- [x] 3.2 `PadControlsOverlay` 增加 `scheme` 参数：`JOYSTICK` 时左侧原十字键位置放 `JoystickControl(translucent = true)`，`DPAD` 保持现状；`GameScreen` 进入时 `ControlSchemeStore.load()` 并传入竖屏 `ControlPanel` 与横屏浮层两处；验证：MuMu 分别以两设置值冷启动进游戏，竖屏/横屏呈现与设置一致
- [x] 3.3 `KeyMappingScreen` 顶部（映射行上方）加「虚拟手柄样式」行：显示当前值（十字键 / 摇杆），点按弹单选 AlertDialog（复用「关于」对话框模式），选完即 `ControlSchemeStore.save` 并刷新行文本；验证：MuMu 切换后退出应用重进设置页，选择保持
- [x] 3.4 手感联调（随归档由用户确认关闭；五六轮实机反馈迭代已覆盖手感调优，常量集中于 JoystickMath）：JoystickMath 常量按实机试玩微调（超级玛丽走位/斜向、坦克大战转向）；验证：MuMu 实机试玩，斜向射击（魂斗罗式）与滑动换向无抖动、无迟钝感（备注：adb 手指无法替代真手感——已验证方向响应/滑动换向/死区无抖动（1944 配点菜单光标移动、探针 mask 连续送达），最终手感与常量微调留给用户实机试玩，参数集中在 JoystickMath 顶部）

## 5. 二轮：十字键斜向 + 皮肤切片按压双圈修复（用户实测反馈）

- [x] 5.1 十字键改代码绘制（竖屏皮肤面板 + 横屏浮层共用同一手势）：凹槽圆固定盖住底图挖孔（切片把凹槽环烘焙进了精灵图，按压位移会错位出双圈——合成图已复现），十字/箭头朝按压方向弹性偏移 + 半臂红色高亮；手势改触点角度判向（`JoystickMath` 8 向：死区+迟滞），支持斜向组合（斜向双半臂同亮）；验证：编译 + `JoystickMath` 单测通过
- [x] 5.2 SELECT/START 改代码绘制胶囊：盖满挖孔窗口，按压在窗口内下沉 5dp（上缘露孔内暗部、下缘被窗口裁剪、不再与挖孔错位）+ 按下红；B/A 保持素材精灵；验证：编译通过
- [x] 5.3 实机验收：斜向按压双半臂同亮截图、胶囊下沉无双圈、十字键凹槽固定；验证：MuMu 截图（1942 游戏屏：怠速面板无接缝、UR 斜向拖动上/右双半臂同亮且十字对角前倾、SELECT 下沉露孔变红而挖孔环贴合） + `openspec validate` 通过

## 6. 三轮：控制面板弃底图、全代码绘制（用户反馈「突兀」）

- [x] 6.1 `SkinPadPanel` → `GamepadPanel`：深色圆角面板底（Canvas）+ 复用 DpadControl / JoystickControl / AbButtons / MenuPills 按面板高度比例布局；SkinSprite / SkinPill / SkinDpadDirection 删除，skin_*.png 不再被面板引用（素材文件保留）；A/B 新增随面板高度缩放的尺寸参数，胶囊文字改像素字体（横竖屏一致）；验证：编译 + 单测通过
- [x] 6.2 `GameScreen` ControlPanel 去掉方形底色（面板自带圆角，黑底上圆角可见）；验证：MuMu 竖屏截图（十字键/摇杆两模式）——面板整体协调无混排突兀感；另修复十字键/摇杆方形命中角落拦截胶囊左上角的问题（控件命中 clip 成圆形 + 十字键 0.95h→0.82h、胶囊右移 0.06h）

## 7. 四轮：真机反馈调优

- [x] 7.1 十字键点按无响应修复：手势 down 落点即判定（快速点按无 move 事件也生效）+ 十字键专用死区 0.12/0.10（`JoystickMath.directions` 加参数）；验证：编译 + JoystickMath 单测
- [x] 7.2 十字键高亮改臂端短块（env/4）+ 下压加深至 4.5%；A/B 球体加高光/深影/沉入（depth 随尺寸缩放）；胶囊加投影/顶光/下沉 3dp；验证：编译
- [x] 7.3 布局：面板加高 386/200、十字键/A-B 0.86h/0.38h、胶囊 78×30 间距 18dp 并移至右下（B/A 正下方）；验证：真机（RMX5060）+ MuMu 截图

## 8. 五轮：真机反馈微调

- [x] 8.1 面板加高 386/215；SELECT/START 回到底部居中（十字键靠左上 0.78h + 圆形命中避让）；A/B 错位加大（A 高于 B 0.20 倍键径）；验证：编译 + 真机截图
- [x] 8.2 十字键高亮恢复覆盖整条半臂（与臂身同宽同长圆角一致，四轮臂端短块被否决）；验证：MuMu 按右截图半臂贴合
- [x] 8.3 高亮长度回调：整条半臂被否（「太长」）→ 改为臂端向内覆盖 env×35%（`DPAD_HIGHLIGHT_FRAC` 常量，一处可调），与臂身同宽圆角一致；验证：MuMu 按右下截图双短块同亮
- [x] 8.4 A/B 缩小（0.38h → 0.34h）+ B 靠向 A（间距 0.35 → 0.16 倍键径）；验证：真机截图
- [x] 8.5 A/B 间距回调（0.16 → 0.24 倍键径，8.4 过近）；SELECT/START 缩小（78×30 → 70×27dp、字号 10 → 9sp）；验证：真机截图
- [x] 8.6 A/B 整体上移（CenterEnd 基础上提 0.06h）+ A 键错位加大（0.20 → 0.32 倍键径）；验证：真机截图
- [x] 8.11 面板高度试验 386/230 后用户回退，维持 386/215；验证：真机截图
- [x] 8.12 摇杆模式 SELECT 被摇杆底座遮挡：摇杆模式尺寸 0.84h → 0.75h（底座圆 1.02×边 比十字键凹槽大，等比视觉）；验证：真机（摇杆模式）截图——底座与 SELECT/START 间有清晰间隙
- [x] 8.7 十字键放大（0.78h → 0.84h）+ 命中区收缩到凹槽圆（内层 0.85×边 clip，方框角落不拦胶囊）；验证：编译 + MuMu 截图
- [x] 8.8 按压方向物理修正：十字朝按压方向偏移（nudge 0.018×边），阴影朝反方向偏移（按「右」影在左露出，跷跷板离缝），替换原「任何方向都直线下沉」的错误动效；验证：MuMu 按右下截图——右下偏移 + 左上阴影 + 双半臂高亮
- [x] 8.10（范围外顺带修复）详情页「开始游戏」素材按钮：按压蒙层是矩形黑 12%，盖住素材透明边距泛灰 → 删除蒙层，改 `ColorFilter.tint(SrcAtop)` 只染按钮不透明像素（下沉 3dp 保留）；验证：真机按压截图无灰底
- [x] 8.9 斜按误判修复（用户反馈「按左偏移是左下」）：均匀 8 扇区下臂身偏下 22.5° 即误入斜向；十字键改非对称扇区（正方向 ±32° / 斜向 ±13°，`JoystickMath.directions` 加 `cardinal/diagonalHalfDegrees` 参数，摇杆保持均匀 22.5°）；`JoystickDpadModeTest` 3 用例；验证：单测通过 + 真机（RMX5060）按左臂偏下截图——纯左高亮。注意：realme/MuMu 的 `input motionevent` 注入坐标不可靠（标定测试 D→左、L→下错乱），坐标敏感验证须用带拖动的 `input swipe`

## 4. 集成验证

- [x] 4.1 全量构建与单测：`./gradlew :app:assembleDebug :app:testDebugUnitTest` 通过；`openspec validate virtual-joystick` 通过
- [x] 4.2 MuMu（adb 地址参照 pad-feel 记录 192.168.31.41:5555）回归：默认设置下十字键行为与改动前一致（皮肤面板完好、弹簧手感无损，见 1942 游戏屏截图）；摇杆模式：竖屏摇杆呈现、遮蔽挖孔、推杆映射（1944 配点菜单光标下移两行）、RIGHT/START 按住 1.4s/1.5s 探针连续送达核心（poll mask=128/8）、A/B 触发正常；样式切换持久化跨重启与重装保持；探针已移除并干净重建重装。注意：adb `input tap`/`keyevent` 按下时长不足一帧会被核心漏采，验证须用带时长的 `input swipe` 按住
