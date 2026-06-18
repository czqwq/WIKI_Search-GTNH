# WikiSearch 重构总结

## 概述

本次重构将 WikiSearch 模组从 **MediaWiki API 直接调用** 改为 **DuckDuckGo 搜索引擎检索**，同时完成了以下核心改进：

1. **搜索后端替换** — 从 MediaWiki API → DuckDuckGo HTML 搜索引擎
2. **字符串本地化** — 所有硬编码中文字符串迁移至 `.lang` 文件，通过 `StatCollector.translateToLocal()` 获取
3. **代码解耦** — 将"上帝类"拆分为职责单一的小模块

---

## 架构变更

### 旧架构 (重构前)

```
GTNHWikiSearch (key binding, search trigger)
    └── WikiSearchFetcher (HTTP + 解析 + 聊天格式化 — 全部混在一起)
            ├── fetchResults() — MediaWiki API 调用
            ├── parseResults() — JSON 解析
            ├── fetchAndDisplay() — 组合上述 + 直接拼接聊天消息
            └── pingAndDisplay() — ping 测试 + 直接拼接聊天消息
```

**问题：**
- `WikiSearchFetcher` 是高耦合低内聚的"上帝类"：HTTP、JSON 解析、聊天格式化全部揉在一起
- 所有用户可见字符串硬编码在 Java 代码中 (中文)，无法切换语言
- 搜索后端不可替换 (紧耦合到 MediaWiki API)

### 新架构 (重构后)

```
GTNHWikiSearch (key binding, search trigger)
    └── WikiSearchFetcher (编排层 — 线程管理 + 调用搜索 + 调用格式化)
            ├── SearchEngine (接口) ←── 可插拔搜索后端
            │       └── DuckDuckGoSearchEngine (Jsoup 解析 DDG HTML)
            └── ChatFormatter (纯展示 — 本地化字符串 → 聊天组件)
                    └── .lang 文件 (zh_CN.lang / en_US.lang)

Config (配置层 — 管理所有可配置常量)
    ├── wikiSearchDomain — 搜索引擎限定域名字段 (新增)
    └── 其他已有字段不变
```

**改进：**
- **单一职责：** `SearchEngine` 只负责搜索，`ChatFormatter` 只负责格式化，`WikiSearchFetcher` 只负责编排
- **开闭原则：** 新增搜索引擎只需实现 `SearchEngine` 接口，无需修改现有代码
- **依赖倒置：** `WikiSearchFetcher` 依赖 `SearchEngine` 接口而非具体实现
- **可测试性：** 可以通过 `WikiSearchFetcher.setEngine()` 注入 mock 实现

---

## 文件变更清单

### 新增文件

| 文件 | 职责 |
|------|------|
| `SearchEngine.java` | 搜索后端抽象接口，包含 `SearchResult` 数据类 |
| `DuckDuckGoSearchEngine.java` | DuckDuckGo HTML 搜索引擎实现 (基于 Jsoup) |
| `ChatFormatter.java` | 聊天消息格式化工具类，所有字符串通过 `.lang` 文件获取 |
| `docs/refactor-summary.md` | 本文档 |

### 修改文件

| 文件 | 变更内容 |
|------|----------|
| `WikiSearchFetcher.java` | 完全重写：移除 MediaWiki API 调用和 JSON 解析，改为委托 `SearchEngine` 和 `ChatFormatter` |
| `WikiSearchCommand.java` | 移除所有硬编码中文字符串，改为调用 `ChatFormatter` 方法 |
| `LocalAuthServer.java` | `applyAndNotify()` 方法移除硬编码字符串，改为 `ChatFormatter.displayAuthSuccess()` |
| `Config.java` | 新增 `wikiSearchDomain` 配置字段 (DuckDuckGo `site:` 语法使用) |
| `build.gradle.kts` | 新增 Jsoup 依赖 (`org.jsoup:jsoup:1.14.3`) |
| `zh_CN.lang` | 新增 15 个本地化键 |
| `en_US.lang` | 新增 15 个本地化键 |

### 未修改文件

| 文件 | 原因 |
|------|------|
| `GTNHWikiSearch.java` | 仅包含 key binding 注册和搜索触发，职责边界清晰 |
| `ClientProxy.java` | 初始化逻辑无变化 |
| `CommonProxy.java` | 服务端代理无变化 |
| `ChromeLikeSSLSocketFactory.java` | TLS 指纹伪装独立工具类，无需变动 |
| `GUIKeyDownMixin.java` | Mixin 注入逻辑无变化 |

---

## 新增本地化键

所有键均以 `wikisearch.` 为前缀，通过 `StatCollector.translateToLocal("wikisearch.<key>")` 获取。

### 搜索结果

| 键 | 中文 | 英文 |
|----|------|------|
| `wikisearch.search.header` | 搜索 "%1$s" 的结果 (%2$d 项): | Search results for "%1$s" (%2$d items): |
| `wikisearch.search.no_results` | 未找到 "%1$s" 的相关页面。 | No pages found for "%1$s". |
| `wikisearch.search.open_button` | [打开] | [Open] |
| `wikisearch.search.error_403` | 搜索请求失败 (...)，请尝试使用 /wikisearch auth... | Search request failed (...). Try /wikisearch auth... |
| `wikisearch.search.error_generic` | 搜索请求失败 (...)，详情请查看日志。 | Search request failed (...). See log for details. |
| `wikisearch.search.unknown_error` | 未知错误 | Unknown error |

### Ping

| 键 | 中文 | 英文 |
|----|------|------|
| `wikisearch.ping.start` | 正在 ping %1$s ... | Pinging %1$s ... |
| `wikisearch.ping.success` | ✔ %1$s (%2$s) 可达，耗时 %3$d ms | ✔ %1$s (%2$s) reachable, took %3$d ms |
| `wikisearch.ping.success_tcp` | ✔ %1$s (%2$s) TCP:80 可达，耗时 %3$d ms | ✔ %1$s (%2$s) TCP:80 reachable, took %3$d ms |
| `wikisearch.ping.failure` | ✘ %1$s (%2$s) 不可达: %3$s | ✘ %1$s (%2$s) unreachable: %3$s |
| `wikisearch.ping.error` | ✘ ping %1$s 失败... | ✘ Ping %1$s failed... |

### 认证

| 键 | 中文 | 英文 |
|----|------|------|
| `wikisearch.auth.ports_exhausted` | 无法启动本地认证服务器 (端口 %1$d - %2$d 均被占用)。 | Cannot start local auth server (ports %1$d - %2$d are all in use). |
| `wikisearch.auth.server_started` | 正在打开浏览器认证助手... %1$s | Opening browser auth helper... %1$s |
| `wikisearch.auth.browser_failed` | 无法自动打开浏览器，请手动访问: %1$s | Cannot open browser automatically. Please manually visit: %1$s |
| `wikisearch.auth.success` | ✓ 认证成功！Cookie 已自动保存... | ✓ Auth successful! Cookie saved... |

### 命令

| 键 | 中文 | 英文 |
|----|------|------|
| `wikisearch.command.config_reloaded` | 配置已从本地文件重新加载。 | Configuration reloaded from local file. |
| `wikisearch.command.cookie_saved` | Cookie 已设置并保存到本地配置文件。 | Cookie set and saved to local configuration. |
| `wikisearch.command.usage_prefix` | 用法: | Usage: |

---

## DuckDuckGo 搜索引擎工作原理

1. 构建 `site:<wikiDomain> <keyword>` 查询
2. 请求 `https://html.duckduckgo.com/html/?q=<encodedQuery>` (非 JS 版本)
3. 使用 Jsoup 解析返回的 HTML
4. 从 `a.result__url` 选择器提取结果链接
5. 解析 DDG 的 `uddg=` 重定向参数获取真实 URL
6. 从 URL 路径推导页面标题

**优势：** DuckDuckGo 的 HTML 端点不像 MediaWiki API 那样需要 Cloudflare cookie，普通请求即可获取结果。

---

## 向后兼容

- 所有公开 API (`WikiSearchFetcher.fetchAndDisplay()`, `WikiSearchFetcher.pingAndDisplay()`) 签名不变
- 配置文件新增 `wikiSearchDomain` 字段，但有默认值 (`gtnh.huijiwiki.com`)，已有配置无需修改
- `searchApiUrl` 和 `wikiPageBase` 配置字段保留 (可用于未来切换回 Wiki API)
- `/wikisearch auth`, `/wikisearch cookie`, `/wikisearch ping`, `/wikisearch reload` 命令行为不变
- 按键绑定 (HOME 键) 行为不变

---

## 扩展指南

### 切换回 MediaWiki API (或添加其他搜索引擎)

1. 实现 `SearchEngine` 接口
2. 调用 `WikiSearchFetcher.setEngine(new YourEngine())`

```java
public class WikiApiSearchEngine implements SearchEngine {
    @Override
    public List<SearchResult> search(String keyword) throws Exception {
        // 使用 Config.searchApiUrl 和 Config.cookie 调用 MediaWiki API
    }
}
```

### 添加新语言

在 `src/main/resources/assets/wikisearch/lang/` 下创建 `<lang_code>.lang` 文件，按相同键名翻译即可。

---

## 构建验证

```
$ ./gradlew compileJava
BUILD SUCCESSFUL in 1m 49s
```

仅有一个预存在的 `unchecked cast` 警告 (来自 `WikiSearchCommand.addTabCompletionOptions` 的 raw types 使用)，非本次重构引入。

---

## 第二轮改进 (2026-06-19)

### 1. 搜索标题精简

**问题：** DuckDuckGo 返回的页面标题包含冗长的 wiki 站名后缀，例如：
> 巨型工业高炉 - GTNH 中文维基 - 灰机wiki - 北京嘉闻杰诺网络科技有限公司

**修复：**
- `Config.java` 新增 `titleStripSuffixes` 配置项 (逗号分隔的后缀黑名单)
- 默认值自动去除 `" - GTNH 中文维基 - 灰机wiki - 北京嘉闻杰诺网络科技有限公司"`
- `DuckDuckGoSearchEngine.stripTitleSuffixes()` 方法负责剔除匹配的后缀
- DDG 解析器改用 `.result__body` 容器 + `.result__a` 标题选择器，从源头获取更准确的页面标题

**效果：**
> 修复前: `1. 巨型工业高炉 - GTNH 中文维基 - 灰机wiki - 北京嘉闻杰诺网络科技有限公司 [打开]`
> 修复后: `1. 巨型工业高炉 [打开]`

### 2. NEI / BlockRenderer6343 多方块预览界面搜索键修复

**问题：** 在 BlockRenderer6343 的多方块结构预览 (NEI recipe GUI) 中，按下搜索键 (HOME) 后 `GuiContainerManager.getStackMouseOver()` 可正常获取物品，但 `GTNHWikiSearch.search()` 未被调用，无法触发搜索。

**根因分析：**

```
GuiScreen.keyTyped()                    ← 空实现
    ↑ override
GuiContainer.keyTyped()                 ← 我们的 GUIKeyDownMixin 注入点 ✓
    ↑ override
GuiRecipe.keyTyped()                    ← NEI 完全覆盖，不调 super ❌
```

NEI 的 `GuiRecipe.keyTyped()` 是一个完全独立的实现——它处理 NEI 搜索框、ESC 关闭、书签快捷键、翻页键后直接 `return`，**从未调用 `super.keyTyped()`**。因此我们注入在 `GuiContainer.keyTyped` 的 Mixin 在这个 GUI 内永远不会触发。

**修复：** 新增 `GuiRecipeKeyDownMixin.java`，直接在 `GuiRecipe.keyTyped` 的 `@HEAD` 注入：

```java
@Mixin(GuiRecipe.class)
public abstract class GuiRecipeKeyDownMixin extends GuiScreen {
    @Inject(method = "keyTyped(CI)V", at = @At("HEAD"))
    public void onKeyInput(...) {
        // 与 GUIKeyDownMixin 完全一致的搜索键拦截逻辑
    }
}
```

**注册：** `mixins.wikisearch.json` 新增 `"GuiRecipeKeyDownMixin"`。

---

## 第三轮改进 (2026-06-19)

### 1. titleStripSuffixes 改用 JSON 数组格式

**变更：**

| 项目 | 旧格式 | 新格式 |
|------|--------|--------|
| 配置类型 | `String` (逗号分隔) | `List<String>` (JSON 数组解析) |
| 默认值 | `" - GTNH 中文维基 - 灰机wiki - 北京嘉闻杰诺网络科技有限公司"` | `[" - GTNH 中文维基 - 灰机wiki - 北京嘉闻杰诺网络科技有限公司"]` |
| 用户配置示例 | `" (页面), - 维基百科"` | `[" (页面)", " - 维基百科", " (消歧义)"]` |
| 解析方式 | `String.split(",")` | `new JsonParser().parse(raw).getAsJsonArray()` |

**行为：** 只剔除标题末尾匹配的后缀文字，不会移除整个条目。例如标题 `"石头 (页面)"` 配置 `[" (页面)"]` → 显示为 `"石头"`。

**错误处理：** JSON 格式错误时打印 WARN 日志并回退为空列表（不剔除任何后缀）。

### 2. 无搜索结果时的诊断日志

在 `DuckDuckGoSearchEngine.parseResults()` 中，当没有任何结果时输出详细 DEBUG 日志：

```
[WikiSearch] DuckDuckGo returned no results.
  keyword='<关键词>', domain='<域名>', fullQuery='site:<域名> <关键词>',
  bodiesFound=<N>, urlLinksFound=<M>, docTitle='<页面标题>', docUrl='<请求URL>'
```

这可以区分：
- **网络问题** → `docTitle` 为空或 `docUrl` 不是 DDG
- **选择器不匹配** → `bodiesFound`/`urlLinksFound` 有值但无结果
- **域名过滤过严** → `urlLinksFound` 有值但全被 `Config.wikiSearchDomain` 过滤

### 3. /wikisearch reload 验证

确认重载流程正确：

```
WikiSearchCommand.processCommand("reload")
  → Config.reload()
    → config.load()        // Forge Configuration 从磁盘重读 .cfg 文件
    → load()               // 重新读取所有字段 (含 titleStripSuffixes)
```

所有配置项均通过 `load()` 统一读取，reload 后即时生效。

### 4. mixin加载
确认 "GuiRecipeKeyDownMixin" 不在 `mixins.wikisearch.json`中被加载

它调用LateMixinPlugin加载,调用mixin.late.json作为配置文件,而且只在加载完模组后才注入mixin,本身通过一个List列表加载,因此不需要在mixin.late.json中声明

