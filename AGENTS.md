# AnimeMiTV Agent 指南

## Agent skills

### Issue tracker

本仓库使用 GitHub Issues 管理 issue 和 spec。参见 `docs/agents/issue-tracker.md`。

### Domain docs

本仓库使用单上下文领域文档布局。参见 `docs/agents/domain.md`。

## 构建环境

如果系统 `java` 不可用，使用 Android Studio 自带的 JBR 运行 Gradle：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
```
