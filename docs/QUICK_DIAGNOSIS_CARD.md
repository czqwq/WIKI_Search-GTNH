# 🎯 结构预览搜索问题 - 快速参考卡

## 问题
在结构预览 GUI 中按 HOME 键无法触发搜索

## 推断的根本原因
ModularUI2 新 GUI 框架 → 事件处理方式不同 → 我们的 Mixin 无法拦截

## 验证方法

### 步骤 1: 部署调试版
```
build/libs/wikisearch-5.09.52.417.jar → mods/
```

### 步骤 2: 打开结构预览并按 HOME 键

### 步骤 3: 查看日志
```
logs/latest.log 中搜索 "[WikiSearch]"
```

## 预期日志输出

### ✅ 如果问题已解决
```
[WikiSearch] handleKeyboardInput() - keyCode: 199, typedChar: 0
[WikiSearch] GuiScreen.keyTyped() called - Screen: GuiStructurePreview, keyCode: 199
[WikiSearch] Detected preview screen: structurelib.gui.GuiStructurePreview
[WikiSearch] Key pressed on screen: structurelib.gui.GuiStructurePreview
```

### ❌ 如果是 ModularUI2 问题
```
[WikiSearch] handleKeyboardInput() - keyCode: 199, typedChar: 0
(没有后续 keyTyped() 消息)
```
→ 需要为 ModularUI2 创建专门的 Mixin

### ❌ 如果是识别问题
```
[WikiSearch] GuiScreen.keyTyped() called - Screen: ModularUIStructurePreview, keyCode: 199
(没有 "Detected preview screen" 消息)
```
→ 需要更新 isLikelyPreviewScreen() 规则

## 文档导航

| 需要 | 查看文档 | 说明 |
|------|--------|------|
| 详细调试步骤 | DEBUG_GUIDE.md | 完整的调试指南 |
| 技术分析 | ROOT_CAUSE_ANALYSIS.md | 根本原因和修复方案 |
| 完整报告 | FINAL_ANALYSIS_SUMMARY.md | 执行总结 |
| 深度分析 | DEEP_ANALYSIS_REPORT.md | 技术深度分析 |

## 关键 KEY_CODE
- HOME: 199
- END: 207
- PAGEUP: 201
- PAGEDOWN: 209

## 如果日志不显示

### 检查日志级别
确保启用了 DEBUG 级别日志
```
log4j2.xml 中设置 <Configuration level="DEBUG">
或启动参数: -Dlog4j2.level=DEBUG
```

### 检查 Mixin 加载
搜索日志中的 "Mixing in" 消息
```
[mixin] Mixing in...
[mixin] GuiKeyboardInputMixin
[mixin] MultiblockPreviewKeyMixin
[mixin] GUIKeyDownMixin
```

## 修复方案 (根据诊断结果)

### 如果是 ModularUI2 问题
需要添加新的 Mixin:
```java
@Mixin(ModularUIBase.class)
public class ModularUI2KeyboardMixin { ... }
```

### 如果是识别问题
更新识别规则:
```java
|| className.contains("modulargui")
|| className.contains("modularui")
```

### 如果是事件消费
使用 HEAD 注入和 cancellable:
```java
@Inject(method = "keyTyped(CI)V", at = @At("HEAD"), 
        require = 0, cancellable = true)
```

## HOME 键代码
```java
if (keyCode == key.getKeyCode()) {  // keyCode = 199 for HOME
    // 搜索触发
}
```

## 三个 Mixin 的覆盖范围

| Mixin | 目标 | 优先级 | 覆盖 GUI |
|-------|------|--------|----------|
| GuiKeyboardInputMixin | GuiScreen.handleKeyboardInput() | 低 | 所有 |
| MultiblockPreviewKeyMixin | GuiScreen.keyTyped() | 中 | 预览 |
| GUIKeyDownMixin | GuiContainer.keyTyped() | 高 | 容器 |

## 关键类名模式
- "structurelib" → StructureLib GUI
- "blockrenderer" → BlockRenderer GUI
- "multiblock" → 多方块相关
- "modulargui" → ModularUI 框架
- "nei" → NEI 面板

## 建议的后续步骤
1. 使用调试版测试 ✓ 已部署
2. 收集游戏日志
3. 根据诊断树定位问题
4. 应用相应修复
5. 重新编译和验证

---

**调试状态**: ✅ 就绪  
**编译状态**: ✅ 成功  
**可测试性**: ✅ 完全


