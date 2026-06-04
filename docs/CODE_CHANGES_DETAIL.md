# 代码修改详细对比

## 1. mixins.wikisearch.json - Mixin 配置

### 修改前
```json
{
  "required": true,
  "minVersion": "0.8.5-GTNH",
  "package": "com.czqwq.wikisearch.mixin",
  "compatibilityLevel": "JAVA_8",
  "refmap": "mixins.wikisearch.refmap.json",
  "client": [
    "GuiKeyboardInputMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

### 修改后
```json
{
  "required": true,
  "minVersion": "0.8.5-GTNH",
  "package": "com.czqwq.wikisearch.mixin",
  "compatibilityLevel": "JAVA_8",
  "refmap": "mixins.wikisearch.refmap.json",
  "client": [
    "GuiKeyboardInputMixin",
    "MultiblockPreviewKeyMixin",
    "GUIKeyDownMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

### 关键变更
**新增两个 mixin 的注册:**
- `MultiblockPreviewKeyMixin` - 处理多方块预览界面的键盘事件
- `GUIKeyDownMixin` - 处理容器界面（NEI）的键盘事件优化

---

## 2. GTNHWikiSearch.java - 核心逻辑类

### 修改前
```java
@SideOnly(Side.CLIENT)
private static void tryTriggerSearch(GuiScreen screen) {
    if (screen == null) {
        return;
    }

    HoveredStackContext context = HoveredStackContext
        .from(screen, Minecraft.getMinecraft(), Mouse.getX(), Mouse.getY());
    ItemStack hovered = STACK_RESOLVER.resolve(context);
    if (hovered != null) {
        search(hovered);
    }
}

/** GUI fallback for screens where Forge KeyInputEvent does not fire consistently. */
@SideOnly(Side.CLIENT)
public static void onGuiKeyboardEvent(GuiScreen screen) {
    if (key == null || screen == null || !Keyboard.getEventKeyState()) {
        return;
    }

    int eventKey = Keyboard.getEventKey();
    int keyCode = eventKey == 0 ? Keyboard.getEventCharacter() + 256 : eventKey;
    if (keyCode == key.getKeyCode()) {
        tryTriggerSearch(screen);
    }
}
```

### 修改后
```java
@SideOnly(Side.CLIENT)
private static void tryTriggerSearch(GuiScreen screen) {
    if (screen == null) {
        return;
    }

    HoveredStackContext context = HoveredStackContext
        .from(screen, Minecraft.getMinecraft(), Mouse.getX(), Mouse.getY());
    ItemStack hovered = STACK_RESOLVER.resolve(context);
    if (hovered != null) {
        search(hovered);
    }
}

/**
 * Unified keyboard event handler for all mixin injection points.
 * This method centralizes the search key press logic to avoid code duplication.
 */
@SideOnly(Side.CLIENT)
public static void handleSearchKeyPress(GuiScreen screen, char typedChar, int keyCode) {
    if (key == null || screen == null || keyCode != key.getKeyCode()) {
        return;
    }

    tryTriggerSearch(screen);
}

/** GUI fallback for screens where Forge KeyInputEvent does not fire consistently. */
@SideOnly(Side.CLIENT)
public static void onGuiKeyboardEvent(GuiScreen screen) {
    if (key == null || screen == null || !Keyboard.getEventKeyState()) {
        return;
    }

    int eventKey = Keyboard.getEventKey();
    int keyCode = eventKey == 0 ? Keyboard.getEventCharacter() + 256 : eventKey;
    if (keyCode == key.getKeyCode()) {
        tryTriggerSearch(screen);
    }
}
```

### 关键变更
**新增 `handleSearchKeyPress()` 方法:**
- 作为所有 mixin 的统一入口
- 统一检查键盘按键代码
- 调用 `tryTriggerSearch()` 执行搜索
- 减少代码重复和耦合

---

## 3. GuiKeyboardInputMixin.java

### 修改前
```java
@Mixin(GuiScreen.class)
public abstract class GuiKeyboardInputMixin extends GuiScreen {

    @Inject(method = "handleKeyboardInput()V", at = @At("TAIL"), require = 0)
    private void onKeyboardInput(CallbackInfo ci) {
        GTNHWikiSearch.onGuiKeyboardEvent(this);
    }
}
```

### 修改后
```java
@Mixin(GuiScreen.class)
public abstract class GuiKeyboardInputMixin {

    @Inject(method = "handleKeyboardInput()V", at = @At("TAIL"), require = 0)
    private void onKeyboardInput(CallbackInfo ci) {
        if (Keyboard.getEventKeyState()) {
            int eventKey = Keyboard.getEventKey();
            int keyCode = eventKey == 0 ? Keyboard.getEventCharacter() + 256 : eventKey;
            char typedChar = Keyboard.getEventCharacter();
            GTNHWikiSearch.handleSearchKeyPress((GuiScreen) (Object) this, typedChar, keyCode);
        }
    }
}
```

### 关键变更
1. **移除不正确的继承**: 删除 `extends GuiScreen`
   - Mixin 不应该继承被 Mixin 的类
   - 这会导致编译错误和运行时问题

2. **调用统一入口**: 改为调用 `handleSearchKeyPress()`
   - 传递必要的参数（typedChar, keyCode）
   - 让核心逻辑集中在一个地方

3. **完整的键盘检查**: 直接在 mixin 中进行键盘状态检查
   - 避免调用已弃用的 `onGuiKeyboardEvent()`
   - 更清晰的逻辑流

---

## 4. MultiblockPreviewKeyMixin.java

### 修改前
```java
@Mixin(GuiScreen.class)
public abstract class MultiblockPreviewKeyMixin {

    @Inject(method = "keyTyped(CI)V", at = @At("TAIL"), require = 0)
    public void onPreviewSearchKey(char typedChar, int keyCode, CallbackInfo ci) {
        if (GTNHWikiSearch.key == null || keyCode != GTNHWikiSearch.key.getKeyCode()) {
            return;
        }

        GuiScreen self = (GuiScreen) (Object) this;
        if (!MultiblockPreviewSearch.isLikelyPreviewScreen(self)) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        int mouseX = Mouse.getX() * self.width / mc.displayWidth;
        int mouseY = self.height - Mouse.getY() * self.height / mc.displayHeight - 1;

        ItemStack stack = MultiblockPreviewSearch.findHoveredStack(self, mouseX, mouseY);
        if (stack != null) {
            GTNHWikiSearch.search(stack);
        }
    }
}
```

### 修改后
```java
@Mixin(GuiScreen.class)
public abstract class MultiblockPreviewKeyMixin {

    @Inject(method = "keyTyped(CI)V", at = @At("TAIL"), require = 0)
    public void onPreviewSearchKey(char typedChar, int keyCode, CallbackInfo ci) {
        GuiScreen self = (GuiScreen) (Object) this;
        if (MultiblockPreviewSearch.isLikelyPreviewScreen(self)) {
            GTNHWikiSearch.handleSearchKeyPress(self, typedChar, keyCode);
        }
    }
}
```

### 关键变更
1. **移除重复的键盘检查**
   - 由 `handleSearchKeyPress()` 统一处理

2. **移除冗余的坐标计算**
   - 由 `HoveredStackResolver` 的 `PreviewReflectionHoveredStackProvider` 处理

3. **精简职责**
   - 仅保留多方块预览屏幕的类型判断
   - 调用统一的处理方法

---

## 5. GUIKeyDownMixin.java

### 修改前
```java
@Mixin(GuiContainer.class)
public abstract class GUIKeyDownMixin extends GuiScreen {

    @Inject(method = "keyTyped(CI)V", at = @At("TAIL"))
    public void onKeyInput(char typedChar, int keyCode, CallbackInfo ci) {
        if (GTNHWikiSearch.key != null && keyCode == GTNHWikiSearch.key.getKeyCode()) {
            ItemStack stack = GuiContainerManager.getStackMouseOver((GuiContainer) (Object) this);
            if (stack != null) {
                GTNHWikiSearch.search(stack);
            }
        }
    }
}
```

### 修改后
```java
@Mixin(GuiContainer.class)
public abstract class GUIKeyDownMixin {

    @Inject(method = "keyTyped(CI)V", at = @At("TAIL"))
    public void onKeyInput(char typedChar, int keyCode, CallbackInfo ci) {
        GTNHWikiSearch.handleSearchKeyPress((GuiContainer) (Object) this, typedChar, keyCode);
    }
}
```

### 关键变更
1. **移除不正确的继承**: 删除 `extends GuiScreen`
   - `GUIKeyDownMixin` 应该是一个纯粹的 Mixin 类
   - 不需要继承任何类

2. **简化为一行调用**: 直接调用 `handleSearchKeyPress()`
   - 所有的检查和逻辑都在统一入口处理
   - 代码更清晰，更易维护

3. **物品查找委托**: 让 `HoveredStackResolver` 处理物品查找
   - `GuiContainerHoveredStackProvider` 会调用 `GuiContainerManager.getStackMouseOver()`
   - 保持关注点分离

---

## 总结表格

| 组件 | 修改前行数 | 修改后行数 | 变化 | 目的 |
|------|----------|----------|------|------|
| mixins.wikisearch.json | 8 client: ["GuiKeyboardInputMixin"] | 10 client: [3 mixins] | +2 | 注册缺失的 mixin |
| GTNHWikiSearch.java | - | +14 lines | 新增 handleSearchKeyPress() | 统一入口 |
| GuiKeyboardInputMixin.java | 18 lines | 22 lines | 修复继承，改进逻辑 | 消除错误并调用统一入口 |
| MultiblockPreviewKeyMixin.java | 34 lines | 21 lines | -13 lines | 消除冗余代码 |
| GUIKeyDownMixin.java | 24 lines | 17 lines | -7 lines | 消除冗余代码 |

### 总体改进
- **代码行数**: 减少约 20 行重复代码
- **圈复杂度**: 在各个 mixin 中减少
- **耦合度**: 显著降低，通过统一入口实现
- **可维护性**: 大幅提升，集中管理键盘事件逻辑


