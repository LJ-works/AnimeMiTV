# AnimeMiTV 网络请求说明

本文档基于源码分析（`968cdc3`，2026-08-14），列出 AnimeMiTV 会向外部服务发出的全部请求：种类、触发时机、单次操作产生的请求数量，以及是否有缓存/去重。目的是让维护者和使用者了解本应用对 `anime1.me` / `v.anime1.me` 造成的负载，便于评估和优化。

## 1. 概览

- 应用把请求分为两类：**元数据请求**（对 `anime1.me`、`v.anime1.me` 的 GET/POST，返回 JSON、HTML 或表单响应）与**视频流请求**（Media3 ExoPlayer 对已解析视频地址的分段下载）。两者实现路径完全独立，下文分别说明。
- 全部元数据请求都经过唯一入口 `Anime1HttpDataSource`（`app/src/main/java/com/ljworks/animemitv/Anime1DataSource.kt`），底层使用平台自带的 `HttpURLConnection`，没有引入 Retrofit/OkHttp 等网络框架。
- 应用界面为纯文字卡片，**不加载任何图片**；也没有埋点、崩溃上报或广告 SDK（见 `app/build.gradle.kts` 依赖列表），因此不存在这些来源的后台请求。
- 所有元数据请求都由用户操作直接触发，**没有轮询、没有预取、没有后台自动刷新**；应用没有前台常驻的心跳请求。
- 应用不持久化网络数据，进程内使用内存缓存；退出应用或系统回收进程后，所有已加载数据需要重新请求（`README.md` “当前限制”一节已说明）。

## 2. 元数据请求清单

| 场景触发 | 方法与地址 | 单次触发的请求数 | 缓存/去重情况 |
| --- | --- | --- | --- |
| 首次进入“动画”或“关注”页（`state.anime` 为 `Idle`/`Loading`） | `GET https://anime1.me/animelist.json` | 1 | 成功后在进程内复用；失败后用户重试会再次请求。`loadAnime()` 会先取消旧 Job，避免旧请求与新请求并发 |
| 首次进入“季度新番” | `GET https://anime1.me/`（解析 `#masthead` 当前季） | 1 | 成功后在进程内复用；发现失败后用户重试会再次请求 |
| 选择一个尚未加载过的季度 | `GET {season.url}`（如 `https://anime1.me/2026年夏季新番`） | 1 | 按季度标签存入 `seasonalCache`（进程内 Map），同一季度重复选择直接命中缓存，不再请求 |
| 打开任意一部动画的剧集列表 | `GET https://anime1.me/?cat={id}`，随后**自动**跟随页面内“上一頁”分页链接连续请求 | **N**（N = 该分类下 WordPress 分页页数，逐页顺序请求直至无下一页） | **不缓存**：每次进入同一部动画都会重新顺序请求全部 N 页（见第 3 节） |
| 播放某一集 | `POST https://v.anime1.me/api`（表单体 `d={签名}`） | 1 | 不缓存，每次点击播放都会重新请求 |
| 播放出错后点击“重试” | `GET` 剧集所在分类页（1 次，刷新播放签名）；找到原剧集后再 `POST /api`（1 次） | 1–2 | 不缓存，只重取剧集所在的单页，不会重新拉取该动画的全部分页 |

对应源码位置：

```text
Anime1DataSource.kt
├── fetchAnimeList()       → GET /animelist.json           (第 23-25 行)
├── fetchCurrentSeason()   → GET /                          (第 27-29 行)
├── fetchSeasonSchedule()  → GET {season.url}                (第 31-33 行)
├── fetchEpisodes()        → GET {pageUrl}                   (第 35-37 行)
└── resolvePlayback()      → POST https://v.anime1.me/api    (第 39-44, 51-59 行)

AnimeViewModel.kt
├── loadAnime()            → 调用 fetchAnimeList             (第 105-123 行)
├── openSeasonal()         → 调用 fetchCurrentSeason          (第 200-245 行)
├── loadSeason()           → 调用 fetchSeasonSchedule + 缓存  (第 255-276 行)
├── openAnime()            → 调用 loadAllEpisodes             (第 332-360 行)
├── loadAllEpisodes()      → 循环调用 fetchEpisodes 直至无下一页 (第 464-475 行)
├── playEpisode()          → 调用 resolvePlayback             (第 376-400 行)
└── retryPlayback()        → 调用 fetchEpisodes(单页) + resolvePlayback (第 402-430 行)
```

请求头统一在 `Anime1DataSource.open()` 中设置：`User-Agent: AnimeMiTV/1.0 (Android TV)`、`Accept`、`Origin: https://anime1.me`、`Referer`，并在拿到 `Set-Cookie` 后于后续请求带上 `Cookie`；超时为连接 15 秒、读取 30 秒；`instanceFollowRedirects = true` 会自动跟随分类页重定向。

## 3. 会随内容规模成倍增长的来源：剧集分页自动全量加载

这是全部请求中唯一会随内容量线性增长、且当前**没有缓存**的部分，值得重点关注：

```text
用户点击一部动画
  → AnimeViewModel.openAnime()
  → loadAllEpisodes(anime)
      pageUrl = anime.categoryUrl   // https://anime1.me/?cat={id}
      while (pageUrl != null) {
          GET pageUrl               // 第 1、2、3 … 页，逐页顺序请求（非并发）
          pageUrl = 该页“上一頁”链接
      }
  → 所有页面的剧集合并、按 id 去重后一次性展示
```

- 请求数量 N 取决于该动画分类在 `anime1.me` 上被拆成多少页（WordPress 分页，常见为每页固定篇数），剧集越多、开播时间越久的番剧，打开时产生的连续 GET 请求越多。
- 请求为**顺序**执行（`while` 循环中逐次 `await`），不会并发轰炸服务器，但也没有请求间隔或退避。
- **没有本地缓存**：用户返回动画列表后再次点开同一部动画，会重新顺序请求全部 N 页；同一部动画在一次使用中反复查看，请求量会成倍累加。
- 播放重试（`retryPlayback`）只重新请求剧集所在的单页以刷新签名，不会重新拉取整部动画的全部分页。

> `ARCHITECTURE.md` 已同步为当前行为：`openAnime()` 通过 `loadAllEpisodes()` 自动连续加载全部分页，全部完成后一次性展示。

## 4. 视频播放请求（不经过 Anime1DataSource）

播放地址解析完成后，实际视频数据由 `PlayerScreen.kt` 中的 Media3 ExoPlayer 负责拉取，与上文的元数据请求是两条独立路径：

```text
resolvePlayback() 返回 PlayableSource(url, headers)
  → DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)
  → ExoPlayer 对 source.url 发起分段 / Range 请求持续拉流
```

- 请求次数取决于视频时长、码率与 ExoPlayer 的默认分段策略，无法用固定数字衡量；这是视频类应用本身固有的流量，不属于可通过“减少 API 调用”优化的部分。
- 会带上 `resolvePlayback()` 中保存的 `Cookie` 头，但不会带上元数据请求使用的 `User-Agent` / `Origin` / `Referer`（这三个头只在 `Anime1DataSource.open()` 中设置）。
- 切到后台时 `PlayerScreen` 会调用 `pause()`；但暂停不保证立即停止网络读取，ExoPlayer 仍可能按其缓冲策略继续加载一段数据。离开播放页时播放器会被释放。应用没有预取下一集。

## 5. 频率与缓存总结

- **成功后在进程内复用**：`animelist.json`（动画总表）、Anime1 首页当前季发现；首次请求失败后，用户重试会再次请求。
- **按 key 缓存，命中后不再请求**：季度排期（按季度标签缓存）。
- **每次触发都重新请求，不缓存**：剧集列表分页（每次打开动画都会重新拉取全部分页）、播放地址解析（每次点击播放都重新请求）。
- **没有任何轮询/心跳/预取**：所有请求都是用户导航或点击后的一次性结果，应用位于后台或空闲时不会自主发起请求。

## 6. 典型场景请求量估算

以下面这个使用路径为例（数字为该步骤新增的元数据请求次数，不含视频流量）：

```text
1. 冷启动，停留在“动画”页                              → 1  (animelist.json)
2. 打开一部剧集拆成 5 页的动画                           → 5  (?cat=id + 4 次翻页)
3. 播放第 1 集                                          → 1  (POST /api)
4. 播放中断网重连，点击“重试”                             → 2  (GET 单页 + POST /api)
5. 返回动画列表，进入“季度新番”（本进程首次）               → 2  (GET / + GET 当前季页面)
6. 切换到另外 2 个此前未访问过的季度                       → 2  (每季度 1 次)
7. 返回，再次打开步骤 2 中同一部动画（无缓存，全量重取）      → 5
────────────────────────────────────────────────────────
合计                                                    → 18 次元数据请求
```

其中第 7 步（重复打开同一动画）在长时间使用、反复比较剧集的场景下会持续累加，是当前实现中最容易放大请求量的部分。

## 7. 已识别的风险点

- **剧集分页无缓存、无节流**：长篇动画分页越多，每次打开/重新打开该动画产生的连续 GET 越多；请求间没有延时，是短时间内对服务器发起请求最密集的路径。
- **播放重试会额外产生 1–2 次请求**：先重取 1 次分类页刷新签名；仅在找到原剧集后再请求播放接口。网络不稳定导致的多次手动重试会线性增加请求数。
- **无 HTTP 层面的失败退避**：`Anime1HttpDataSource` 没有实现指数退避或对 `429`/`5xx` 的特殊处理；失败后完全依赖用户手动点击“重试”，不会自动连续重试从而造成雪崩，但也不会智能降速。
- **暂停后仍可能继续缓冲**：切到后台只调用 `pause()`，不等同于立即取消媒体请求；若需要严格避免后台流量，应显式控制播放器的停止与恢复。

## 8. 可能的优化方向（仅供参考，本文档不涉及实现）

- 为剧集分页恢复“按需加载/手动加载更多”，或至少限制自动加载的页数上限。
- 为剧集列表增加与季度排期类似的进程内缓存，避免重复打开同一动画时重新拉取全部分页。
- 为 `animelist.json` 等静态性较强的数据增加 TTL 或轻量本地磁盘缓存。
- 在 `Anime1HttpDataSource` 中加入基础的失败退避策略，并识别服务端的限流响应（如 `429`/`Retry-After`）。

如后续 `Anime1DataSource.kt` 或 `AnimeViewModel.kt` 中的请求触发逻辑发生变化，请同步更新本文档。
