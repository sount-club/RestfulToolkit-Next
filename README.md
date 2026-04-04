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

## 功能特性

| 功能 | 说明 |
|------|------|
| **URL 跳转** | `Ctrl + \` / `Cmd + \` 输入 URL 快速定位到接口方法定义 |
| **服务树浏览** | 通过 *RestServices* 工具窗口按项目结构浏览 REST 接口 |
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
- 弹出 URL 导航面板，输入接口路径即可跳转
- 如果剪贴板中已有 HTTP URL，会自动作为预填内容

### 2. 浏览 REST 服务树

- 通过 `Find Action` 搜索 `Refresh Services in Project` 初始化
- 右侧 *RestServices* 工具窗口按项目结构展示 REST 接口
- 右键节点可 `Copy Full URL` 或 `Jump to Source`

### 3. 在接口方法上使用右键菜单

在 Spring Controller 或 JAX-RS 方法上右键：

- **Generate && Copy Full URL** — 生成完整请求地址
- **Generate && Copy Relation URL** — 生成相对路径
- **Generate && Copy RequestBody (JSON)** — 生成请求体 JSON
- **Generate && Copy Query Param (Key Value)** — 生成查询参数

### 4. JSON 转换

在 Java / Kotlin 类上右键：

- **Convert to JSON** — 转换为格式化 JSON
- **Convert to JSON (Compressed)** — 转换为压缩 JSON
- **Convert to Bulk Value** — 转换为 Bulk Value

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

[Apache License 2.0](LICENSE)
