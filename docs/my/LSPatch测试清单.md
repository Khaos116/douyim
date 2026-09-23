# LSPatch Local 兼容测试清单（Phase 10）

> 任务书 §二十一/§二十二：LSPosed 是第一基线，LSPatch Local 是兼容目标。
> 本模块在无真机可测前**不做推测性兼容改造**；以下清单由用户在设备上逐项验证，
> 只有出现真实失败点才写兼容代码（方案 A→B→C 见文末）。

## 0. 审计结论（代码静态核对，2026-09-23）

- Hook 侧配置入口唯一：`DouyinModule` → `getRemotePreferences("content_filter")`，
  已加固——取不到时记 `[LSPatch]` warn 并跑默认值，不再让整个 tracker 子系统安装失败。
- 设置侧：`MainActivity` 无 service 时已是禁用态 + 提示语，不会崩；
  `ModuleApplication` 的 `registerListener` 已加固，框架缺席时只记 warn。
- 无 DexKit、无 world-readable、无跨 UID SharedPreferences 假设；
  日志落目标 App 自己的 media 目录，LSPatch 同进程下同样可写。
- 未验证的最大风险：LSPatch 对 libxposed Modern API（API 102 `XposedModule`）的支持程度，
  必须真机实测第 1 步。

## 1. 基础加载（先过这 5 项，再测功能）

- [ ] 1. 用 LSPatch 以本地模式打包抖音 + 本模块，安装后抖音可正常启动、无闪退
- [ ] 2. logcat 出现 `loaded in com.ss.android.ugc.aweme, framework=...`（模块已加载）
- [ ] 3. logcat 出现 `hook installation finished for com.ss.android.ugc.aweme`
      且 9 个 `hook subsystem installed` 无 `failed`
- [ ] 4. 若出现 `[LSPatch] remote preferences unavailable`：
      记录下来继续测（模块跑默认值），这是方案 B 的触发条件
- [ ] 5. 播放视频，确认无新增崩溃（`AndroidRuntime:E` 无本模块栈）

打包/安装命令示例（以实际 LSPatch 版本为准）：

```powershell
adb logcat -v threadtime -s DouyinImmersive AndroidRuntime:E > lspatch-log.txt
```

## 2. 功能矩阵（与 LSPosed 同一套用例）

- [ ] 过滤：广告 / 直播 / 图文 / 关键词 / 长视频各触发一次，正常上滑且只跳一条
- [ ] 自动下一条：播完自动下一条，不连跳两条；关开关后停在末尾
- [ ] 精确数字：点赞/评论/收藏显示完整数字（对照 `[Statistics]` 行）
- [ ] 发布时间/IP 属地/地点：overlay 行数与标注正确（对照 `[PublishInfo]` 行）
- [ ] 颜色：改三色生效，关总开关恢复原样式
- [ ] 下载/MP3/复制链接：暂停态两按钮正常，文件落盘，链接可粘贴
- [ ] 界面隐藏：发布按钮 / TAB 关键词隐藏与恢复
- [ ] 日志查看器：插件内“查看运行日志”可读到当天文件

## 3. 回报格式（发给 AI 时附带）

1. 上面哪一项失败（编号 + 现象 + 复现步骤）
2. `lspatch-log.txt`（复现后立刻导出）
3. LSPatch 版本、打包模式（local / integrated）、抖音版本、Android 版本
4. 设置 App 能否打开、开关是否可改（决定方案 B/C）

## 4. 兼容方案触发条件（给 AI 的后续指令）

- 全部通过 → Phase 10 关闭，不写任何兼容代码。
- 仅第 4 项出现（配置不可用，功能跑默认值正常）→ 做方案 B：
  `ConfigProvider` 接口 + `LsposedConfigProvider`（现状搬迁，零行为变化），
  再按实测进程/UID 决定 `LocalConfigProvider` 形态；严禁 world-readable。
- 模块根本不加载（第 2/3 项失败）→ 先查 LSPatch 对 API 102 Modern API 的支持，
  可能需要降级 API 或换入口；改动前先在 LSPosed 回归。
- 其他单项失败 → 按 Feature 独立修，修完 LSPosed + LSPatch 双回归。
