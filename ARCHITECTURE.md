# AnimeMiTV 架构说明

## 1. 架构目标

AnimeMiTV 采用单 Activity、Compose 状态驱动的轻量架构，目标是：

- 适配 Google TV 遥控器与焦点导航。
- 将 Anime1 页面结构变化限制在数据层。
- 通过公共数据源和 ViewModel 接缝进行测试。
- 避免引入当前功能不需要的导航、依赖注入和网络框架。

## 2. 分层结构

```text
┌──────────────────────────────────────────────┐
│ UI：MainActivity / Compose TV / Media3       │
├──────────────────────────────────────────────┤
│ 状态：AnimeViewModel / AppUiState            │
├──────────────────────────────────────────────┤
│ 数据接口：Anime1DataSource                   │
├──────────────────────────────────────────────┤
│ 实现：HttpURLConnection / JSON / Jsoup       │
├──────────────────────────────────────────────┤
│ 外部服务：anime1.me / v.anime1.me            │
└──────────────────────────────────────────────┘
```

依赖方向始终由 UI 指向状态层，再指向数据接口。`AnimeViewModel` 不依赖具体 HTTP 实现，因此测试可以替换为内存数据源。

## 3. 目录与职责

```text
app/src/
├── main/
│   ├── AndroidManifest.xml
│   └── java/com/ljworks/animemitv/
│       ├── MainActivity.kt
│       ├── AnimeListScreen.kt
│       ├── EpisodeListScreen.kt
│       ├── PlayerScreen.kt
│       ├── SharedUi.kt
│       ├── Anime.kt
│       ├── Anime1DataSource.kt
│       ├── AnimeViewModel.kt
│       └── ui/theme/
├── test/
│   ├── java/com/ljworks/animemitv/
│   │   ├── AnimeDataTest.kt
│   │   └── AnimeViewModelTest.kt
│   └── resources/
└── androidTest/
    └── java/com/ljworks/animemitv/AnimeMiTvUiTest.kt
```

### UI 文件

- `MainActivity.kt`：创建 `AnimeViewModel` 和真实 `Anime1HttpDataSource`，并根据 `AppScreen` 在动画列表、剧集列表和播放器间切换。
- `AnimeListScreen.kt`：实现动画列表、文字卡片网格和焦点恢复。
- `EpisodeListScreen.kt`：实现剧集列表、排序、文字卡片网格和焦点恢复。
- `PlayerScreen.kt`：使用 `PlayerView` 和 ExoPlayer 自动播放视频，仅显示播放/暂停和进度条；左右键自动聚焦进度条并以 15 秒为单位预览跳转，确认后才提交；上下键保留 Media3 默认焦点导航，控制器显示时返回仅关闭控制器；播放器同时监听播放错误。
- `SharedUi.kt`：实现左侧栏、加载状态和错误重试等共享界面。

### `Anime.kt`

定义核心数据类型：

- `Anime`：动画分类及展示字段。
- `Episode`：剧集、文章地址和播放签名。
- `EpisodePage`：一页剧集与下一页地址。
- `PlayableSource`：媒体地址及请求头。

同时包含三个纯解析入口：

- `parseAnimeList`：解析动画 JSON，并过滤分类 ID 为 `0` 的条目。
- `parseCategoryPage`：使用 Jsoup 从分类 HTML 提取剧集及后续分页地址。
- `parsePlaybackResponse`：解析视频接口返回的媒体地址。

### `Anime1DataSource.kt`

`Anime1DataSource` 是状态层使用的公共数据接缝：

```kotlin
suspend fun fetchAnimeList(): List<Anime>
suspend fun fetchEpisodes(anime: Anime, pageUrl: String): EpisodePage
suspend fun resolvePlayback(anime: Anime, episode: Episode): PlayableSource
```

`Anime1HttpDataSource` 使用平台自带的 `HttpURLConnection`：

- 在 IO dispatcher 中执行网络请求。
- 设置超时、User-Agent、Origin 和 Referer。
- 跟随 Anime1 分类地址重定向。
- 保存视频接口返回的 Cookie，并传给 Media3。
- 将 HTTP、空播放地址等问题转换为可展示的异常信息。

### `AnimeViewModel.kt`

`AnimeViewModel` 是唯一应用状态持有者，`AppUiState` 包含：

- 当前页面 `AnimeList / EpisodeList / Player`。
- 动画、剧集和播放的加载状态。
- 完整动画列表、已加载剧集及下一页 URL。
- 剧集排序方式。
- 当前动画、剧集和焦点 ID。

所有用户行为通过 ViewModel 方法进入，例如选择动画、切换排序、加载更多、播放、重试和返回。Compose 只渲染状态并转发事件。

每类异步请求都持有 Job。打开新动画、播放新剧集或离开页面时取消旧 Job；请求完成后还会核对当前页面、动画 ID、剧集 ID 和分页 URL，旧结果不能写入新页面。

## 4. 核心数据流

### 动画列表

```text
应用启动
  → AnimeViewModel.loadAnime
  → Anime1DataSource.fetchAnimeList
  → GET /animelist.json
  → parseAnimeList
  → AppUiState.anime = Content
  → Compose 使用 LazyVerticalGrid 连续展示完整列表
```

整个动画 JSON 只请求一次；列表由 Compose 惰性组合可见区域附近的卡片，向下滚动不会再次访问网络。

### 剧集列表

```text
选择动画
  → GET /?cat={id}
  → 跟随重定向到分类页面
  → parseCategoryPage
  → 提取该页多个 article
  → 展示剧集
```

若 HTML 含下一页地址，用户聚焦“加载更早剧集”后才请求后续页面；新旧结果按剧集 ID 去重合并。

### 视频播放

```text
选择剧集
  → 从 article 取得 data-apireq
  → POST https://v.anime1.me/api
  → 保存 Set-Cookie
  → 解析 MP4 地址
  → 将地址和 Cookie 交给 Media3
```

播放地址失效时，重试会重新抓取剧集所在分类页以刷新签名，再请求新的播放地址。

## 5. UI 与焦点模型

应用没有引入 Navigation Compose，而是通过 `AppScreen` 切换三个页面：

```text
AnimeList → EpisodeList → Player
    ↑            ↑           │
    └────────────┴───────────┘ Back
```

焦点策略：

- ViewModel 保存原动画/剧集 ID；动画网格通过 `LazyGridState.scrollToItem` 先滚动到目标，再在目标卡片完成布局后请求焦点。
- 从剧集页返回时恢复原动画及卡片焦点。
- 从播放器返回时恢复原剧集。
- 动画列表滚动到完整数据集末尾后结束；剧集列表仍使用分页哨兵加载更早剧集。

## 6. 错误处理

网络状态统一通过 `LoadState` 表达：

```text
Idle → Loading → Content
               ↘ Error → Retry
```

- 首次动画或剧集请求失败：显示错误信息和“重试”。
- 加载更多剧集失败：保留已加载内容，只在页尾显示重试。
- 播放解析或播放过程中失败：释放播放器并留在错误页，可重新获取签名或返回。
- 播放重试刷新剧集分类页；若找不到原剧集，则直接报错，不复用旧签名。

应用当前不做磁盘缓存；进程结束后会重新请求数据。

## 7. 测试策略

### JVM 单元测试

- JSON、HTML 与播放响应解析。
- 成人外站条目过滤。
- 视频请求的 Origin、Referer、表单内容和 Cookie 传递。
- ViewModel 的加载、剧集分页、剧集排序、播放错误、请求取消和旧结果保护状态。
- 播放重试找不到原剧集时不会调用旧签名。

测试使用固定 fixture，不依赖 Anime1 在线服务。

### Compose 设备测试

- 左侧栏和文字卡片展示。
- 完整动画列表展示及焦点恢复。
- 动画列表进入剧集页。
- 播放错误页的“重试/返回”操作。
- 播放页面的播放/暂停、进度条跳转和非可见卡片的滚动/焦点恢复。

### 验证命令

```bash
./gradlew test
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew connectedAndroidTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

## 8. 扩展位置

后续功能应优先沿现有接缝扩展：

- 搜索和筛选：在 `AnimeViewModel` 对已加载动画列表派生结果。
- 播放记录：为剧集进度增加独立的本地持久化数据源。
- 收藏：增加最小本地存储，不修改 Anime1 网络数据源。
- Anime1 页面变化：仅修改 `Anime1HttpDataSource` 或 `Anime.kt` 中的解析器及 fixtures。
- 新侧栏页面：先扩展 `AppScreen`，只有页面数量和返回栈明显增长时再引入导航库。
