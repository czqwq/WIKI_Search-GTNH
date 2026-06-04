# 🔧 结构预览界面搜索问题 - 根本原因和修复方案

## 基于依赖库的分析

根据项目的依赖配置，我们可以推断出可能的问题原因。

### 依赖库分析

```gradle
implementation("com.github.GTNewHorizons:NotEnoughItems:2.8.91-GTNH:dev")
implementation("com.github.GTNewHorizons:StructureLib:1.4.37:dev")
implementation("com.github.GTNewHorizons:BlockRenderer6343:1.4.13:dev")
implementation("com.github.GTNewHorizons:GT5-Unofficial:5.09.52.579:dev")
implementation("com.github.GTNewHorizons:ModularUI2:2.3.72-1.7.10:dev")
```

### 关键发现

1. **ModularUI2** - 这是一个**新的 GUI 框架**，很可能是问题的根源
   - ModularUI2 可能实现了完全不同的 GUI 系统
   - ModularUI2 的键盘事件处理可能与标准 Minecraft GUI 不兼容

2. **StructureLib 1.4.37** - 可能使用了 ModularUI2 作为基础
   - 结构预览 GUI 可能是 ModularUI2 的 GUI 实现
   - 可能不继承标准的 `GuiScreen`

3. **BlockRenderer6343** - 可能与 StructureLib 集成
   - 3D 预览可能基于 ModularUI2 框架

---

## 推论的根本原因

### 最可能的问题：ModularUI2 GUI 不继承 GuiScreen

```
ModularUI2 GUI 系统 (新框架)
  ├─ 不继承 GuiScreen
  ├─ 有自己的键盘事件处理
  ├─ 有自己的 GUI 基类（可能是 ModularUIBase 或类似的）
  └─ 不调用标准的 keyTyped() 或 handleKeyboardInput()

我们的 Mixin 依赖于：
  ├─ GuiScreen.keyTyped() ✗ 不被调用
  ├─ GuiScreen.handleKeyboardInput() ✗ 不被调用
  └─ GuiContainer.keyTyped() ✗ 不被调用
```

### 具体表现

当用户在结构预览 GUI 中按 HOME 键时：

```
1. Minecraft 接收键盘输入
2. ModularUI2 GUI 处理事件（使用自己的事件系统）
3. 标准的 GuiScreen 方法不被调用
4. 我们的 Mixin 注入点没有机会执行
5. 搜索功能无法触发
```

---

## 修复方案

### 方案 A: 针对 ModularUI2 的 Mixin（推荐）

创建新的 Mixin 来支持 ModularUI2：

```java
// ModularUI2KeyboardMixin.java
package com.czqwq.wikisearch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.bartimaeusnek.modularui.api.UIInfos;
import com.github.bartimaeusnek.modularui.common.internal.network.NetworkUtils;

import net.minecraftforge.client.event.GuiScreenEvent;

/**
 * Support for ModularUI2 keyboard events.
 * ModularUI2 uses a different event system than standard Minecraft GUI.
 */
@Mixin(UIInfos.class)  // 可能需要调整到实际的 ModularUI2 类
public abstract class ModularUI2KeyboardMixin {

    @Inject(method = "handleKeyboardInput", at = @At("TAIL"), require = 0)
    public void onModularUIKeyboardInput(CallbackInfo ci) {
        // 处理 ModularUI2 的键盘事件
    }
}
```

**问题**: 需要知道 ModularUI2 的确切类名和方法签名。

### 方案 B: 通过 Forge 事件系统处理（备用方案）

使用 Forge 事件作为通用的事件捕获器：

```java
// ForgeEventKeyboardHandler.java
package com.czqwq.wikisearch;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.lwjgl.input.Keyboard;

@Mod.EventBusSubscriber(modid = "wikisearch", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ForgeEventKeyboardHandler {

    @SubscribeEvent
    public static void onGuiKeyboard(GuiScreenEvent.KeyboardInputEvent event) {
        if (Keyboard.getEventKeyState() && Keyboard.getEventKey() == 0) {
            int keyCode = Keyboard.getEventCharacter() + 256;
            // 处理搜索快捷键
        }
    }
}
```

**优点**: 可以捕获所有 GUI 系统的键盘事件
**缺点**: 可能不够精确，需要额外的处理

### 方案 C: 检测并支持多种 GUI 系统

在 `MultiblockPreviewSearch` 中添加对更多 GUI 类型的支持：

```java
public static boolean isLikelyPreviewScreen(GuiScreen screen) {
    String className = screen.getClass().getName().toLowerCase();
    
    // 标准预览屏幕
    if (className.contains("structurelib") || className.contains("multiblock")
        || className.contains("blockrenderer")) {
        return true;
    }
    
    // ModularUI 相关
    if (className.contains("modulargui") || className.contains("modularui")) {
        // 检查是否是结构预览 ModularUI
        return isStructurePreviewModularUI(screen);
    }
    
    // NEI 相关
    if (className.contains("nei") && className.contains("structure")) {
        return true;
    }
    
    return false;
}

private static boolean isStructurePreviewModularUI(GuiScreen screen) {
    // 通过反射检查 GUI 的属性
    try {
        Class<?> clazz = screen.getClass();
        // 查找与结构相关的字段
        for (Field field : clazz.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if (name.contains("structure") || name.contains("preview") 
                || name.contains("multiblock")) {
                return true;
            }
        }
    } catch (Exception ignored) {}
    return false;
}
```

---

## 立即可实施的修复

### 修复 1: 改进 GUI 类名识别

添加更多的识别模式到 `isLikelyPreviewScreen()`：

```java
public static boolean isLikelyPreviewScreen(GuiScreen screen) {
    String className = screen.getClass().getName().toLowerCase(Locale.ROOT);
    
    // 现有的识别规则
    boolean isStandardPreview = className.contains("structurelib") 
        || className.contains("multiblock")
        || className.contains("construct")
        || className.contains("hologram")
        || className.contains("blockrenderer");
    
    // 新增的识别规则
    boolean isModularUIPreview = className.contains("modularui") 
        && (className.contains("preview") || className.contains("structure"));
    
    boolean isNEIStructure = className.contains("nei") 
        && className.contains("structure");
    
    return isStandardPreview || isModularUIPreview || isNEIStructure;
}
```

### 修复 2: 添加备用事件处理

在 `GTNHWikiSearch.java` 中添加 Forge 事件处理作为备用：

```java
@SideOnly(Side.CLIENT)
@SubscribeEvent
public void onGuiKeyboardEvent(GuiScreenEvent.KeyboardInputEvent event) {
    if (event.getGui() == null) {
        return;
    }
    
    // 备用键盘处理
    if (Keyboard.getEventKeyState() && Keyboard.getEventKey() == 0) {
        int keyCode = Keyboard.getEventCharacter() + 256;
        if (keyCode == key.getKeyCode()) {
            tryTriggerSearch(event.getGui());
        }
    }
}
```

### 修复 3: 改进反射查找

增强 `findByMethod()` 和 `findByField()` 的搜索范围：

```java
private static ItemStack findByMethod(GuiScreen screen, int mouseX, int mouseY) {
    Class<?> type = screen.getClass();
    while (type != null && type != Object.class) {
        for (Method method : type.getDeclaredMethods()) {
            try {
                method.setAccessible(true);
                
                // 扩展搜索范围
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 0 || 
                    (params.length == 2 && params[0] == int.class && params[1] == int.class)) {
                    
                    // ... 现有逻辑 ...
                    
                    // 新增: 尝试无参数的获取方法
                    if (params.length == 0) {
                        String methodName = method.getName().toLowerCase();
                        if (methodName.contains("getblock") || methodName.contains("getselected")) {
                            Object value = method.invoke(screen);
                            ItemStack stack = extractItemStack(value);
                            if (stack != null) return stack;
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        type = type.getSuperclass();
    }
    return null;
}
```

---

## 实现步骤

### 步骤 1: 编译和测试调试版本

已完成 ✅
- 添加了日志
- 编译成功

### 步骤 2: 收集日志数据

需要用户：
1. 部署调试版 JAR
2. 在各种结构预览 GUI 中测试
3. 收集日志输出
4. 分析日志确定实际问题

### 步骤 3: 基于日志诊断

根据日志输出判断：
- 是否是 GUI 识别问题
- 是否是键盘事件不被调用
- 是否是 ModularUI2 问题

### 步骤 4: 应用相应的修复

根据诊断结果应用相应的修复方案。

---

## 预期的日志诊断结果

### 如果是 ModularUI2 问题

日志会显示：
```
[DEBUG] [WikiSearch] handleKeyboardInput() - keyCode: 199, typedChar: 0
（没有看到 keyTyped() 被调用）
```

**解决**: 需要为 ModularUI2 创建专门的 Mixin

### 如果是识别问题

日志会显示：
```
[DEBUG] [WikiSearch] handleKeyboardInput() - keyCode: 199, typedChar: 0
[DEBUG] [WikiSearch] GuiScreen.keyTyped() called - Screen: ModularUIStructurePreview, keyCode: 199
（没有看到 "Detected preview screen" 消息）
```

**解决**: 更新 `isLikelyPreviewScreen()` 的识别规则

### 如果一切正常但搜索不工作

日志会显示：
```
[DEBUG] [WikiSearch] Key pressed on screen: structurelib.gui.GuiStructurePreview
（但搜索没有发生）
```

**解决**: 问题可能在 `tryTriggerSearch()` 或 `HoveredStackResolver` 中

---

## 需要的信息

为了进行更精确的修复，需要以下信息：

1. **StructureLib 的 GUI 类名**
   - 结构预览 GUI 的完整类名
   - 其父类和接口

2. **ModularUI2 的 GUI 系统**
   - ModularUI2 的基础 GUI 类名
   - ModularUI2 的键盘事件处理方式
   - ModularUI2 的 GUI 标记方式

3. **实际的日志输出**
   - 按 HOME 键时的完整日志
   - 包括 Mixin 加载信息

---

## 建议的测试清单

- [ ] 在 NC 反应堆预览中按 HOME 键
- [ ] 在 GT 多方块预览中按 HOME 键
- [ ] 在 Thermal Foundation 结构预览中按 HOME 键
- [ ] 在 NEI 的结构页面中按 HOME 键
- [ ] 收集所有上述场景的日志
- [ ] 分析日志中的差异
- [ ] 识别 GUI 系统的类型

---

## 总结

基于对项目依赖的分析，最可能的根本原因是：

**ModularUI2 引入了新的 GUI 系统，其键盘事件处理方式与标准 Minecraft GUI 不兼容。**

已添加的调试日志将帮助确认这一推论，并指导后续的修复工作。

---

**下一步**: 
1. 编译包含调试日志的版本（✅ 已完成）
2. 在游戏中测试并收集日志
3. 根据日志应用相应的修复
4. 验证修复是否有效


