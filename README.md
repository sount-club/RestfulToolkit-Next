# RestfulToolkit Next

`RestfulToolkit Next` 是一个基于原始项目 `RestfulToolkit` 进行二次开发和重新发布的 IntelliJ IDEA 插件项目。

本仓库的目标不是简单镜像原仓库，而是在保留原有核心能力的基础上，针对新的 JetBrains Platform / IntelliJ IDEA 版本做兼容性修复、资源整理和持续维护。

## 二次开发说明

- 原始项目仓库：<https://github.com/mrmanzhaow/RestfulToolkit>
- 本项目基于原项目代码进行二次开发。
- 当前仓库主要面向新版 IDEA 平台做适配，并以 `RestfulToolkit Next` 的名称重新发布。
- 本项目保留了原插件“围绕 REST 接口导航和辅助操作”的核心设计思路，同时对工程结构、图标资源和插件元数据进行了整理。

如果你正在寻找原始版本，请直接访问上面的原仓库；如果你需要一个可继续维护、可面向新版 IDEA 使用的分支版本，这个仓库就是对应的延续实现。

## 主要功能

- 根据 URL 快速跳转到对应的 Controller / Service 声明
- 展示 REST 服务结构树
- 生成并复制接口 URL、完整 URL、请求参数等内容
- 提供部分类与方法级辅助能力，例如 JSON 相关转换
- 支持 Spring MVC / Spring Boot
- 支持 JAX-RS
- 支持 Java 和 Kotlin

## 安装方式

### 方式一：从打包产物安装

先执行：

```bash
./gradlew buildPlugin
```

然后在 IDE 中打开：

`Settings/Preferences` -> `Plugins` -> 齿轮图标 -> `Install Plugin from Disk...`

选择 `build/distributions/` 目录下生成的 ZIP 包安装即可。

### 方式二：开发模式运行

在项目根目录执行：

```bash
./gradlew runIde
```

Gradle 会启动一个带有当前插件的测试 IDE 实例，适合本地调试和二次开发。

## 使用方式

### 1. 根据 URL 快速跳转到接口定义

- 使用快捷键 `Ctrl + \` 或 `Ctrl + Alt + N`
- macOS 下使用 `Command + \`
- 会弹出 URL 导航面板，输入接口路径后即可跳转到对应方法
- 如果剪贴板中已经复制了一个 HTTP URL，插件会优先把它作为预填内容

适用场景：

- 已知线上或本地接口 URL，想反查到 Controller 方法
- 需要在大型项目中快速定位某个 REST 接口定义

### 2. 查看 REST 服务树

- 通过 IDE 的 `Find Action` 搜索 `Refresh Services in Project`
- 执行后，插件会初始化右侧的 `RestServices` 工具窗口
- 工具窗口中会按项目结构展示扫描到的 REST 接口
- 你可以在树节点上使用右键菜单执行 `Copy Full Url` 或 `Jump to Source`

适用场景：

- 想按模块或接口列表浏览项目中的 REST 服务
- 想从树结构中快速复制完整地址或回到源码

### 3. 在接口方法上使用右键菜单

在 Spring Controller 方法或部分 JAX-RS 接口方法上右键，可以看到插件提供的操作项：

- `Generate && Copy Full URL`
- `Generate && Copy Relation URL`
- `Generate && Copy RequestBody (JSON)`
- `Generate && Copy Query Param (Key Value)`

这些操作适合用于：

- 快速生成调试请求地址
- 复制请求参数到 Postman、Apifox 或其他调试工具
- 快速查看方法参数生成效果

### 4. 在 Java / Kotlin 类上使用 JSON 辅助功能

在 Java 类或 Kotlin 类上右键，可以使用：

- `Convert to JSON`
- `Convert to JSON (Compressed)`
- `Convert to Bulk Value`

这些功能适合在定义 DTO / VO / Request Body 时快速生成示例数据。

### 5. 推荐的典型使用流程

1. 在项目中打开一个 Spring Boot 或 JAX-RS 工程。
2. 执行 `Refresh Services in Project` 初始化 `RestServices` 工具窗口。
3. 通过服务树浏览接口，或直接使用 `Ctrl + \` / `Command + \` 按 URL 跳转。
4. 在目标接口方法上右键，生成并复制 URL、参数或请求体。
5. 如需构造示例 JSON，可在相关 Java / Kotlin 类上直接执行转换操作。

## 当前维护方向

- 适配较新的 IntelliJ IDEA / JetBrains Platform 版本
- 修复旧版本插件在新 IDE 中的兼容性问题
- 保持核心 REST 导航能力可用
- 对插件名称、图标和打包产物进行重新整理，便于后续发布

## 构建

在项目根目录执行：

```bash
./gradlew build
```

生成可发布插件包：

```bash
./gradlew buildPlugin
```

构建产物默认位于：

```text
build/distributions/
```

## 当前版本

- 插件名称：`RestfulToolkit Next`
- 当前工程产物名：`RestfulToolkit-next`

## 致谢

感谢原始项目 `RestfulToolkit` 的作者和贡献者提供基础实现。本项目是在原有工作的基础上继续维护和演进的二次开发版本。
