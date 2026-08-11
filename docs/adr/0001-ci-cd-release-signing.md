# CI/CD 版本与长期签名

AnimeMiTV 使用 Release Please 根据 Conventional Commits 维护 SemVer，并让 Gradle 与发布流程共享 `version.txt`；正式 Release 使用长期 Android keystore 签名 APK。keystore 和密码只通过 GitHub Repository secrets 注入，因为更换或丢失签名身份会破坏已有用户的覆盖升级路径；签名 workflow 同时校验 APK 版本、签名和 SHA-256 后再上传到 Release。
