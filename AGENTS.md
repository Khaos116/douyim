# AGENTS.md — douyim fork 改造工程

> 给编程智能体（Codex / Muse Code / Claude Code / Gemini CLI）看的工程说明。
> 需求总纲在任务书里，本文件只定工作流、铁律和收尾标准，冲突时以任务书为准。

## 1. 项目与任务书

- 本仓库是用户 fork：`origin = https://github.com/Khaos116/douyim.git`，上游是 `1zzzzzlll/douyim`（抖仙人 LSPosed 模块，包名 `com.zz.douyin`）。
- **任务书（唯一需求来源）**：[`docs/my/抖仙人_功能迁移与LSPatch_Local兼容改造执行方案.txt`](docs/my/抖仙人_功能迁移与LSPatch_Local兼容改造执行方案.txt)，共 48 节，开工前通读。
- 改造目标（任务书 §目标4）：在保持抖仙人架构的前提下，参考 FreedomPlus / Dou+ 迁移功能——自动下一条、广告/直播/图文/关键词/长视频过滤、无水印下载、MP3、精确点赞/评论/收藏/分享数、发布时间常显、IP 属地/POI 常显、文本颜色可配、UI 常显控制；LSPosed 为第一基线，兼顾 LSPatch Local。
- **红线**：只动客户端 UI、播放行为、内容过滤、本地媒体保存；绝不碰登录、支付、会员、风控、签名校验、设备认证、安全验证等逻辑。

## 2. 分支与提交工作流（强制）

- 所有改造只在 **`my_dev`** 分支进行；当前 `my_dev` 与 `master` 一致。
- 开工前必做：`git status` 确认干净 + `git rev-parse --abbrev-ref HEAD` 确认是 `my_dev`。
- 远端目前只有 `origin/master`，**绝不直接 push master**；改造完成后推到远端 `my_dev`：
  - 首次推送：`git push -u origin my_dev`
  - 之后：`git push`
- 一个 Phase 一个 commit，一个 commit 只做一件事，message 规范（任务书 §三十二）：
  - `refactor: unify feed navigation`
  - `feat: add auto next after playback completion`
  - `fix: support local config under lspatch`
- 每完成一个 Phase：写修改记录（§3）→ 本地全量构建通过（§6）→ commit → push 到 `origin/my_dev`。
- 不要把多个 Phase 塞进一个 commit；不要顺手改无关代码。

## 3. 修改记录文档（强制）

- 记录文件：`docs/my/修改记录.md`（running log，不存在则创建；按 Phase 从上往下追加，不要覆盖历史）。
- 每个 Phase（含 Phase 0 基线）完成后必须追加一条，模板：

```markdown
## Phase N：标题（YYYY-MM-DD）

- commit：<hash> `<message>`
- 修改文件：
  - `app/src/.../Xxx.java`（新增/修改/删除，一句话说明）
- 核心逻辑：（数据流 / Hook 点 / 关键决策）
- 风险点：（版本适配风险、回归面）
- 真机验证：
  - [ ] LSPosed：...
  - [ ] LSPatch Local：...（如适用）
- 备注：（许可证核对结果、遗留问题）
```

- 记录文档本身随该 Phase 的 commit 一起提交。

## 4. Phase 顺序（一次只做一个，做完停下等确认）

按任务书 §三十严格执行，不得跳序、不得并行多个 Phase：

| Phase | 内容 | 产出 commit 示例 |
|---|---|---|
| 0 | 基线：读代码 + 原版构建通过，不改代码 | （只写记录，不提交代码） |
| 1 | 统一下一条为 `FeedNavigator.moveToNext(reason)`，行为不变 | `refactor: unify feed navigation` |
| 2 | 播放完成自动下一条（复用 PlayerHooks/PlaybackState） | `feat: add auto next after playback completion` |
| 3 | `AwemeAccessor` / `StatisticsAccessor` 数据抽象，不改 UI | `refactor: add aweme data accessors` |
| 4 | 精确人数（先日志验证原始值，再接 UI） | `feat: show exact engagement counts` |
| 5 | 发布时间常显 | `feat: keep publish time visible` |
| 6 | IP 属地/POI/地点显示（先验证字段真实存在） | `feat: show available publish location metadata` |
| 7 | 文本颜色可配 | `feat: add configurable feed text colors` |
| 8 | 长视频过滤（参考 FreedomPlus 思路） | `feat: add long video filter` |
| 9 | 下载增强（先做三方差异分析，只补缺失） | 按能力拆分提交 |
| 10 | LSPatch Local 实测兼容（先测出真实失败点再改） | `fix: ...` |

- Phase 0 的基线输出（架构图、6 条数据流、Phase 1 最小方案）写进修改记录，不单独建文件。
- 每个 Phase 的详细验收标准见任务书 §三十～§三十一。

## 5. 架构铁律（违反即返工）

1. **一个 currentAweme**：唯一归属 `FeedContentTracker`，禁止出现第二套状态（FreedomPlus/Dou+ 式各管各的）。
2. **一个 moveToNext**：所有“下一条”只走 `FeedNavigator.moveToNext(reason)`，reason 显式命名（`AUTO_PLAY_FINISHED` / `FILTER_AD` / …）。
3. **版本变化只修 resolver/accessor**：新增 Hook 经 `compat/` resolver 解析，Feature 不散落 `findClass` 和写死的混淆名；版本分支收敛到 `DouyinVersionCompat`，禁止到处 `if versionName == ...`。
4. **故障隔离**：沿用 `DouyinModule.installSubsystem(name, installer)` 模式；单个 Feature 解析/安装失败只记日志停用该 Feature，不拖垮模块和抖音进程。启动日志打印各 Feature `READY` / `FAILED: 原因`。
5. **Feature Flag**：所有新功能独立开关（`FEATURE_AUTO_NEXT` / `FEATURE_FILTER_AD` / …），总开关关闭时全部停用；配置只经 `ConfigProvider` 接口，不直调 Preferences API（为 LSPatch 留出 `LocalConfigProvider`）。
6. **高频路径禁令**：Feed bind 内禁止 Dex 扫描、遍历 ClassLoader、读大文件、网络请求、大规模反射；只允许已缓存 `Method/Field` 调用 + 简单格式化 + View 更新。Dex 搜索只在初始化时跑一次并缓存。
7. **数据诚实**：精确人数必须读 statistics 原始 long 值，禁止反解析“1.2万”；地点只显示模型真实字段，无数据则隐藏——**IP 属地必须叫 IP 属地，禁止标成拍摄地点，禁止伪造**。
8. **许可证**：复制任何第三方代码前先查 LICENSE/README/文件头并记入修改记录；许可证不明则只参考思路自行重写；保留原作者版权声明。
9. **渐进拆分**：`feature/`、`model/`、`compat/`、`config/`、`util/` 按需逐步建，不要一次搭全套空架子。

## 6. 构建、测试与日志

- Windows PowerShell 全量门禁（lint `abortOnError = true`，不过即失败）：
  ```powershell
  .\gradlew.bat clean lintRelease test assembleRelease
  ```
- 纯决策逻辑（Policy/State/Accessor）必须有 `app/src/test/.../*Test.java` 覆盖并通过 `.\gradlew.bat test`。
- 构建产物在系统临时目录 `douyin-immersive-gradle/app/outputs/apk/release`；`dist/` 只收用户验证过的发布件，改造分支不要顺手更新 `dist/` 和 README 版本号（除非用户明确要求发版）。
- 日志 tag 继续沿用 `DouyinImmersive`，加 Feature 前缀：`[AutoNext]` `[Filter]` `[Aweme]` `[Statistics]` `[PublishInfo]` `[FeedUi]` `[Download]` `[LSPatch]`；**禁止输出 cookie / token / 登录凭证**。

## 7. 动手前的调查清单

写代码前必须能回答（任务书 §三十七），答不上就继续读代码，不许直接开发：

1. currentAweme 如何获得？`FeedContentTracker` 生命周期？
2. `PlaybackState` 存什么？`PlayerHooks` Hook 了什么？
3. 现有过滤如何触发下一条？`VideoDownloader` 从哪取 URL？
4. `FilterPreferences` 如何同步？设置页如何写值？Modern API 入口在哪？
5. 哪些 Hook 在主线程？当前如何应对抖音版本变化？
6. （迁移功能时）参考项目的 Hook 点/字段/依赖是什么？结论是复用思路、小段移植还是不移植？（任务书 §三十八）

可用工具：Serena 记忆（`core` / `tech_stack` / `conventions` / `suggested_commands` / `task_completion`）、codebase-memory 图谱（project `douyim`）、`graphify-out/` 知识图谱（`graphify query "..."`）。

## 8. 完成标准

以任务书 §四十一 的 21 条为准，核心抽查：LSPosed 无崩溃、原功能无回归、各过滤与自动下一条正常（不连跳两条）、数字/时间/地点与当前视频一致且 RecyclerView 不串显、颜色关闭可恢复、下载/MP3 正常、新 Feature 均可单独关闭、单 Feature 失效不拖垮进程、全量构建通过、LSPatch 有明确测试结论（不兼容项标记 unsupported，不静默异常）。
