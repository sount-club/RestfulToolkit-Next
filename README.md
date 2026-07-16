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

## 功能特性

| 功能 | 说明 |
|------|------|
| **URL 跳转** | `Ctrl + \` / `Cmd + \` 输入 URL 快速定位到接口方法定义 |
| **统一搜索** | 通过 URL 跳转窗口搜索 REST 接口，支持路径、模块、Controller、方法名和描述 |
| **URL 生成** | 在接口方法上右键生成并复制完整 URL / 相对路径 URL |
| **参数生成** | 生成并复制 Query 参数（Key-Value）和 RequestBody（JSON） |
| **JSON 转换** | 将 Java / Kotlin 类转换为 JSON 或 Bulk Value |
| **框架支持** | Spring MVC / Spring Boot、JAX-RS、Java、Kotlin（含 K2） |

## 快速开始

### 安装

**方式一：从 JetBrains Marketplace 安装（推荐）**

> 在 IDE 中 `Settings → Plugins → Marketplace` 搜索 *RestfulToolkit Next*

安装后重启 IDE，即可使用 `Ctrl + \` / `Cmd + \` 打开接口搜索；也可在 Controller 方法、Java / Kotlin 类上通过右键菜单调用对应功能。

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

### 1. 搜索并跳转到接口定义

在任意编辑器位置按 `Ctrl + \` 或 `Ctrl + Alt + N`（Windows/Linux），或 `Command + \`（macOS），打开 REST 接口搜索窗口。输入关键词后，选择目标结果并按 `Enter` 或双击，即可跳转到对应的 Controller 方法。

如果剪贴板中已有完整请求地址，例如 `https://example.com/api/users/123`，打开窗口时会自动带入该地址；直接确认搜索即可定位接口。

支持的输入方式如下：

| 想查找的内容 | 输入示例 |
|---|---|
| 接口路径或路径片段 | `/api/users`、`users` |
| 指定 HTTP Method 的接口 | `GET /api/users`、`POST users` |
| Controller 与方法 | `UserController#getUser`、`UserController getUser` |
| 模块名或接口描述 | 输入模块名称、接口注释中的关键词 |

搜索结果会展示接口的 Method、Path 及来源信息。同一个 Method + Path 在多个模块或 Controller 中存在时，所有匹配项都会保留，可根据副信息选择正确来源。

真实请求路径也可以匹配参数化路由：例如搜索 `/users/123` 时，能够找到定义为 `/users/{id}` 的接口。项目刚启动或 IDE 正在建立索引时，窗口会提示当前状态；索引完成后会自动刷新搜索结果和模块筛选项。

#### 按模块缩小范围

当项目包含多个模块时，可在搜索窗口中选择模块筛选项，只查看该模块的接口。清除筛选即可恢复全项目搜索。

#### 配置网关前缀

如果线上请求地址带有网关前缀，可在搜索窗口模块筛选右侧点击齿轮图标，填写需要在匹配前剥离的前缀；多个前缀以逗号或换行分隔：

```text
/gateway, /api
```

例如 `/gateway/app/users/123`，模块配置了 `server.servlet.context-path=/app` 时，插件会依次尝试原始路径、剥离网关前缀后的路径，以及 `/users/123`，从而匹配项目中的接口定义。也兼容旧配置项 `server.context-path`。

### 2. 在接口方法上生成请求信息

将光标放在 Spring Controller 或 JAX-RS 的接口方法内，右键选择 `RestfulToolkit Next`。执行以下动作后，内容会直接复制到剪贴板，可粘贴到浏览器、Postman 或 API 调试工具中：

| 菜单动作 | 生成内容 | 常见用途 |
|---|---|---|
| **Generate && Copy Full URL** | 完整请求地址，包含 host、port、context-path 和查询参数 | 直接发起或分享请求 |
| **Generate && Copy Relation URL** | 相对请求路径 | 填写网关路由、接口文档 |
| **Generate && Copy RequestBody (JSON)** | 方法中 `@RequestBody` 参数对应的 JSON 示例 | 构造 POST / PUT 请求体 |
| **Generate && Copy Query Param (Key Value)** | `Key=Value` 格式的查询参数 | 粘贴到 Postman Bulk Edit 等参数编辑区 |

### 3. 由类快速生成 JSON 示例

将光标放在 Java 或 Kotlin 类声明内，右键选择 `RestfulToolkit Next`，可将类字段转换为以下内容并复制到剪贴板：

| 菜单动作 | 生成内容 |
|---|---|
| **Convert to JSON** | 带缩进的 JSON 示例，适合阅读、接口文档和请求体编辑 |
| **Convert to JSON (Compressed)** | 单行压缩 JSON，适合日志、脚本或紧凑参数 |
| **Convert to Bulk Value** | Bulk Value 格式的字段值，适合批量参数录入 |

### 4. 常见使用流程

1. 从浏览器、日志或 API 文档复制一个请求 URL。
2. 在 IDEA 中按快捷键打开 REST 接口搜索，确认目标接口并跳转到实现。
3. 在接口方法上右键生成完整 URL、查询参数或 RequestBody JSON。
4. 需要调整请求模型时，在对应 Java / Kotlin DTO 上右键生成新的 JSON 示例。

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
