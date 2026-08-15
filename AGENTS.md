# AnimeMiTV Agent 指南

## Agent skills

### Issue tracker

本仓库使用 GitHub Issues 管理 issue 和 spec。参见 `docs/agents/issue-tracker.md`。

### Domain docs

本仓库使用单上下文领域文档布局。参见 `docs/agents/domain.md`。

## 提交流程

实现完成并通过自动化检查后，保留修改在工作区，等待用户人工测试确认；只有收到明确确认后才允许 commit、push 或创建 PR。

用户确认后，从功能分支推送并创建 PR；由 PR 合并进入 `main`。禁止直接推送到 `main`。

### PR 前检查

创建 PR 前，逐项检查所有代码修改是否有覆盖其用户可见行为的自动化测试；发现缺口时，先新增并运行相应测试。再检查功能修改是否影响 `README.md` 或 `ARCHITECTURE.md`；有影响时，在同一分支更新相应文档。

## 构建环境

如果系统 `java` 不可用，使用 Android Studio 自带的 JBR 运行 Gradle：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
```
