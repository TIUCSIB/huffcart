# Tasks

## 1. 修复

- [x] 1.1 GameScreen 中 menuOpen 在 isPortrait 变化时自动复位（LaunchedEffect(isPortrait)）；验证：debug 构建，菜单展开→旋转→菜单收起，logcat 无「not responding」

## 2. 验证

- [x] 2.1 双向旋转冒烟：竖→横、横→竖各执行「菜单开→旋转」，无 ANR 对话框；旋转后菜单可重新打开、各菜单项可用
- [x] 2.2 `openspec validate game-menu-rotation-anr` 通过
