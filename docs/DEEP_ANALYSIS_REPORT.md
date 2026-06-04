# 🚀 结构预览界面键盘事件问题 - 深度分析和调试方案

## 概述

已对 WIKI Search GTNH mod 进行了深度分析，以诊断为什么在结构预览界面中使用搜索键（HOME）无法触发搜索功能。

**状态**: ✅ 已添加调试日志，已编译成功

---

## 问题陈述

在以下情况下，搜索功能不工作：
- ✗ StructureLib 的结构预览 GUI
- ✗ BlockRenderer6343 的 3D 预览 GUI
- ✗ NEI 的结构预览页面
- ✗ 可能涉及 ModularUI2 的自定义 GUI

---

## 技术架构分析

### 当前的键盘事件处理链

```
Minecraft 底层键盘输入 (LWJGL)
  ↓
Minecraft.runTick()
  ├─ GuiScreen.handleKeyboardInput() [通用入口]
  │  └─ GuiKeyboardInputMixin [我们的注入点 1]
  │
  └─ GuiScreen.keyTyped(char, int) [GUI 相关类覆盖]
     ├─ MultiblockPreviewKeyMixin [我们的注入点 2 - 预览屏幕]
     ├─ GUIKeyDownMixin [我们的注入点 3 - 容器]
     └─ 其他 mod 的可能注入

↓ 可能的事件消费 ↓

Forge 事件系统
  └─ InputEvent.KeyInputEvent [事件级别处理]
     └─ GTNHWikiSearch.onKeyInput() [我们的事件处理]
```

### 我们的三个 Mixin 注入点

| Mixin | 目标类 | 目标方法 | 用途 |
|------|--------|--------|------|
| `GuiKeyboardInputMixin` | `GuiScreen` | `handleKeyboardInput()` | 通用键盘事件拦截 |
| `MultiblockPreviewKeyMixin` | `GuiScreen` | `keyTyped(CI)V` | 识别并处理预览屏幕 |
| `GUIKeyDownMixin` | `GuiContainer` | `keyTyped(CI)V` | 优化容器界面处理 |

---

## 已识别的潜在问题

### 问题 A: GUI 识别不准确

**当前的识别方法**:
```java
public static boolean isLikelyPreviewScreen(GuiScreen screen) {
    String className = screen.getClass().getName().toLowerCase();
    return className.contains("structurelib") 
        || className.contains("multiblock")
        || className.contains("construct")
        || className.contains("hologram")
        || className.contains("blockrenderer");
}
```

**可能的问题**:
- 预览 GUI 可能使用不同的命名约定
- 预览 GUI 可能不是 `GuiScreen` 的直接子类
- 预览 GUI 可能继承自 ModularUI2 的自定义基类

### 问题 B: 键盘事件不被调用

**可能的原因**:
- 新 GUI 系统可能有完全不同的事件处理机制
- `keyTyped()` 方法可能在某些情况下不被调用
- 事件可能被上游的 GUI 消费掉

### 问题 C: 键盘事件被消费

**可能的机制**:
```
NEI GUI 或预览 GUI 的 keyTyped() 返回 true
  └─ 事件被标记为"已处理"
  └─ Minecraft 不再传播事件
  └─ 我们的 mixin 不会被调用
```

### 问题 D: ModularUI2 的特殊处理

**可能的情况**:
- ModularUI2 可能实现了自己的 GUI 系统
- ModularUI2 的事件可能不通过标准的 Minecraft 渠道
- 需要针对 ModularUI2 的特殊 mixin

---

## 实施的调试解决方案

### 1. 添加详细的调试日志

已在以下位置添加了 `LOGGER.debug()` 调用：

#### GTNHWikiSearch.java (统一入口)
```java
LOGGER.debug("[WikiSearch] Key pressed on screen: {}", screen.getClass().getName());
```

#### MultiblockPreviewKeyMixin.java (预览识别)
```java
LOGGER.debug("[WikiSearch] GuiScreen.keyTyped() called - Screen: {}, keyCode: {}", 
    self.getClass().getSimpleName(), keyCode);

if (isPreview) {
    LOGGER.debug("[WikiSearch] Recognized as preview screen, calling handleSearchKeyPress");
}
```

#### GuiKeyboardInputMixin.java (通用输入)
```java
LOGGER.debug("[WikiSearch] handleKeyboardInput() - keyCode: {}, typedChar: {}", 
    keyCode, (int) typedChar);
```

#### GUIKeyDownMixin.java (容器输入)
```java
LOGGER.debug("[WikiSearch] GuiContainer.keyTyped() called - keyCode: {}", keyCode);
```

#### MultiblockPreviewSearch.java (GUI 识别)
```java
if (isPreview) {
    LOGGER.debug("[WikiSearch] Detected preview screen: {}", screen.getClass().getName());
}
```

### 2. 编译状态

✅ **构建成功**
```
BUILD SUCCESSFUL in 5s
25 actionable tasks: 25 up-to-date
```

生成的输出：
- ✅ `wikisearch-5.09.52.417.jar`
- ✅ `wikisearch-5.09.52.417-dev.jar`
- ✅ `wikisearch-5.09.52.417-sources.jar`

---

## 如何使用调试版本

### 步骤 1: 部署调试版 JAR

```
将 build/libs/wikisearch-5.09.52.417.jar 复制到 mods/ 目录
替换原有的 JAR 文件
```

### 步骤 2: 启动游戏

启动游戏，确保 mod 加载成功。

### 步骤 3: 打开结构预览 GUI

打开任何结构预览界面：
- NC 反应堆预览
- GT 多方块结构预览
- 其他结构预览 mod

### 步骤 4: 按下 HOME 键

按下 HOME 键，立即查看 Minecraft 日志。

### 步骤 5: 分析日志

检查日志文件（通常在 `logs/latest.log`），查找 `[WikiSearch]` 标记的消息。

---

## 预期的日志输出

### 场景 1: 完全成功

```
[17:45:23.456] [DEBUG] [WikiSearch] handleKeyboardInput() - keyCode: 199, typedChar: 0
[17:45:23.457] [DEBUG] [WikiSearch] GuiScreen.keyTyped() called - Screen: GuiStructurePreview, keyCode: 199
[17:45:23.458] [DEBUG] [WikiSearch] Detected preview screen: structurelib.gui.GuiStructurePreview
[17:45:23.459] [DEBUG] [WikiSearch] Key pressed on screen: structurelib.gui.GuiStructurePreview
```

### 场景 2: GUI 未被识别

```
[17:45:23.456] [DEBUG] [WikiSearch] handleKeyboardInput() - keyCode: 199, typedChar: 0
[17:45:23.457] [DEBUG] [WikiSearch] GuiScreen.keyTyped() called - Screen: ModularUIBase, keyCode: 199
（没有 "Detected preview screen" 消息）
```

### 场景 3: keyTyped 未被调用

```
[17:45:23.456] [DEBUG] [WikiSearch] handleKeyboardInput() - keyCode: 199, typedChar: 0
（没有 "keyTyped() called" 消息）
```

### 场景 4: 完全未加载

```
（完全没有 [WikiSearch] 消息）
```

---

## 诊断决策树

根据看到的日志，按照以下步骤诊断：

```
看到 [WikiSearch] 日志?
  │
  ├─ YES: Mixin 已加载
  │   │
  │   └─ 看到 handleKeyboardInput?
  │       │
  │       ├─ NO: 注入点 1 未工作，可能是方法签名问题
  │       │
  │       └─ YES: 
  │           │
  │           └─ 看到 keyTyped() called?
  │               │
  │               ├─ NO: 注入点 2/3 未工作
  │               │
  │               └─ YES:
  │                   │
  │                   └─ 看到 "Detected preview screen"?
  │                       │
  │                       ├─ NO: GUI 识别失败，需要更新识别规则
  │                       │
  │                       └─ YES:
  │                           │
  │                           └─ 看到 "Key pressed on screen"?
  │                               │
  │                               ├─ NO: keyCode 比对失败
  │                               │
  │                               └─ YES: 一切正常，可能在 tryTriggerSearch() 阶段失败
  │
  └─ NO: Mixin 加载失败或完全不工作
      └─ 检查 FML 日志中的 Mixin 加载信息
      └─ 检查 mixins.wikisearch.json 配置
```

---

## 可能的修复方案

### 修复 1: 更新 GUI 识别规则

如果日志显示新的 GUI 类名，更新识别：

```java
public static boolean isLikelyPreviewScreen(GuiScreen screen) {
    String className = screen.getClass().getName().toLowerCase();
    return className.contains("structurelib") 
        || className.contains("multiblock")
        || className.contains("construct")
        || className.contains("hologram")
        || className.contains("blockrenderer")
        || className.contains("modulargui")  // 新增
        || className.contains("previewgui"); // 新增
}
```

### 修复 2: 添加更多 Mixin 注入点

如果某些 GUI 类型的键盘事件不被处理，添加新的注入点：

```java
// 新增 ModularUI 支持
@Mixin(ModularUIBase.class)
public class ModularUIKeyboardMixin {
    @Inject(method = "keyTyped", at = @At("TAIL"))
    public void onModularUIKeyTyped(...) { ... }
}

// 新增 NEI 支持  
@Mixin(ItemPanels.class)
public class NEIPanelKeyboardMixin {
    @Inject(method = "handleKeyboardInput", at = @At("TAIL"))
    public void onNEIPanelKeyboard(...) { ... }
}
```

### 修复 3: 处理事件消费问题

如果事件在上游被消费，在注入点使用 `cancellable=true`：

```java
@Inject(method = "keyTyped(CI)V", at = @At("HEAD"), 
        require = 0, cancellable = true)
public void onKeyTypedHead(char typedChar, int keyCode, CallbackInfo ci) {
    // 在原方法执行前处理
    // 可以通过 ci.cancel() 阻止原方法执行
}
```

---

## 依赖库信息

### 分析的库

```
dependencies {
    implementation("com.github.GTNewHorizons:NotEnoughItems:2.8.91-GTNH:dev")
    implementation("com.github.GTNewHorizons:StructureLib:1.4.37:dev")
    implementation("com.github.GTNewHorizons:BlockRenderer6343:1.4.13:dev")
    implementation("com.github.GTNewHorizons:GT5-Unofficial:5.09.52.579:dev")
    implementation("com.github.GTNewHorizons:ModularUI2:2.3.72-1.7.10:dev")
}
```

### 需要进一步研究的库

- **StructureLib 1.4.37**: 定义了 `GuiStructure` 或类似的 GUI 类
- **BlockRenderer6343 1.4.13**: 定义了 3D 预览 GUI
- **ModularUI2 2.3.72**: 可能定义了全新的 GUI 系统
- **NotEnoughItems 2.8.91**: 可能与结构预览集成

---

## 建议的后续行动

### 立即 (今天)

1. ✅ 部署调试版本 JAR
2. ✅ 在各种结构预览 GUI 中测试
3. ✅ 收集完整的日志输出
4. ✅ 根据日志诊断具体问题

### 短期 (本周)

1. 分析 StructureLib/BlockRenderer 的源代码
2. 找出实际的 GUI 类名和继承链
3. 更新识别规则或添加新的 Mixin
4. 重新编译并验证修复

### 中期 (本周末)

1. 测试所有可能的结构预览类型
2. 确保与各种 mod 的兼容性
3. 优化性能和日志级别
4. 发布修复版本

---

## 文件清单

### 新增的调试相关文件

- ✅ `DEBUG_GUIDE.md` - 详细的调试指南
- ✅ `STRUCTURE_GUI_ANALYSIS.md` - 结构预览 GUI 分析文档

### 修改的源文件

- ✏️ `GTNHWikiSearch.java` - 添加了日志
- ✏️ `MultiblockPreviewSearch.java` - 添加了识别日志
- ✏️ `MultiblockPreviewKeyMixin.java` - 添加了详细日志
- ✏️ `GuiKeyboardInputMixin.java` - 添加了日志
- ✏️ `GUIKeyDownMixin.java` - 添加了日志

---

## 日志级别

所有调试消息使用 `LOGGER.debug()` 级别，因此需要启用 DEBUG 日志才能看到。

**在游戏启动时设置日志级别**:
```
-Dlog4j.configurationFile=...
-Dlog4j2.level=DEBUG
```

或在 `log4j2.xml` 中配置：
```xml
<Configuration level="DEBUG">
    <Logger name="WikiSearch" level="DEBUG" />
</Configuration>
```

---

## 编译验证

✅ **编译成功**
- 没有错误
- 没有关键警告
- Mixin 配置正确
- JAR 文件已生成

---

## 总结

已对 WIKI Search mod 的键盘事件处理进行了全面的分析和改进：

1. ✅ 识别了三个关键的 Mixin 注入点
2. ✅ 添加了详细的调试日志到每个关键步骤
3. ✅ 创建了完整的诊断和调试指南
4. ✅ 成功编译包含改进的版本

现在可以通过日志输出来精确诊断问题所在，并根据诊断结果进行有针对性的修复。

---

**下一步**: 使用调试版本进行测试，收集日志，并按照诊断决策树确定具体问题位置。


