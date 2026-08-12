# AnimeMiTV

AnimeMiTV 是一个面向 Google TV / Android TV 的第三方 Anime1 客户端，使用遥控器即可浏览动画、选择剧集并在应用内播放。

> 本项目与 Anime1 官方无关。内容与服务可用性取决于 `anime1.me`，使用时请遵守所在地法律及网站规则。

## 功能

- YouTube 风格左侧导航栏，目前仅包含“动画”。
- 从 Anime1 加载完整动画列表，以纯文字卡片展示。
- 在完整动画列表上连续滚动，支持遥控器焦点导航。
- 加载动画分类页面的全部分页，展示并排序完整剧集列表。
- 使用 AndroidX Media3 在应用内播放视频。
- 播放器支持 DPAD Center 播放/暂停、左右键每次跳转 10 秒。
- 播放中错误会显示“重试”和“返回”，并重新获取播放签名。
- 网络、剧集和播放加载失败时可重试。
- 返回列表时恢复原动画及卡片焦点。

## 技术栈

- Kotlin
- Jetpack Compose for TV
- AndroidX TV Material
- AndroidX Lifecycle ViewModel
- Jsoup
- AndroidX Media3 ExoPlayer
- JUnit 与 Compose UI Test

## 环境要求

- Android Studio（附带 JDK）
- Android SDK 37
- Google TV / Android TV 设备或模拟器
- 最低 Android 10（API 29）

## 构建与运行

1. 使用 Android Studio 打开项目。
2. 等待 Gradle 同步完成。
3. 选择 Google TV / Android TV 设备。
4. 运行 `app` 配置。

也可使用命令行：

```bash
./gradlew :app:assembleDebug
```

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

应用版本统一记录在根目录 `version.txt`。修改版本后可运行以下命令校验 SemVer 和 Android `versionCode`：

```bash
./gradlew checkVersion
```

若终端找不到 Java，可使用 Android Studio 自带的 JDK：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## 测试

```bash
# JVM 单元测试
./gradlew test

# 编译设备测试
./gradlew :app:compileDebugAndroidTestKotlin

# 在已连接的设备或模拟器上运行 Compose UI 测试
./gradlew connectedAndroidTest

# Android Lint
./gradlew :app:lintDebug
```

## 自动发布

提交和 Pull Request 标题使用 Conventional Commits：`feat:` 提升 minor，`fix:` 和 `perf:` 提升 patch，带 `!` 或 `BREAKING CHANGE:` 提升 major。合并到 `main` 后，Release Please 会创建或更新 Release PR；合并 Release PR 才会创建带 `vX.Y.Z` tag 的 GitHub Release。

Release Please 需要仓库 Secret `RELEASE_PLEASE_TOKEN`，该 token 应为仅限本仓库的 fine-grained PAT。

正式 Release 发布后，签名 workflow 会使用以下 Repository secrets 构建并上传 APK 与 SHA-256：

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

`ANDROID_KEYSTORE_BASE64` 是长期 keystore 的 Base64 内容；keystore 密码、别名和 key 密码只配置在 GitHub Secrets，不写入仓库。可在 Actions 中手动输入 tag 重新上传已有 Release 的附件。

## 数据来源

- 动画列表：`https://anime1.me/animelist.json`
- 剧集列表：`https://anime1.me/?cat={分类ID}`
- 播放信息：由剧集页面中的签名参数向 Anime1 视频接口请求

应用不会托管动画内容。分类 ID 为 `0`、指向外部成人站点的条目会被过滤。

## 项目结构

```text
app/src/main/java/com/ljworks/animemitv/
├── MainActivity.kt       # Activity、TV 页面与播放器 UI
├── Anime.kt              # 领域模型与解析
├── Anime1DataSource.kt   # Anime1 网络访问与 Cookie
├── AnimeViewModel.kt     # 页面状态、导航和用户事件
└── ui/theme/             # Compose TV 主题
```

完整设计参见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 当前限制

- 没有搜索、收藏、图片和播放历史。
- 没有字幕、倍速和自动连播。
- Anime1 页面或接口结构变化时，解析逻辑可能需要同步更新。
- 当前网络数据仅保存在内存中，不支持离线浏览。
- 剧集分页按顺序加载，全部完成后显示列表；请求按页面流程取消，避免快速切换动画时旧结果覆盖当前页面。
