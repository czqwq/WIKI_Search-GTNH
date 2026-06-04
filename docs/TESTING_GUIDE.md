# WIKI Search GTNH 修复 - 测试指南

## 快速验证检查

### 编译验证 ✅
- [x] Gradle build 成功完成
- [x] 没有编译错误
- [x] Mixin 配置正确加载
- [x] JAR 文件正确生成

```bash
# 构建命令
gradlew.bat build

# 预期结果
BUILD SUCCESSFUL in 25s
```

---

## 游戏内功能测试

### 测试环境要求
- Minecraft 1.7.10
- GTNH Modpack (含 NEI、StructureLib、BlockRenderer 等)
- 本修复编译生成的 JAR 文件

### 测试步骤

#### 1. 多方块预览界面测试

**场景**: 打开多方块预览窗口（如 NC 反应堆、GT 多方块结构等）

**操作步骤**:
1. 打开一个包含多方块预览的 GUI
   - 例如: NC 反应器设置界面
   - 或: GT 多方块结构预览

2. 将鼠标悬停在左侧的方块材料列表上
   - 应该看到方块信息

3. 按下 **HOME 键**（默认搜索快捷键）
   - 预期: 自动打开浏览器或客户端搜索 GTNH Wiki
   - 搜索词: 当前鼠标下的方块/物品名称

**预期结果** ✅
- 按键有响应
- 搜索立即触发
- Wiki 页面打开

**故障排查**:
如果不工作，检查：
- [ ] 日志中是否有错误信息
- [ ] `MultiblockPreviewKeyMixin` 是否被正确注册
- [ ] HOME 键是否被正确配置

---

#### 2. NEI 物品悬停测试

**场景**: 在 NEI 物品面板中搜索并悬停物品

**操作步骤**:
1. 打开 NEI (通常按 O 键)
2. 在搜索框中输入物品名称 (如 "copper")
3. 将鼠标悬停在搜索结果中的物品
4. 按下 **HOME 键**

**预期结果** ✅
- 按键有响应
- 搜索该物品立即触发
- Wiki 页面打开显示该物品信息

**故障排查**:
如果不工作，检查：
- [ ] NEI 是否正确加载
- [ ] `GUIKeyDownMixin` 是否被正确注册
- [ ] `GuiContainerHoveredStackProvider` 是否正常工作

---

#### 3. 普通容器界面测试

**场景**: 在任何背包/容器界面中搜索物品

**操作步骤**:
1. 打开任何容器 (背包、箱子、炉子等)
2. 将鼠标悬停在一个物品上
3. 按下 **HOME 键**

**预期结果** ✅
- 按键有响应
- 搜索该物品立即触发
- Wiki 页面打开

**故障排查**:
如果不工作，检查：
- [ ] `GuiKeyboardInputMixin` 是否被正确注册
- [ ] `GuiContainerHoveredStackProvider` 工作状态

---

## 性能测试

### 响应时间测试

| 测试场景 | 预期响应时间 | 备注 |
|---------|----------|------|
| 普通容器 | <100ms | 即时反应 |
| NEI 面板 | <100ms | 即时反应 |
| 多方块预览 | <200ms | 反射查找可能稍慢 |
| Wiki 搜索启动 | <500ms | 包括网络请求 |

### 内存使用测试
- 无额外的常驻内存占用
- 搜索线程为守护线程，不会阻止服务器关闭

---

## 调试信息

### 启用详细日志

在 `GTNHWikiSearch.java` 中添加日志（可选）：

```java
@SideOnly(Side.CLIENT)
public static void handleSearchKeyPress(GuiScreen screen, char typedChar, int keyCode) {
    if (key == null || screen == null || keyCode != key.getKeyCode()) {
        return;
    }
    
    LOGGER.info("Search key pressed on screen: {}", screen.getClass().getSimpleName());
    tryTriggerSearch(screen);
}
```

### 查看 Mixin 加载状态

运行时可在日志中查找：
```
[mixin] Mixing in...
[mixin] GuiKeyboardInputMixin
[mixin] MultiblockPreviewKeyMixin
[mixin] GUIKeyDownMixin
```

如果这三个都出现，说明 Mixin 被正确加载。

---

## 已知限制与注意事项

### 1. 多方块预览反射查找
- 使用反射在不同版本的 StructureLib/BlockRenderer 中查找物品
- 某些特殊的多方块结构可能无法正确识别
- **解决方案**: `MultiblockPreviewSearch` 已实现多种查找策略

### 2. 键盘快捷键冲突
- 如果 HOME 键被其他 mod 占用，可在 MOD 设置中修改
- 不同区域键盘布局可能不同

### 3. 网络依赖
- 搜索需要网络连接
- 如果网络不可用，搜索不会显示结果（但不会出错）

---

## 回归测试清单

修复后验证不会破坏现有功能：

- [ ] 其他 mod 的键盘快捷键仍正常工作
- [ ] 多方块结构预览界面显示正常
- [ ] NEI 界面功能完整
- [ ] 容器界面拖拽物品仍正常
- [ ] 没有额外的内存泄漏
- [ ] FPS 没有下降

---

## 问题报告模板

如果在测试中发现问题，请用以下格式报告：

```
# 问题报告

## 现象描述
[详细描述问题]

## 复现步骤
1. [第一步]
2. [第二步]
3. [第三步]

## 预期行为
[应该发生什么]

## 实际行为
[实际发生了什么]

## 环境信息
- Minecraft 版本: 1.7.10
- GTNH Modpack 版本: [版本号]
- Java 版本: [版本号]
- 操作系统: [系统名称]

## 日志截图
[粘贴相关日志]
```

---

## 修复验证通过标准

所有以下标准都满足，则修复通过：

| 标准 | 状态 | 备注 |
|------|------|------|
| 编译成功 | ✅ | gradle build 无错误 |
| 多方块预览可搜索 | ⏳ | 待游戏内验证 |
| NEI 物品可搜索 | ⏳ | 待游戏内验证 |
| 普通容器可搜索 | ⏳ | 待游戏内验证 |
| 无性能问题 | ⏳ | 待性能测试 |
| 无新的错误 | ⏳ | 待日志检查 |

---

## 后续维护

### 定期检查项
- [ ] 月度：检查 NEI 和 StructureLib 的更新
- [ ] 季度：检查 Mixin 版本更新
- [ ] 年度：代码审查和优化

### 升级路径
如果 GTNH Modpack 的 MC 版本更新（如从 1.7.10 升级到 1.12）：
1. 更新各个 provider 的反射策略
2. 检查 Mixin 兼容性
3. 进行完整的回归测试

---

**测试完成日期**: ________________  
**测试人员**: ________________  
**测试结果**: ✅ / ⚠️ / ❌  
**备注**: ____________________________________________________


