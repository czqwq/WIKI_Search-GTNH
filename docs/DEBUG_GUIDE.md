# 🔧 键盘事件调试指南 - 如何诊断结构预览界面搜索问题

## 已实施的调试改进

已经在以下位置添加了详细的调试日志：

### 1. GTNHWikiSearch.java - 统一处理入口
```java
LOGGER.debug("[WikiSearch] Key pressed on screen: {}", screen.getClass().getName());
```
**作用**: 显示每次按键时当前的 GUI 屏幕类名

### 2. MultiblockPreviewSearch.java - GUI 识别
```java
if (isPreview) {
    LOGGER.debug("[WikiSearch] Detected preview screen: {}", screen.getClass().getName());
}
```
**作用**: 显示是否成功识别为预览屏幕

### 3. MultiblockPreviewKeyMixin.java - 预览屏幕 Mixin
```java
LOGGER.debug("[WikiSearch] GuiScreen.keyTyped() called - Screen: {}, keyCode: {}", 
    self.getClass().getSimpleName(), keyCode);
```
**作用**: 显示每次 keyTyped 调用及其参数

### 4. GuiKeyboardInputMixin.java - 通用键盘输入 Mixin
```java
LOGGER.debug("[WikiSearch] handleKeyboardInput() - keyCode: {}, typedChar: {}", 
    keyCode, (int) typedChar);
```
**作用**: 显示通用键盘输入处理

### 5. GUIKeyDownMixin.java - 容器 Mixin
```java
LOGGER.debug("[WikiSearch] GuiContainer.keyTyped() called - keyCode: {}", keyCode);
```
**作用**: 显示容器键盘输入

## 调试步骤

### 第一步：启用调试日志

编辑 `run/config/log4j2.xml` 或在 Minecraft 启动参数中添加：
```
-Dlog4j.configurationFile=path/to/log4j2-debug.xml
```

或者在 `forge-client.toml` 中启用调试级别日志。

### 第二步：打开结构预览 GUI

1. 启动游戏
2. 打开一个结构预览 GUI（如 NC 反应堆、GT 多方块预览等）
3. 查看 `latest.log` 文件

### 第三步：按下 HOME 键并观察日志

按下 HOME 键，查看输出的日志行：

```
[WikiSearch] handleKeyboardInput() - keyCode: 199, typedChar: 0
[WikiSearch] GuiScreen.keyTyped() called - Screen: GuiStructurePreview, keyCode: 199
[WikiSearch] Detected preview screen: structurelib.gui.GuiStructurePreview
[WikiSearch] Key pressed on screen: structurelib.gui.GuiStructurePreview
```

或者可能看到：

```
[WikiSearch] handleKeyboardInput() - keyCode: 199, typedChar: 0
[WikiSearch] GuiScreen.keyTyped() called - Screen: SomeOtherGUI, keyCode: 199
（没有预览屏幕识别）
```

## 诊断决策树

### 问题 1: 完全没有看到 "[WikiSearch]" 日志

**可能的原因**:
- 日志级别设置太高（没有显示 DEBUG）
- Mixin 没有被加载
- 游戏没有调用相关的方法

**解决方案**:
1. 检查日志配置，确保启用了 DEBUG 级别
2. 在日志中查找 "Mixin" 相关的消息，验证 Mixin 是否加载
3. 查看 FML 日志确认 mod 是否正确加载

### 问题 2: 看到 "handleKeyboardInput" 但没看到 "keyTyped"

**可能的原因**:
- `keyTyped()` 方法没有被调用
- GUI 可能有自定义的键盘事件处理，跳过了 `keyTyped()`
- 事件可能在 Forge 事件系统中被消费了

**解决方案**:
1. 检查 StructureLib/BlockRenderer 的代码，看它们是否覆盖了 `keyTyped()`
2. 尝试在 `handleKeyboardInput()` 中直接处理键盘事件（不依赖 `keyTyped()`）
3. 添加更多的 mixin 注入点

### 问题 3: 看到 "keyTyped" 但没看到 "Detected preview screen"

**可能的原因**:
- GUI 类名不匹配识别规则
- `isLikelyPreviewScreen()` 的识别逻辑太严格

**解决方案**:
1. 查看日志中的实际 GUI 类名
2. 更新 `isLikelyPreviewScreen()` 以包含新的类名模式
3. 例如，如果看到 "GuiStructurePreview"，需要检查该类名是否包含识别的字符串

### 问题 4: 看到所有日志都输出，但搜索没有触发

**可能的原因**:
- KEY_CODE 比对失败
- `tryTriggerSearch()` 没有找到物品
- Wiki 搜索本身有问题

**解决方案**:
1. 检查 KEY_CODE 是否匹配（通常 HOME 键是 199）
2. 添加更多日志到 `tryTriggerSearch()`
3. 检查 `HoveredStackResolver` 是否工作正常

## 关键的 KEY_CODE 值

| 按键 | KEY_CODE |
|------|---------|
| HOME | 199 |
| END | 207 |
| PAGEUP | 201 |
| PAGEDOWN | 209 |
| INSERT | 210 |
| DELETE | 211 |
| 数字 0-9 | 176-185 |
| F1-F12 | 59-70 |

## 日志输出示例

### 成功的情况：
```
2026-06-05 12:34:56.789 [DEBUG] [WikiSearch] handleKeyboardInput() - keyCode: 199, typedChar: 0
2026-06-05 12:34:56.790 [DEBUG] [WikiSearch] GuiScreen.keyTyped() called - Screen: GuiStructurePreview, keyCode: 199
2026-06-05 12:34:56.791 [DEBUG] [WikiSearch] Detected preview screen: structurelib.gui.GuiStructurePreview
2026-06-05 12:34:56.792 [DEBUG] [WikiSearch] Key pressed on screen: structurelib.gui.GuiStructurePreview
```

### 识别失败的情况：
```
2026-06-05 12:34:56.789 [DEBUG] [WikiSearch] handleKeyboardInput() - keyCode: 199, typedChar: 0
2026-06-05 12:34:56.790 [DEBUG] [WikiSearch] GuiScreen.keyTyped() called - Screen: ModularGUIBase, keyCode: 199
（没有 "Detected preview screen" 消息）
```

### Mixin 未加载的情况：
```
（完全没有 [WikiSearch] 消息）
```

## 收集日志的方法

### Windows:
```powershell
# 查看实时日志
Get-Content "path/to/minecraft/logs/latest.log" -Tail 100 -Wait

# 搜索 WikiSearch 日志
Select-String "\[WikiSearch\]" "path/to/minecraft/logs/latest.log"
```

### Linux/Mac:
```bash
# 查看实时日志
tail -f ~/.minecraft/logs/latest.log

# 搜索 WikiSearch 日志
grep "\[WikiSearch\]" ~/.minecraft/logs/latest.log
```

## 修复步骤

基于日志诊断，可能需要的修复：

### 修复 1: 改进 GUI 识别

如果看到新的 GUI 类名（如 `ModularGUIBase` 或 `NEIGuiStructure`），需要更新：

```java
public static boolean isLikelyPreviewScreen(GuiScreen screen) {
    String className = screen.getClass().getName().toLowerCase(Locale.ROOT);
    return className.contains("structurelib") 
        || className.contains("multiblock")
        || className.contains("construct")
        || className.contains("hologram")
        || className.contains("blockrenderer")
        || className.contains("modulargui")  // 新增
        || className.contains("neistructure"); // 新增
}
```

### 修复 2: 添加更多 Mixin 注入点

如果 `keyTyped()` 不被调用，需要添加其他入口：

```java
@Inject(method = "handleInput()V", at = @At("TAIL"))
public void onHandleInput(...) { ... }

@Inject(method = "keyDown(II)V", at = @At("TAIL"))
public void onKeyDown(...) { ... }
```

### 修复 3: 使用 Forge 事件作为备用

```java
@SubscribeEvent
public static void onKeyInput(InputEvent.KeyInputEvent event) {
    // 在 Forge 事件级别处理键盘
}
```

## 后续行动

1. **立即**: 编译包含调试日志的版本
2. **测试**: 在各种结构预览 GUI 中按 HOME 键
3. **收集**: 将日志输出复制到分析文件
4. **诊断**: 根据日志输出跟踪决策树
5. **修复**: 根据诊断结果应用相应的修复

---

**调试建议**: 添加这些日志后重新编译，然后在游戏中进行测试。日志将指出问题所在。


