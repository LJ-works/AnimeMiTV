# Issue tracker：GitHub

本仓库的 issue 和 spec 存放在 GitHub Issues 中。相关技能使用 `gh` CLI 操作。

## 约定

- 创建 issue：`gh issue create --title "..." --body "..."`
- 查看 issue：`gh issue view <number> --comments`
- 列出 issue：`gh issue list --state open`
- 评论 issue：`gh issue comment <number> --body "..."`
- 添加或移除标签：`gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- 关闭 issue：`gh issue close <number> --comment "..."`

在仓库目录中执行命令时，`gh` 会根据 Git remote 自动识别仓库。

## PR 请求入口

**PR 不作为 triage 请求入口：否。**

当技能要求“发布到 issue tracker”时，应创建 GitHub issue。
