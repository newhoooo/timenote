# 0.1.0 APK 构建记录

- 构建时间：2026-11-04 UTC。
- 成功任务：https://github.com/newhoooo/timenote/actions/runs/41760688061
- 已验证代码提交：`9fa61c835ccb78d417d2d87488531004fdc872c2`。
- 源码分支：`codex/moment-apk-20261104`，主分支未修改。
- 应用名称：时刻。
- 包名：`app.moment.tracker`。
- 版本：`0.1.0`，版本号 `1`。
- 最低 Android SDK：26（Android 8.0）。目标/编译 SDK：35。
- 类型：调试版 APK，APK v2 签名验证通过。
- APK 大小：17,588,728 字节。
- APK SHA-256：`8345d967fea2cac28be845f7be354c1a4855c09a2315fd40ee74d83131749db0`。

## 已执行的验证

- 核心计时、提醒时段与跨日分摊：8,023 项行为断言通过。
- `:app:testDebugUnitTest`：4 项 JUnit 测试，0 失败、0 错误、0 跳过。
- `:app:assembleDebug`：成功。
- `:app:lintDebug`：成功，0 错误、17 警告。警告包括依赖更新建议、目标 SDK 更新建议、资源和触控可访问性建议。
- `apksigner verify --verbose`：通过。
- `aapt dump badging`：应用名称、包名、版本和 SDK 信息符合预期。
- 下载的产物 ZIP SHA-256 与 GitHub 记录一致；解压后的 APK SHA-256 与云端签名验证步骤输出一致。

## 尚未执行

没有连接手机或模拟器，未验证安装后启动、悬浮窗键盘焦点、前台应用识别和厂商后台持续运行行为。请按 [ACCEPTANCE.md](ACCEPTANCE.md) 在手机上逐项确认。

安装后先打开「设置」，为本 APP 授予悬浮窗、无障碍和通知权限，开启「定时问问自己」。再到「应用」添加要自动计时的应用。Shizuku 为可选连接，当前前台识别使用无障碍。
