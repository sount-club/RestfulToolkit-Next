# RestfulToolkit Next

<p align="center">
  <strong>一套用于 RESTful 服务开发的辅助工具集，基于 <a href="https://github.com/mrmanzhaow/RestfulToolkit">RestfulToolkit</a> 二次开发</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/IntelliJ-2026.1+-blue" alt="IntelliJ Platform">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Kotlin-2.2+-purple" alt="Kotlin 2.2+">
  <img src="https://img.shields.io/badge/License-Apache%202.0-green" alt="License">
</p>

---

## 1.1.2 更新

- 修复索引重建和导航中的线程安全问题（retryCount 改为 AtomicInteger、移除 getter 触发重建的竞态条件）
- 增强导航稳定性：EndpointNavigationTarget 降级导航、RestServiceItem 增加 tryNavigate 失败检测
- 重构端点索引错误处理与状态跟踪：引入 EndpointResolutionException、ensureRebuildScheduled 和 RebuildResult
- 优化异步搜索背景执行，避免 EDT 阻塞，并增加搜索结果应用回调
- 改进导航失败的用户反馈：SearchPopupActions 显示状态信息而非静默返回
- 修复 GenerateUrlAction 中的单例状态泄漏
- 优化 SpringWebFlux 解析器：移除低效的 FileTypeIndex 全量文件扫描，改用关键词预过滤
- 改进 Spring 占位符解析和 JSON 格式化错误处理
- 增强 Kotlin 端点解析器的类型安全检查
- 为 SearchHistory 添加同步保护和状态复制机制

---

## 1.1.1 更新

- 修正 README 与插件发布说明中已过期的 `RestServices` 工具窗口描述，统一为当前的统一搜索窗口和编辑器右键能力
- 补齐插件服务注册测试，覆盖 `EndpointIndex` 的 `plugin.xml` 注册和 `SearchHistory` 的项目级服务注解
- 抽取 `EndpointDescriptor` 作为 REST 接口的纯数据模型，降低导航项、搜索字段和 PSI 元素之间的耦合
- 抽取 `SearchPopupModel` 承载统一搜索窗口的纯逻辑，减少 `UnifiedSearchPopup` 中的非 UI 职责
- 新增 `ServiceResolverRegistry`，集中管理 Spring 与 JAX-RS resolver 注册，方便后续扩展更多框架解析器
- 拆分 Spring Java / Kotlin 接口解析协作者，缩小 `SpringResolver` 的编排职责
- 补充架构约束测试并通过 `./gradlew test` 与 `./gradlew build` 验证

---

## 1.1.0 更新

- URL 跳转面板升级为统一搜索窗口，支持按路径、HTTP Method、模块名、Controller、方法名和接口描述搜索
- 支持 `UserController#getUser`、`UserController getUser` 等 Controller / Method 组合查询
- 同 Method + Path 的重复接口不再被隐藏，搜索结果会保留不同 Controller / Module 的来源信息
- 搜索窗口在项目启动和 IDE 索引期间会显示索引中状态，索引完成后自动刷新接口结果和模块过滤器
- 搜索窗口文案支持中英文多语言，并集中维护资源 key
- 优化搜索性能：缓存搜索选择 key、空搜索最近访问列表使用 Top 20 策略、结果渲染复用模块标签组件

---

## 1.0.1 更新

- 编辑器右键菜单统一收拢到 `RestfulToolkit Next` 子菜单
- 修复接口方法与类转换动作在 Java / Kotlin 中经常无法通过右键触发的问题
- URL 跳转入口升级为统一搜索窗口，支持按路径、HTTP Method、模块名和源码位置过滤
- 接口方法右键菜单支持生成完整 URL、相对 URL、Query 参数和 RequestBody JSON

---

## 功能特性

| 功能 | 说明 |
|------|------|
| **URL 跳转** | `Ctrl + \` / `Cmd + \` 输入 URL 快速定位到接口方法定义 |
| **统一搜索** | 通过 URL 跳转面板搜索 REST 接口，支持路径、模块、Controller、方法名和描述 |
| **URL 生成** | 在接口方法上右键生成并复制完整 URL / 相对路径 URL |
| **参数生成** | 生成并复制 Query 参数（Key-Value）和 RequestBody（JSON） |
| **JSON 转换** | 将 Java / Kotlin 类转换为 JSON 或 Bulk Value |
| **框架支持** | Spring MVC / Spring Boot、JAX-RS、Java、Kotlin（含 K2） |

## 快速开始

### 安装

**方式一：从 JetBrains Marketplace 安装（推荐）**

> 在 IDE 中 `Settings → Plugins → Marketplace` 搜索 *RestfulToolkit Next*

**方式二：从源码构建**

```bash
./gradlew buildPlugin
```

在 IDE 中打开 `Settings → Plugins → ⚙️ → Install Plugin from Disk...`，选择 `build/distributions/` 下的 ZIP 包。

**方式三：开发调试**

```bash
./gradlew runIde
```

## 使用方式

### 1. 根据 URL 跳转到接口定义

- 快捷键：`Ctrl + \` 或 `Ctrl + Alt + N`（Windows/Linux）、`Command + \`（macOS）
- 弹出统一搜索窗口，输入接口路径即可跳转
- 如果剪贴板中已有 HTTP URL，会自动作为预填内容
- 搜索支持：
  - 路径片段，例如 `/api/users` 或 `users`
  - HTTP Method，例如 `GET /api/users`
  - Controller / Method，例如 `UserController#getUser` 或 `UserController getUser`
  - 模块名和接口描述
- 当多个模块或 Controller 暴露相同 Method + Path 时，搜索结果会同时显示，副信息用于区分来源
- 项目刚启动或 IDE 正在索引时，窗口会显示索引中状态；索引完成后会自动刷新结果和模块列表

### 2. 在接口方法上使用右键菜单

在 Spring Controller 或 JAX-RS 方法上右键，进入 `RestfulToolkit Next` 子菜单：

- **Generate && Copy Full URL** — 生成完整请求地址
- **Generate && Copy Relation URL** — 生成相对路径
- **Generate && Copy RequestBody (JSON)** — 生成请求体 JSON
- **Generate && Copy Query Param (Key Value)** — 生成查询参数

### 3. JSON 转换

在 Java / Kotlin 类上右键，进入 `RestfulToolkit Next` 子菜单：

- **Convert to JSON** — 转换为格式化 JSON
- **Convert to JSON (Compressed)** — 转换为压缩 JSON
- **Convert to Bulk Value** — 转换为 Bulk Value

## 多语言

- 搜索窗口、结果列表、预览区和状态栏文案支持英文与中文资源
- 资源文件位于 `src/main/resources/RestfulToolkitBundle.properties` 和 `src/main/resources/RestfulToolkitBundle_zh.properties`
- 资源 key 统一维护在 `RestfulToolkitBundle.Keys` 中，新增窗口文案时应先补充 key 常量，再更新中英文资源文件

## 相比原项目的技术改进

- 全面迁移已废弃的 IntelliJ Platform API，确保与未来版本兼容：

  | 原始 API（已废弃） | 替代方案 |
  |---|---|
  | `CommonBundle.message()` | `DynamicBundle` |
  | `DataProvider.getData(String)` | `UiDataProvider.uiDataSnapshot(DataSink)` |
  | `DumbService.runReadActionInSmartMode()` | `ReadAction.nonBlocking().inSmartMode().executeSynchronously()` |
  | `JavaShortClassNameIndex.get()` | `PsiShortNamesCache.getClassesByName()` |
  | `FilenameIndex.getFilesByName()` | `FilenameIndex.getVirtualFilesByName()` |
  | `JavaAnnotationIndex.get()` | `JavaAnnotationIndex.getAnnotations()` |
  | `StartupManager.registerPostStartupActivity()` | `DumbService.runWhenSmart()` |
  | `DisposeAwareRunnable.create()` | 手动 `isDisposed()` 检查 |
  | `NameUtil.buildMatcher(String, MatchingMode)` | `NameUtil.buildMatcher(String).withMatchingMode().build()` |
  | `ChooseByNameModel.getCheckBoxMnemonic()` | 已移除 |

- 支持 Kotlin K2 编译模式
- 构建系统升级至 IntelliJ Platform Gradle Plugin 2.x
- 目标平台 IntelliJ IDEA 2026.1+（Build 261+）
- REST endpoint 索引在 Smart Mode 下异步构建，避免启动期阻塞 UI，并在索引完成后自动刷新搜索窗口
- 搜索路径避免高频 PSI 读取，预计算 Controller、方法名、描述和 lowercase 搜索字段

## 兼容性

| 项目 | 要求 |
|------|------|
| IntelliJ IDEA | 2026.1+ (Build 261+) |
| Java | 21 |
| Kotlin | 2.2+ |
| 依赖插件 | Java、Kotlin |

## 构建

```bash
# 编译
./gradlew build

# 生成可发布插件包
./gradlew buildPlugin

# 产物位于 build/distributions/
```

## 致谢

感谢 [RestfulToolkit](https://github.com/mrmanzhaow/RestfulToolkit) 原作者 [@mrmanzhaow](https://github.com/mrmanzhaow) 及所有贡献者的开源工作。本项目在原有基础上适配新版 IntelliJ 平台并持续维护。

## 许可证

[Apache License 2.0](LICENSE.txt)
