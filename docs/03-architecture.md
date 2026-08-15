# 03 · 架构文档

## 分层

```
┌─────────────────────────────────────────────┐
│  Surface  (Compose)                         │
│  Canvas · Search · Browse · GestureOverlay  │
│  唯一职责：构造 Query，渲染 List<Target>      │
└──────────────────┬──────────────────────────┘
                   │  Query
┌──────────────────▼──────────────────────────┐
│  Resolver                                   │
│  Query → List<Target>   全系统唯一解析入口    │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│  Engine  (纯 JVM，可单测)                     │
│  IndexStore · PinyinEngine · Ranker          │
│  TagResolver · GestureRegistry · Predictor   │
└──────────────────┬──────────────────────────┘
                   │  Target
┌──────────────────▼──────────────────────────┐
│  Source                                     │
│  LauncherApps · ShortcutManager             │
│  Contacts · Settings · WebFallback          │
└─────────────────────────────────────────────┘
```

依赖方向严格自上而下。Engine 不知道 Surface 存在，Source 不知道 Engine 存在。

## 核心数据模型

```kotlin
data class Target(
    val id: String,             // 稳定 URI: app://pkg/activity, shortcut://pkg/id,
                                //           contact://lookupKey, setting://action
    val kind: Kind,             // APP / SHORTCUT / CONTACT / SETTING / ACTION / WEB
    val label: String,
    val aliases: List<String>,  // 拼音全拼、首字母、英文名、用户自定义别名
    val tags: Set<Tag>,         // 引擎生成，非用户维护
    val iconUri: String?,       // 只存 URI，不存 Drawable
    val launch: LaunchSpec
)

data class Query(
    val text: String? = null,       // 来自 Search（键盘或语音）
    val tag: Tag? = null,           // 来自 Browse
    val gestureId: String? = null,  // 来自 Gesture
    val limit: Int = 6
)

data class ScoredTarget(
    val target: Target,
    val score: Float,
    val reason: MatchReason,        // 用于 UI 显示"首字母"/"今天 3 次"
    val breakdown: ScoreBreakdown   // 调试用，Release 可编译掉
)
```

`id` 必须稳定：应用更新、重装后不变，否则 frecency 和手势绑定会丢。

## 模块契约

### IndexStore

内存倒排 + 前缀 Trie。**不用 SQLite，不用 Room。** 200–500 条目全内存，冷启动从 Proto DataStore 反序列化快照 < 20ms，之后后台增量重建。

```kotlin
interface IndexStore {
    fun lookup(prefix: String): Sequence<Target>
    fun all(): Sequence<Target>
    suspend fun rebuild()
    fun observe(): StateFlow<IndexVersion>
}
```

监听 `ACTION_PACKAGE_ADDED` / `REMOVED` / `CHANGED` 做增量，不做全量重建。

### PinyinEngine

三层索引，构建期一次性生成：

| 层 | 例 | 权重 |
|---|---|---|
| 全拼 | `weixindushu` | 高 |
| 首字母 | `wxds` | 中 |
| 模糊音 | z↔zh, c↔ch, s↔sh, l↔n, an↔ang, en↔eng, in↔ing | 低 |

多音字：优先取词典中该字在人名/常用词中的高频读音，不做上下文推断。

```kotlin
interface PinyinEngine {
    fun index(label: String): PinyinKeys      // 构建期
    fun match(query: String, keys: PinyinKeys): MatchQuality?  // 运行期
}
```

### Ranker

```kotlin
object Weights {
    const val MATCH = 0.45f
    const val FRECENCY = 0.30f
    const val CONTEXT = 0.15f
    const val PIN = 0.10f
}
```

`frecency = count / (1 + daysSinceLastUse)`，30 天滑动窗口。权重是常量表，调参不改逻辑。

### TagResolver

分类来源优先级：

1. 用户对单条目的覆盖（持久化）
2. `ApplicationInfo.category`
3. 内置 metadata 映射表（按包名前缀，随版本更新）
4. 首次安装时一次性兜底打标

用户不能创建/重命名/删除分类，只能覆盖单个条目的归属。

### GestureRegistry

只存 `gestureId → targetId` 映射，零业务逻辑。手势识别参数：阈值 48dp，上限 300ms，起点必须落在 Canvas 空白区。

### Predictor

预热 top-8 的 icon 与 `PendingIntent`。也负责手势提议：连续 7 天进 top-3 且未绑定 → 触发一次提议事件。

## 目录结构

```
app/
  src/main/kotlin/com/rubyketang/launcher/
    ui/                    # ← 不允许出现排序/过滤/匹配逻辑
      canvas/
      search/
      browse/
      gesture/
      theme/               # Design tokens，见 04-design-system.md
    resolver/
      Resolver.kt
      Query.kt
    engine/                # ← 纯 JVM，无 Android UI 依赖
      index/
      pinyin/
      rank/
      tag/
      gesture/
      predict/
    source/
      AppSource.kt
      ShortcutSource.kt
      ContactSource.kt
      SettingSource.kt
    model/
      Target.kt
  src/test/kotlin/         # 引擎层单测，不需要 Robolectric
```

## 数据流

```
用户上滑
  → SearchSurface 构造 Query(text = "wxds")
  → Resolver.resolve(query)
      → IndexStore.lookup("wxds")        取候选
      → PinyinEngine.match()              算 matchQuality
      → Ranker.score()                    合成总分
      → 取 top-6
  → SearchSurface 渲染 List<ScoredTarget>
用户点击
  → Resolver.launch(target)
  → UsageRecorder.record(target.id)       写回 frecency
```

语音路径完全相同，只是 `Query.text` 来自 `onPartialResults` 而非键盘，每 200ms 触发一次。

## 性能预算

| 指标 | 预算 |
|---|---|
| 冷启动到首帧可交互 | < 150ms |
| 索引反序列化 | < 20ms |
| 单次 query（500 条目） | < 8ms |
| 语音 partial 刷新 | 200ms |

超预算就去修实现，不加骨架屏掩盖。

## 开发顺序

`P0-1 → P0-2 → P0-3 → P0-4`（引擎，纯单测验证）→ `P0-5 ~ P0-8`（Surface）→ `P0-9 / P0-10`（工程化）→ P1。

**不要先做 UI。** 这套设计的成败全在搜得准不准。引擎跑通前，UI 有多丑都无所谓。
