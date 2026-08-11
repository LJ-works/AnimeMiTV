# AnimeMiTV 完整实施计划（TDD）

## 1. 目标

构建一个面向 Google TV、支持遥控器操作的 Anime1 客户端。首期包含：

1. 左侧固定 YouTube 风格导航栏，目前仅有“动画”。
2. 从 `https://anime1.me/animelist.json` 一次下载全部动画，过滤分类 ID 为 `0` 的外站成人条目。
3. 主内容区以纯文字卡片展示动画，每页 20 部，在内存中分页。
4. 进入动画后，请求 `https://anime1.me/?cat={分类ID}`，跟随重定向并解析该动画分类总页面中的多集内容。
5. 长篇动画存在分类 HTML 分页；仅在用户到达末尾时按需请求下一页。
6. 点击剧集后在应用内全屏播放，支持播放/暂停、快进、快退、进度条和返回。
7. 所有网络页面都有加载、失败提示与重试。

## 2. 已确认的数据事实

### 动画列表

- 地址：`https://anime1.me/animelist.json`
- 返回完整数组，目前约 1900 项，不需要服务端分页。
- 单项格式：

```text
[分类ID, 动画名, 集数状态, 年份, 季节, 字幕组]
```

- App 每页 20 项只是本地 UI 分页，不重复请求 JSON。
- 分类 ID 为 `0` 的条目名称含外站 HTML 链接，首期直接过滤。

### 剧集列表

- 请求：`https://anime1.me/?cat={分类ID}`。
- 服务端会重定向到该动画分类总页面；同一 HTML 中包含多个 `<article>`，每个 article 对应一集。
- 使用 Jsoup 提取：文章 ID、剧集标题、文章 URL、`video[data-apireq]`、`data-vid`、`data-tserver`。
- WordPress REST API 实测返回 `401`，因此不依赖它。
- 长篇动画的分类总页面仍可能存在 `/page/2` 等分页；从页面导航链接取得下一页 URL并按需加载，不逐集抓文章页面。

### 播放地址

- 将当前剧集 article 的 `data-apireq` 作为表单字段 `d` POST 到 `https://v.anime1.me/api`。
- 请求需要正确的 `Origin`、`Referer` 与 Cookie；响应 JSON 的 `s` 中包含 MP4 地址，同时响应会设置播放所需 Cookie。
- 播放失败时重新请求分类页面取得新签名，再请求新播放地址，不复用可能过期的签名。

## 3. 最小技术方案

### 依赖

保留现有 Compose for TV，只增加必要依赖：

- `org.jsoup:jsoup`：解析剧集 HTML，避免脆弱的正则。
- AndroidX Media3 ExoPlayer 与 Compose/UI 控件：应用内视频播放。
- Lifecycle ViewModel：保存页面状态并承载异步加载。
- 测试侧仅补充 JVM 可用的 JSON 实现（若本地单元测试需要）；不引入 Retrofit、OkHttp、DI、导航框架或图片库。

### 模块与公共接缝

这是已确认的 TDD 测试边界：

1. **数据接缝 `Anime1DataSource`**
   - 加载并解析动画 JSON。
   - 加载并解析分类 HTML。
   - 用剧集签名换取可播放资源。
2. **状态接缝 `AnimeViewModel`**
   - 暴露不可变 UI 状态与用户事件：加载、重试、翻页、选择动画、加载更多剧集、切换排序、选择剧集、返回。
3. **UI 接缝（Compose semantics）**
   - 验证关键文本、加载/错误状态、分页哨兵、排序和页面切换。
4. **播放器接缝 `PlayerScreen`**
   - 以可播放资源作为输入；只测试控制行为和错误/重试回调，不测试 Media3 内部实现。

网络实现、Jsoup 选择器、Cookie 存储等内部细节不直接测试；通过上述公共行为验证。

### 建议文件

```text
app/src/main/java/com/ljworks/animemitv/
├── MainActivity.kt
├── Anime.kt                  # Anime、Episode、PlayableSource
├── Anime1DataSource.kt       # HTTP、JSON、HTML、签名与 Cookie
├── AnimeViewModel.kt         # 页面与加载状态
└── AnimeMiTvApp.kt           # 侧栏、动画页、剧集页、播放器页

app/src/test/.../
├── Anime1DataSourceTest.kt
└── AnimeViewModelTest.kt

app/src/androidTest/.../
└── AnimeMiTvAppTest.kt

app/src/test/resources/
├── animelist.json
├── category-page-1.html
├── category-page-2.html
└── playback-response.json
```

若实现后某个文件很短，优先合并，避免为目录结构本身制造抽象。

## 4. 状态与导航

使用一个 Activity 和 Compose 状态切换，不增加导航框架：

```text
AnimeList → EpisodeList → Player
     ↑           ↑          │
     └───────────┴──────────┘ Back
```

核心状态：

```text
Screen = AnimeList | EpisodeList(anime) | Player(episode)
LoadState = Loading | Content | Error(message)
AnimePage = pageIndex + 每页20项
EpisodeState = 已加载剧集 + nextPageUrl + 排序方式
```

返回时恢复原页面、页码、滚动位置和原卡片焦点：

- Player 返回后聚焦原剧集。
- EpisodeList 返回后聚焦原动画及原动画页。
- 剧集排序仅在当前剧集页面有效，再次进入时默认“最新优先”。

## 5. TV 界面与焦点规则

### 动画页

- 左侧固定导航栏，唯一项目“动画”，有明确聚焦态和选中态。
- 右侧为标题、文字卡片网格和页码。
- 卡片显示：动画名、集数状态、年份/季节、字幕组；字幕组为空时不占位。
- 每页最多 20 部。
- 第 2 页起在列表前放“上一页”哨兵；末尾有下一页时放“下一页”哨兵。
- 焦点进入哨兵便翻页；真实动画末项仍可聚焦和点击。
- 翻到下一页后聚焦第一部动画；翻到上一页后聚焦最后一部动画。

### 剧集页

- 显示动画名、排序按钮和剧集文字卡片。
- 默认最新集优先，可切换为最早集优先；切换后保持当前已加载数据，不重新请求。
- 有后续 HTML 页时在末尾显示“加载更早剧集”哨兵，聚焦后加载并去重追加。
- 加载下一页失败时保留已显示剧集，并在页尾提供重试。

### 播放器

- 点击剧集后先显示加载状态，成功后全屏播放。
- 遥控器确认键切换播放/暂停，左右键快退/快进，返回键退出播放器。
- 控制层显示进度条和时间。
- 播放地址解析或播放失败时显示重试和返回；重试完整刷新分类页签名与播放 URL。

## 6. TDD 垂直切片

严格执行 **Red → Green**：每次只写一个失败测试，再写使其通过的最小实现；不先批量写测试，不测试私有方法。重构留到全部切片通过后的审查阶段。

### Slice 1：解析动画列表

- **Red**：fixture 中的合法条目被映射为 `Anime`，ID 为 0 的条目被过滤，空字幕组正确处理。
- **Green**：实现最小 JSON 映射。
- 验收：解析 fixture 后顺序与网站返回顺序一致。

### Slice 2：动画本地分页

- **Red**：21 项产生 2 页；第一页 20 项、第二页 1 项；首尾边界不越界。
- **Green**：在 ViewModel 中实现 20 项切片和页码。
- **Red**：聚焦下一页/上一页哨兵后切页，并产生正确的焦点恢复目标。
- **Green**：实现分页事件，不增加通用分页框架。

### Slice 3：动画页状态

- **Red**：加载时显示进度，成功显示“动画”和卡片，请求失败显示重试，重试可回到内容。
- **Green**：实现 ViewModel 加载状态及最小 Compose 页面。
- 使用 fake `Anime1DataSource`，不在 UI 测试中访问真实网络。

### Slice 4：解析分类总页面

- **Red**：从单个 category fixture 的多个 article 解析多集标题、URL 与播放签名字段。
- **Green**：用 Jsoup 实现最小选择器。
- **Red**：解析下一页 URL；无下一页时返回 `null`；重复 article 不重复加入。
- **Green**：加入按需分页和去重。

### Slice 5：剧集页与排序

- **Red**：选择动画进入剧集页并显示该动画的多集内容。
- **Green**：实现页面切换与剧集加载。
- **Red**：排序按钮在“最新优先/最早优先”间切换；离开后再进入恢复默认。
- **Green**：只在当前 EpisodeState 中保存排序。
- **Red**：后页失败时旧剧集仍保留并显示页尾重试。
- **Green**：实现增量加载错误状态。

### Slice 6：换取播放资源

- **Red**：给定 fixture 签名和 API 响应，数据源发送预期表单字段与必要请求头，解析 MP4 URL并保存响应 Cookie。
- **Green**：使用最小 HTTP/Cookie 实现。
- **Red**：签名失效后重试会重新加载分类页并取得新签名，而非重复旧请求。
- **Green**：实现完整重试链。

### Slice 7：应用内播放

- **Red**：选择剧集后进入播放器加载态；资源成功后创建播放界面；失败显示重试和返回。
- **Green**：接入 Media3 ExoPlayer，并把播放 Cookie/请求头传给媒体数据源。
- **Red**：确认键、左右键和返回键分别触发播放暂停、快退快进和恢复原剧集焦点。
- **Green**：实现基础 TV 控制。

### Slice 8：端到端 UI 行为

用 Compose instrumentation 测试覆盖：

1. 左侧只有“动画”。
2. 动画卡片无图片且文字字段正确。
3. 前后哨兵切页与页码正确。
4. 动画 → 剧集 → 播放器 → 返回的页面及焦点正确。
5. 加载失败、后页失败和播放失败均可重试。

## 7. 真实站点冒烟检查

自动测试使用固定 fixture，避免真实网络、Cloudflare、内容更新造成不稳定。实现完成后手动执行一次真实站点冒烟：

1. `animelist.json` 可下载，首项可显示，成人外站条目未显示。
2. `?cat=1933` 可跟随重定向并解析出多集。
3. 长篇分类页能识别并按需加载 `/page/2`。
4. 任选一集可通过 `v.anime1.me/api` 取得播放地址与 Cookie，并在 TV 模拟器播放。
5. 断网后列表/剧集/播放器显示错误；恢复网络后重试成功。

若站点响应格式变化，只更新数据接缝和 fixtures，不改 ViewModel/UI 公共行为测试。

## 8. 完成标准

- 所有本地单元测试和 Compose instrumentation 测试通过。
- `./gradlew test`、`./gradlew connectedAndroidTest`、Lint/LSP 无新增阻断错误。
- Google TV 模拟器中仅用遥控器即可完成：打开 App → 本地翻页 → 选择动画 → 查看/排序/加载更多剧集 → 播放 → 返回并恢复焦点。
- 无 Retrofit、OkHttp、DI、导航、图片缓存等未被当前需求证明必要的依赖或抽象。
