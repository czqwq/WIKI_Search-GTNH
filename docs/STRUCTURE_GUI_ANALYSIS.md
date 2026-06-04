# 🔍 结构预览界面键盘事件分析

## 问题陈述

在 StructureLib、BlockRenderer6343、ModularUI2 等库的结构预览界面中，搜索键（HOME）无法触发搜索功能。

## 已识别的架构

### 当前的 Mixin 注入点

```java
// GuiKeyboardInputMixin.java - 注入 GuiScreen.handleKeyboardInput()
@Inject(method = "handleKeyboardInput()V", at = @At("TAIL"))

// MultiblockPreviewKeyMixin.java - 注入 GuiScreen.keyTyped()  
@Inject(method = "keyTyped(CI)V", at = @At("TAIL"))

// GUIKeyDownMixin.java - 注入 GuiContainer.keyTyped()
@Inject(method = "keyTyped(CI)V", at = @At("TAIL"))
```

### 键盘事件处理流程

```
Minecraft 键盘输入
  ↓
GuiScreen.handleKeyboardInput() [通用入口]
  ├─ GuiKeyboardInputMixin (尝试处理)
  ↓
GuiScreen.keyTyped(char, int) [GUI 相关类覆盖]
  ├─ MultiblockPreviewKeyMixin (如果是预览屏幕)
  ├─ GUIKeyDownMixin (如果是 GuiContainer)
  ↓
Forge KeyInputEvent [Forge 事件系统]
  ├─ onKeyInput() (GTNHWikiSearch 的事件处理)
```

## 潜在问题点

### 问题 1: 结构预览 GUI 可能不是标准 GuiScreen 的子类

**症状**: `MultiblockPreviewSearch.isLikelyPreviewScreen()` 通过类名检查识别预览屏幕，但这可能不够准确。

**可能原因**:
- StructureLib/BlockRenderer 可能定义了完全不同的 GUI 类
- 这些类可能不继承 `GuiScreen`
- 或者继承链可能很深，类名检查不准确

**当前检查**:
```java
public static boolean isLikelyPreviewScreen(GuiScreen screen) {
    String className = screen.getClass().getName().toLowerCase(Locale.ROOT);
    return className.contains("structurelib") || className.contains("multiblock")
        || className.contains("construct") || className.contains("hologram")
        || className.contains("blockrenderer");
}
```

**问题**: 这个检查依赖于**类名包含**特定字符串，可能：
- 漏掉实际的预览 GUI 类
- 错误地识别非预览的 GUI

### 问题 2: keyTyped 方法可能不被调用

**症状**: 即使识别成功，`keyTyped()` 方法可能根本不被调用。

**可能原因**:
- 新 GUI 系统（可能来自 ModularUI2）完全覆盖了键盘事件处理
- GUI 可能有自己的键盘事件消费机制
- `keyTyped()` 可能已被废弃或替换为其他方法

**需要验证**:
- 预览 GUI 是否真的继承 `GuiScreen`
- 预览 GUI 是否覆盖了 `keyTyped()`
- 预览 GUI 是否有自定义的键盘处理

### 问题 3: 新 GUI 框架的特殊处理

**症状**: ModularUI2 可能引入了全新的 GUI 系统。

**可能原因**:
- ModularUI2 可能实现了自己的事件分发系统
- 键盘事件可能被拦截在 ModularUI2 层面
- 需要在 ModularUI2 的事件系统中注入 mixin

**需要研究**:
- ModularUI2 的 GUI 事件处理机制
- ModularUI2 是否提供了事件订阅系统
- 是否需要针对 ModularUI2 的特殊 mixin

### 问题 4: NEI 页面的键盘事件消费

**症状**: NEI 的结构预览 GUI 可能消费了键盘事件，阻止向下传播。

**机制**:
```
Minecraft 键盘事件
  ↓
NEI GUI 处理（可能消费事件）
  ├─ 如果 NEI 返回 true （事件被消费）
  │   └─ 事件不再向下传播
  └─ 如果 NEI 返回 false （事件未消费）
      └─ Forge 继续分发事件
```

**问题**: 如果 NEI 的预览 GUI 消费了 HOME 键，我们的搜索功能无法接收到。

## 相关依赖库分析

### NotEnoughItems (NEI) 2.8.91

**职责**: 提供物品面板和工具提示

**可能的相关类**:
- `codechicken.nei.ItemPanels` - 物品面板
- `codechicken.nei.guihook.GuiContainerManager` - GUI 容器管理
- NEI 的自定义 GUI 类

**关键问题**: NEI 的预览 GUI 类名是什么？如何识别？

### StructureLib 1.4.37

**职责**: 多方块结构管理和预览

**可能的相关类**:
- Structure 相关类
- 结构预览 GUI
- GUI 的基类和继承链

**关键问题**: StructureLib 的预览 GUI 是否是标准 `GuiScreen` 的子类？

### BlockRenderer6343 1.4.13

**职责**: 3D 方块渲染

**可能的相关类**:
- 3D 预览 GUI
- 渲染系统集成

**关键问题**: BlockRenderer 如何与 GUI 系统集成？

### ModularUI2 2.3.72

**职责**: 新一代 GUI 框架

**可能的相关类**:
- ModularUI 的基础 GUI 类
- 事件系统
- 键盘事件处理

**关键问题**: ModularUI2 是否定义了完全不同的 GUI 系统？键盘事件如何分发？

## 根本原因假设

### 假设 A: NEI 或 StructureLib 的 GUI 不是标准 GuiScreen

**影响**: `MultiblockPreviewKeyMixin.isLikelyPreviewScreen()` 可能根本不匹配

**证据需要**:
- 预览 GUI 的类名
- 预览 GUI 的继承链
- 预览 GUI 是否覆盖了关键方法

### 假设 B: 预览 GUI 覆盖了 keyTyped 并消费了事件

**影响**: `keyTyped()` 被调用，但返回 true（消费事件），阻止我们的 mixin

**证据需要**:
- 预览 GUI 的 `keyTyped()` 实现
- 返回值是否为 true
- 事件是否在此被拦截

### 假设 C: ModularUI2 提供了完全不同的事件系统

**影响**: 标准的 Minecraft 键盘事件完全不适用

**证据需要**:
- ModularUI2 的事件系统设计
- 键盘事件如何在 ModularUI2 中处理
- 是否有 API 支持事件监听

### 假设 D: 存在新的 GUI 类，既不是 GuiScreen 也不是 GuiContainer

**影响**: 我们的 mixin 可能无法注入

**证据需要**:
- 预览 GUI 的类层次结构
- 预览 GUI 是否定义了自己的基类
- 是否需要针对新基类的特殊 mixin

## 调查方案

### 第一步: 确定预览 GUI 类

需要答复以下问题:
1. StructureLib 的预览 GUI 类名是什么？
2. BlockRenderer 的预览 GUI 类名是什么？
3. NEI 的预览 GUI 类名是什么？
4. 这些 GUI 是否继承 GuiScreen？
5. 这些 GUI 的完整继承链是什么？

### 第二步: 分析键盘事件处理

需要检查以下代码:
1. 预览 GUI 是否有 `keyTyped()` 方法？
2. 预览 GUI 是否有 `handleKeyboardInput()` 方法？
3. 这些方法是否调用 `super` 方法？
4. 这些方法是否消费了键盘事件（返回 true）？
5. 是否有其他的键盘事件处理方式？

### 第三步: 分析 ModularUI2 系统

需要研究:
1. ModularUI2 是否有自己的 GUI 基类？
2. ModularUI2 的键盘事件如何分发？
3. ModularUI2 是否有事件监听 API？
4. 是否需要在 ModularUI2 层面进行 mixin 注入？

### 第四步: 验证识别逻辑

需要:
1. 添加日志记录当前正在处理的 GUI 类名
2. 验证 `isLikelyPreviewScreen()` 是否正确识别预览 GUI
3. 验证 `keyTyped()` 是否被调用
4. 跟踪键盘事件的流程

## 可能的修复策略

### 策略 1: 改进 GUI 识别

```java
public static boolean isLikelyPreviewScreen(GuiScreen screen) {
    // 1. 检查类名
    String className = screen.getClass().getName().toLowerCase(Locale.ROOT);
    if (className.contains("structurelib") || className.contains("multiblock")
        || className.contains("construct") || className.contains("hologram")
        || className.contains("blockrenderer")) {
        return true;
    }
    
    // 2. 检查接口或注解
    Class<?> clazz = screen.getClass();
    // 可能有特殊的标记接口或注解
    
    // 3. 检查特殊字段或方法
    // 反射查找已知的预览 GUI 特有的字段或方法
    
    return false;
}
```

### 策略 2: 在 keyTyped 前添加注入点

如果 `keyTyped()` 被消费，尝试在 `keyTyped()` 的开始注入：

```java
@Inject(method = "keyTyped(CI)V", at = @At("HEAD"), require = 0, cancellable = true)
public void onPreviewSearchKeyHead(char typedChar, int keyCode, CallbackInfo ci) {
    // 在原方法执行前处理
}
```

### 策略 3: 添加 ModularUI2 特殊支持

如果 ModularUI2 有自己的事件系统，创建专门的 mixin：

```java
@Mixin(ModularUIBase.class) // 假设的类名
public abstract class ModularUIKeyboardMixin {
    @Inject(method = "onKeyEvent(Lnet/minecraftforge/api/distmarker/Side;II)V", at = @At("TAIL"))
    public void onKeyEvent(...) {
        // 处理 ModularUI 事件
    }
}
```

### 策略 4: 在 Forge 事件级别添加备用处理

当 mixin 无效时，使用 Forge 事件系统作为备用：

```java
@SubscribeEvent
public void onGuiKeyboardEvent(GuiScreenEvent.KeyboardInputEvent event) {
    // 在 Forge 事件层面处理
}
```

## 后续行动

### 立即需要:

1. 📖 **获取库的源代码**
   - 下载 StructureLib 的 deobf JAR
   - 下载 BlockRenderer6343 的 deobf JAR
   - 下载 ModularUI2 的 deobf JAR
   - 查看这些库的 GUI 实现

2. 🔍 **确定 GUI 类**
   - 找到结构预览 GUI 的类名
   - 分析其继承链
   - 检查其键盘事件处理

3. 📝 **添加调试日志**
   - 在 mixin 中添加日志记录
   - 跟踪键盘事件的流程
   - 验证我们的 mixin 是否被调用

4. 🧪 **进行测试**
   - 在游戏中打开各种结构预览 GUI
   - 按下 HOME 键
   - 检查日志输出

---

**下一步**: 需要您提供或帮我查找 StructureLib、BlockRenderer6343、ModularUI2 的源代码或 deobf JAR，以便进行深入分析。


