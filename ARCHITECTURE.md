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
│       ├── SeasonalListScreen.kt
│       ├── EpisodeListScreen.kt
│       ├── PlayerScreen.kt
│       ├── SharedUi.kt
│       ├── Anime.kt
│       ├── Anime1DataSource.kt
│       ├── AnimeViewModel.kt
│       ├── FollowedAnimeStore.kt
│       ├── EpisodeProgressStore.kt
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

- `MainActivity.kt`：创建 `AnimeViewModel`、真实 `Anime1HttpDataSource`、本机关注存储和观看进度存储，并根据 `AppScreen` 在动画列表、关注动画列表、季度排期、剧集列表和播放器间切换。
- `AnimeListScreen.kt`：实现动画列表、文字卡片网格、标题搜索和焦点恢复。搜索索引在后台构建，首屏不等待简繁/拼音预处理。
- `SeasonalListScreen.kt`：实现季度选择器、日到六排期、无资源提示与季度焦点恢复。
- `EpisodeListScreen.kt`：实现剧集列表、排序、文字卡片网格、观看进度标识和焦点恢复。
- `PlayerScreen.kt`：使用 `PlayerView` 和 ExoPlayer 自动播放视频并从本机进度续播；真正开始播放后每 10 秒以及暂停、离开或进入后台时保存进度，自然结束时将续播位置重置到开头。页面仅显示播放/暂停和进度条；左右键自动聚焦进度条并以 15 秒为单位预览跳转，确认后才提交；上下键保留 Media3 默认焦点导航，控制器显示时返回仅关闭控制器；播放器同时监听播放错误。
- `SharedUi.kt`：实现包含动画、季度新番和关注入口的左侧栏、加载状态和错误重试等共享界面。
- `FollowedAnimeStore.kt`：定义关注动画本地存储接缝，并提供基于 Android 原生键值存储的实现。
- `EpisodeProgressStore.kt`：定义单集观看进度本地存储接缝，并使用 Android 原生键值存储保存播放位置和视频时长；该偏好文件排除云备份和设备迁移。

### `Anime.kt`

定义核心数据类型：

- `Anime`：动画分类及展示字段。
- `AnimeSeason`：季度标签及 Anime1 季度页面地址。
- `SeasonalAnime` / `AnimeSchedule`：排期动画及按日到六分组的季度排期。
- `Episode`：剧集、文章地址和播放签名。
- `EpisodePage`：一页剧集与下一页地址。
- `PlayableSource`：媒体地址及请求头。

同时包含解析与本地搜索入口：

- `filterAnimeByTitle`：按标题直接匹配、简繁等价或普通话拼音连续音节前缀筛选已加载列表。
- `buildAnimeSearchTerms`：为加载完成的动画生成简体标题和拼音音节搜索表示。
- `parseAnimeList`：解析动画 JSON，并过滤分类 ID 为 `0` 的条目。
- `parseCurrentSeason`：仅从 Anime1 首页 `#masthead` 识别网站声明的当前季度。
- `precedingSeasons`：在本地生成当前季及此前连续 20 季的规则化地址。
- `parseSeasonSchedule`：使用 Jsoup 解析季度页的日到六排期和可用分类链接。
- `parseCategoryPage`：使用 Jsoup 从分类 HTML 提取剧集及后续分页地址。
- `parsePlaybackResponse`：解析视频接口返回的媒体地址。

### `Anime1DataSource.kt`

`Anime1DataSource` 是状态层使用的公共数据接缝：

```kotlin
suspend fun fetchAnimeList(): List<Anime>
suspend fun fetchCurrentSeason(): AnimeSeason
suspend fun fetchSeasonSchedule(season: AnimeSeason): AnimeSchedule
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

- 当前页面 `AnimeList / SeasonalList / FollowedAnimeList / EpisodeList / Player`。
- 动画、季度发现/排期、剧集和播放的加载状态。
- 完整动画列表、当前/选中季度、进程内季度排期缓存、进程内剧集列表缓存及已加载剧集。
- 剧集排序方式。
- 动画、关注动画、季度排期和剧集的独立焦点恢复状态。
- 完整动画列表的本地搜索状态（查询、是否处于搜索态及返回时的输入焦点恢复标记）。
- 本机关注动画 ID、按动画总表顺序派生的关注列表及保存失败提示。
- 以剧集 ID 为键的本机观看进度；剧集卡片据此显示看过圆点和位置进度条。

所有用户行为通过 ViewModel 方法进入，例如选择动画或季度、切换排序、播放、重试和返回。Compose 只渲染状态并转发事件。

每类异步请求都持有 Job。打开新动画、播放新剧集或切换顶层栏目时取消旧 Job；请求完成后还会核对当前页面、季度、动画和剧集 ID，旧结果不能写入新页面，也不会写入缓存。剧集请求的“当前动画”判定与缓存 key 使用同一身份定义（`动画 id + 分类地址`），因此 ID 相同但分类地址不同的两部动画不会互相污染 UI 或缓存。

`episodeCache` 是 ViewModel 内的进程内剧集缓存：key 为 `动画 id + 分类地址`，值为剧集列表与写入时的单调时间戳（`elapsedRealtimeMillis`，默认 `SystemClock.elapsedRealtime()`，测试可注入假时钟），TTL 为 `EPISODE_CACHE_TTL_MILLIS`（10 分钟）。缓存只随 ViewModel / 进程存活，不做任何持久化。

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
  → 后台生成简繁/拼音搜索索引
```

整个动画 JSON 只请求一次；列表由 Compose 惰性组合可见区域附近的卡片，向下滚动不会再次访问网络。完整动画页的搜索仅对这份已加载列表按标题做本地、不区分大小写的连续子字符串过滤，保留原始顺序，不发起额外请求。搜索索引未完成时列表和直接标题搜索已可用；索引完成后自动启用简繁与拼音匹配。

### 季度排期

```text
进入季度新番
  → GET /，从 #masthead 发现当前季
  → 本地生成当前季及此前 20 季
  → GET 已选季度页面
  → parseSeasonSchedule
  → AppUiState.seasonalSchedule = Content
  → Compose 以日到六七列展示排期
```

仅请求当前选中的季度；成功排期按季度标签缓存在进程内，失败不缓存。没有 Anime1 分类链接的条目保持可聚焦，但只显示短暂“暂无资源”提示。

### 剧集列表

```text
选择动画
  → 命中未过期的 episodeCache？ → 直接展示缓存剧集（无请求、无 Job）
  → 未命中或已过期：
  → GET /?cat={id}
  → 跟随重定向到分类页面
  → parseCategoryPage
  → 自动顺序请求“上一頁”，直至没有后续分页
  → 合并并按剧集 ID 去重
  → 全部完成后展示剧集并写入 episodeCache
```

若 HTML 含“上一頁”地址，`loadAllEpisodes()` 会自动顺序请求后续分页；使用已访问 URL 集合避免分页循环，合并后按剧集 ID 去重。

只有全部分页成功、且结果返回时用户仍停留在该动画，最终列表才会写入 `episodeCache`；分页失败、请求被取消或结果已过时都不写入，也不会覆盖已有缓存。TTL 内再次打开同一动画直接展示缓存列表，不发请求；TTL 过期后重新加载全部分页并替换缓存。缓存命中不改变排序、焦点恢复、来源导航和返回行为。

缓存中的剧集包含分类页解析出的播放签名 `apiRequest`，因此使用较短 TTL；若缓存签名已失效，播放重试仍会只刷新该剧集所在的单页并取得新签名。

### 视频播放

```text
选择剧集
  → 从 article 取得 data-apireq
  → POST https://v.anime1.me/api
  → 保存 Set-Cookie
  → 解析 MP4 地址
  → 将地址和 Cookie 交给 Media3
```

播放地址失效时，重试会重新抓取剧集所在分类页以刷新签名，再请求新的播放地址。媒体真正开始播放后，该单集成为看过的单集；暂停状态下单独拖动但未恢复播放不会改变保存位置。观看进度只保存在本机，不随网络缓存过期。

## 5. UI 与焦点模型

应用没有引入 Navigation Compose，而是通过 `AppScreen` 切换五个页面：

```text
AnimeList ───────┐
SeasonalList ────┼→ EpisodeList → Player
FollowedAnimeList┘       ↑           │
     ↑                   └───────────┘ Back
     └────────────────────────────────┘
```

焦点策略：

- ViewModel 保存动画列表、关注动画列表、原动画/关注动画/季度排期和剧集 ID；动画网格通过 `LazyGridState.scrollToItem` 先滚动到目标，再在目标卡片完成布局后请求焦点。
- 季度页初始聚焦第一张排期卡片；第一排按上进入季度选择器，选择器按下回到排期。季度按钮获得焦点时会横向滚动到可见区域。
- 完整动画页的搜索只在该页面显示；搜索输入、清除和取消均不重新加载动画列表。查询按标题连续子字符串匹配，忽略首尾空格且不区分英文字母大小写。
- 从剧集页返回时按来源恢复动画列表、关注动画列表或原季度、排期位置及卡片焦点；若搜索结果中的原动画已不可见，则聚焦当前结果第一项。
- 搜索结果为空时显示空状态，不请求不存在卡片的焦点；查询变化或清空后按当前结果重新定位网格。
- 从播放器返回时恢复原剧集。
- 动画列表滚动到完整数据集末尾后结束；剧集列表在进入页面时已自动加载全部分页，不使用分页哨兵。

## 6. 错误处理

网络状态统一通过 `LoadState` 表达：

```text
Idle → Loading → Content
               ↘ Error → Retry
```

- 动画或剧集加载失败：显示错误信息和“重试”。剧集分页只有在全部页面成功后才展示；任一分页失败会使整次加载进入错误状态。
- 播放解析或播放过程中失败：释放播放器并留在错误页，可重新获取签名或返回。
- 播放重试刷新剧集分类页；若找不到原剧集，则直接报错，不复用旧签名。

应用只在进程内缓存网络数据（季度排期与带 TTL 的剧集列表）；进程结束后会重新请求数据。关注动画 ID 与单集观看进度持久保存到本机，不保存动画资料；观看进度写入失败静默处理，不中断播放。

## 7. 测试策略

### JVM 单元测试

- JSON、季度/分类 HTML 与播放响应解析。
- 成人外站条目过滤。
- 视频请求的 Origin、Referer、表单内容和 Cookie 传递。
- ViewModel 的动画和关注动画加载、关注切换/持久化/失败反馈、观看进度恢复与保存、来源导航、焦点恢复、季度缓存/重试、剧集排序、播放错误、请求取消和旧结果保护状态。
- 剧集缓存：TTL 内命中不再请求、不同动画互不命中、TTL 过期后重取并替换、失败与取消/过时结果不写入缓存，以及从缓存剧集发起播放重试时只刷新单页签名（通过注入假时钟控制 TTL，不使用真实延时）。
- 播放重试找不到原剧集时不会调用旧签名。

测试使用固定 fixture，不依赖 Anime1 在线服务。

### Compose 设备测试

- 左侧栏、季度选择器和文字卡片展示。
- 完整动画列表、关注动画列表与季度排期展示及独立焦点恢复。
- 完整动画页搜索入口、输入焦点、实时过滤、清除、取消、空结果和搜索结果返回焦点恢复。
- 关注动画入口、空状态、过滤顺序和剧集页关注按钮。
- 动画列表进入剧集页。
- 标题的简繁、普通话拼音和连续音节前缀搜索；搜索索引尚未完成时列表仍会显示。
- 播放错误页的“重试/返回”操作。
- 播放页面的播放/暂停、进度条跳转、剧集卡片观看进度标识和非可见卡片的滚动/焦点恢复。

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

- 搜索和筛选：在 `AnimeListScreen` 对已加载动画列表派生结果；耗时搜索索引在后台构建。
- 观看进度：通过 `EpisodeProgressStore` 扩展本机持久化行为，不修改 Anime1 网络数据源。
- 关注动画：使用最小本地存储保存动画 ID，不修改 Anime1 网络数据源。
- Anime1 页面变化：仅修改 `Anime1HttpDataSource` 或 `Anime.kt` 中的解析器及 fixtures。
- 新侧栏页面：先扩展 `AppScreen`，只有页面数量和返回栈明显增长时再引入导航库。
