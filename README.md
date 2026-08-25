# Jenkins Server Monitor

Jenkins 插件，在 Jenkins 顶部 Navbar 区域显示 Jenkins 所在服务器的 CPU、内存、存储、网络和磁盘 IO 情况。

## 功能

- 顶部 Navbar 区域以固定宽度的 Element 风格进度条显示 CPU、内存、存储使用率
- 显示内存和磁盘可用容量
- 显示系统负载和最近更新时间
- 首次加载立即读取，之后默认每 2 秒自动轮询一次，可在全局配置中调整为 2～60 秒
- 使用单请求轮询、超时取消和页面生命周期清理，避免长时间停留产生请求或定时器累积
- Linux 优先读取 `/proc/meminfo`，按可回收缓存/缓冲区修正内存使用率，参考宝塔等服务器面板的统计口径
- 磁盘路径支持绝对路径，也支持相对于 Jenkins 主目录的路径
- 显示网络上传/下载速率，以及磁盘读取、写入、IOPS 和 IO 延迟
- 支持中文和英文，跟随 Jenkins 页面语言或浏览器语言显示
- 通过 Jenkins `PageDecorator`、`RootAction` 和 `GlobalConfiguration` 扩展点实现，不修改 Jenkins 核心文件
- 状态接口要求当前用户具备 `Overall/Read` 权限

## 构建

需要 JDK 11 或更高版本以及 Maven：

```bash
mvn -U clean package -DskipTests
```

构建产物：

```text
target/jenkins-server-monitor.hpi
```

## 安装

在 Jenkins 中进入：

1. `Manage Jenkins` → `Plugins` → `Advanced settings`
2. 在 `Deploy Plugin` 中上传 `jenkins-server-monitor.hpi`
3. 重启 Jenkins，或按 Jenkins 提示完成动态加载

安装后进入 `Manage Jenkins` → `System`，在 `Jenkins 服务器资源监控` 中配置刷新间隔和磁盘路径。

## 更新兼容性

插件只通过公开扩展点加载页面资源和状态接口，不替换 Jenkins 核心文件，因此 Jenkins 更新不会覆盖插件代码。插件本身仍然需要满足 Jenkins 的最低版本要求；如果未来 Jenkins 核心 API 发生不兼容变化，需要重新构建或发布插件版本。

当前工程使用 Jenkins 插件父 POM 4.86，最低 Jenkins 基线为 2.361，编译目标为 Java 11，可在 JDK 17 上构建，不要求 JDK 21。

## 状态接口

登录 Jenkins 后可以通过以下地址查看 JSON：

```text
/server-monitor/status
```

接口由 Jenkins 权限系统保护，未授权用户无法读取服务器资源信息。
