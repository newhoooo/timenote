# 时刻 · Moment

Android 原生个人时间记录原型，应用 ID：`app.moment.tracker`。最低 Android 8.0（API 26），目标 Android 15（API 35）。

**交付状态：已生成 0.1.0 调试版 APK，Android 云端编译、单元测试、lint 和签名验证通过；尚未真机验证。**

源码位于 [newhoooo/timenote 的构建分支](https://github.com/newhoooo/timenote/tree/codex/moment-apk-20261104)，本次成功构建见 [GitHub Actions](https://github.com/newhoooo/timenote/actions/runs/41760688061)。APK 包名为 `app.moment.tracker`，支持 Android 8.0 及以上。

## 已写入的功能

- 原生中文界面：首页、应用选择、时间线、设置。
- 悬浮记录卡片：拖动位置、拖动右下角缩放、宽高数值调整、字号和透明度、草稿保存；横竖屏分别保存位置。
- 默认不抢焦点；主动点击输入框后切换到编辑状态并显示键盘。
- 自选 APP 自动记录：无障碍窗口事件识别前台 APP；锁屏、息屏和离开应用结束当前使用片段。
- 独立计时药丸：正计时/倒计时切换、暂停/继续、增加五分钟、结束会话、拖动、大小和透明度设置。
- 一次计时会话可包含多次应用使用片段；离开 APP 不累计前台使用时长，返回继续未结束会话。
- 人工任务计时、自由文本打点、任务时间编辑；人工任务与 APP 使用分别保存和统计。
- 定时提醒：30/60/90/120 分钟、起止时段、工作日限制、稍后十分钟、通知回退。采用非精确闹钟，可能受到系统省电策略延迟。
- Room/SQLite 本地存储，15 秒低频检查点；中断后关闭到最后确认时刻，不推算未知空白。
- JSON 数据导出、CSV 导出；应用没有声明互联网访问权限。
- Shizuku 连接状态与授权入口。**当前自动识别仍使用无障碍，没有接入 Shizuku 特权任务查询或保活功能。**

## 构建

需要 Android Studio、完整 JDK 17 或 21、Android SDK Platform 35、Build Tools 35.0.0，以及能访问 Google/Maven Central/Gradle 的网络。

### Android Studio

1. 打开本目录，等待 Gradle 同步；同意所需 Android SDK 组件安装。
2. Gradle 版本为 8.11.1，AGP 8.9.1，Kotlin 2.1.20。
3. 若 IDE 提示缺少标准 wrapper JAR，在 Gradle 设置中使用本机 Gradle 8.11.1，或先运行下方脚本下载本目录 `.gradle-dist/gradle-8.11.1` 并将其设为本地分发目录。
4. 执行 Build → Generate App Bundles or APKs → Generate APKs，或在终端执行命令。

### macOS / Linux

```sh
# 设置为自己的路径；也可由 Android Studio 创建 local.properties。
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk
sh ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

### Windows

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`gradlew` / `gradlew.bat` 是随项目提供的轻量引导脚本，并非标准生成的 wrapper。它们下载固定版本的官方 Gradle 分发包并校验官方 SHA-256。首次执行需要联网；完整同步依赖后可使用 Gradle 离线构建。也可以安装 Gradle 8.11.1 后直接执行 `gradle ...`。

输出 APK：`app/build/outputs/apk/debug/app-debug.apk`。调试签名用于个人测试。

项目附有 `.github/workflows/android.yml`，已在仓库构建分支运行成功。可以在成功的 Actions 任务下下载 `moment-debug-apk` 产物，解压得到 `app-debug.apk`。后续构建可通过推送构建分支触发；默认分支也加入工作流后可手动运行。当前 APK 使用调试签名，不用于应用商店发布。不同云构建可能使用不同调试密钥，后续更新需要规划固定签名以保留原有安装数据。

## 无 Android SDK 的核心测试

```sh
sh tools/test-core.sh
```

测试直接编译执行生产代码的 `SessionClock`、`ReminderWindow` 和 `UsageMath`，覆盖暂停和续计、倒计时只提醒一次、超时、周末/休息时段、夏令时、跨天区间分摊以及随机时间约束。

实际验证结果：**8,023 项核心行为断言通过**；云端 Gradle 编译通过；4 项 JUnit 测试全部通过；Android lint 为 0 错误、17 警告；APK v2 签名验证通过。未执行手机安装或真机交互测试。完整记录见 [BUILD_REPORT.md](BUILD_REPORT.md)。

## 手机首次使用

1. 安装构建得到的 APK，打开「设置」。
2. 单独授予本 APP 的悬浮窗权限，在系统无障碍页面启用「时刻」，按需允许通知。
3. 如为系统限制设置的侧载 APP，可能需要先在应用详情页允许受限制的设置，再启用无障碍；是否出现此入口取决于系统。
4. 在「应用」添加需要记录的 APP，点应用卡片设置默认正/倒计时和目标时长。
5. 打开目标 APP，检查药丸和使用记录。系统限制悬浮显示的页面可能无法显示窗口。
6. 根据手机厂商设置允许必要的后台运行；不保证所有机型长期常驻。

已有其他 APP 的无障碍、悬浮窗或 Shizuku 授权，不等于自动授权给本 APP。Shizuku 是可选项，服务断开不影响无障碍方案。

## 数据模型

| 表 | 作用 |
| --- | --- |
| `activities` | 分类结构预留，当前界面使用自由文本 |
| `time_entries` | 人工任务区间 |
| `checkins` | 某一时刻的回答，提醒关联唯一防重复 |
| `reminder_events` | 提醒计划与回应状态 |
| `tracked_apps` | 需要自动记录的包名及默认设置 |
| `timer_sessions` | 跨多次前台片段的逻辑会话 |
| `app_usage_segments` | 真实观察到的每次前台使用片段 |

提醒规则和悬浮窗外观存于 DataStore。时间点以 UTC 毫秒保存，时长使用单调时钟计算。每日统计按设备当前时区分摊；人工任务时间与 APP 使用时间不能直接相加。

## 首版限制和后续工作

- 已通过 Android 编译、JUnit、lint 与 APK 签名验证；实际悬浮交互、前台识别和后台持续运行仍需手机验收。
- Shizuku 只有连接和授权功能；特权前台查询需要按机型验证后再实现。
- 分屏仅按获得焦点的 APP 记录；后台音频不作为前台使用时长。
- 没有 JSON 导入恢复、云同步、每日使用额度倒计时、复杂统计图和分类编辑。
- 时间线目前为首版列表，长期使用需要增加数据库分页与归档。
- 提醒窗口与药丸分别记忆位置，尚未完成自动避让彼此遮挡的布局算法。
- 记录窗启用输入后的键盘行为、权限页悬浮限制、屏幕旋转和厂商后台策略都需要真机确认。

具体验收见 [ACCEPTANCE.md](ACCEPTANCE.md)。
