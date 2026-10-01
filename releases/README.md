# releases/ —— 版本发布归档

这里按模组、按版本号存放**所有编译好的 jar**，旧版本永远保留，方便回退和对比。

## 目录结构

```
releases/
├── rainbow-lotus/
│   ├── v1.0.0/        ← rainbow_lotus-1.0.0.jar 等
│   ├── v1.1.0/        ← rainbow_lotus-1.1.0.jar 等
│   └── ...
└── terra-plate-extension/
    ├── v1.0.0/
    ├── v1.0.1/
    └── ...
```

## 归档规则

每次构建发布新版本时：

1. 改 `gradle.properties` 里的 `mod_version`
2. 构建：`.\gradlew.bat build --no-daemon`
3. 把 `build\libs\<mod_id>-<版本号>.jar` 复制到 `releases\<模组名>\v<版本号>\`
4. 再把同一个 jar 复制到游戏 mods 目录

> 注意：旧版本**不要删**。如果新版本出问题，随时可以拿旧 jar 回退。
