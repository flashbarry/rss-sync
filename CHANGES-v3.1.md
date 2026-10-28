# v3.1.1 改动说明

- 3.1.1：设置对话框可滚动；翻译设置、缓存清理同时放进侧边栏和右上角菜单；设置标题显示版本号。


## 新增：文章翻译
- 文章页右上角「翻译」按钮：无译文时翻译，有译文时在原文/译文间切换；顶部状态条可重试、重译。
- 设置 → 翻译设置：
  - 微软 Azure Translator（官方接口，需 Key + 区域；免费档 F0 每月 200 万字符）
  - AI 大模型（OpenAI 兼容接口），一键填入 DeepSeek / Kimi / 通义千问 / Gemini，也可填任意兼容地址
  - 目标语言：简体 / 繁体 / English / 日本語；可开启「打开文章时自动翻译」；「保存并测试」验证配置
- 按段落切分 HTML 后翻译，保留图片、链接、排版。
- 译文缓存在独立表 `translations`，同步不会冲掉，同一篇不重复计费。数据库 v1→v2 迁移，原有数据保留。

## 新增：定时清理
- 设置 → 缓存清理：开关、频率（每天 / 3 天 / 每周）、保留天数（3/7/14/30）、是否清理过期未读、是否清空图片缓存、立即清理。
- WorkManager 后台执行；收藏永远保留；只清本机缓存，不影响 FreshRSS 服务器。
- 开启后，同步不会再把已过保留期的文章拉回本地。

## 改动文件
新增：data/local/TranslationEntity.kt、TranslationDao.kt、data/translate/TranslationService.kt、
      work/CleanupWorker.kt、ui/screens/ExtraSettingsDialogs.kt
修改：AppDatabase.kt、ArticleDao.kt、PreferencesManager.kt、RssRepository.kt、MainViewModel.kt、
      SettingsDialog.kt、ArticleListScreen.kt、ArticleReaderScreen.kt、app/build.gradle.kts（WorkManager、版本 3.1）、
      gradle/libs.versions.toml

## 关于 debug.keystore
app-debug.apk 用本目录的 debug.keystore 签名。用 Android Studio 自己编译时保留这个文件，
新包就能直接覆盖安装，不用每次卸载。它在 .gitignore 里，不会被推到 GitHub。
