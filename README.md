# 尺子 Ruler

[![Release](https://img.shields.io/github/v/release/MaximeMET/ruler?label=release)](https://github.com/MaximeMET/ruler/releases/latest)
[![License](https://img.shields.io/github/license/MaximeMET/ruler?label=license)](LICENSE)
[![APK size](https://img.shields.io/badge/APK-110%20KB-4f46e5)](https://github.com/MaximeMET/ruler/releases/latest)

把手机屏幕当尺子用的开源 Android 应用：测量厘米、毫米、英寸与角度。

**下载**：[Releases](https://github.com/MaximeMET/ruler/releases/latest) 里的 `Ruler-1.0.0.apk`，
约 110 KB，支持 Android 6.0（API 23）及以上。

## 特性

* 没有广告：AdMob、Pangle、AppLovin、Yandex、IronSource、Firebase 等全部不存在
* 不申请任何权限：清单里连 `INTERNET` 都没有，装完就是一个纯离线工具
* 零第三方依赖：只用 Android Framework + Kotlin 标准库，release 包约 110 KB
* 17 种语言，默认跟随系统语言，也可以在设置里单独指定
* 深色主题（默认）/ 浅色主题，靛蓝 + 紫罗兰配色
* 矢量自适应图标：Android 8+ 走自适应图标（含 Android 13 主题图标），更早的系统用同一套图形的方角图标

## 功能

| 功能 | 说明 |
| --- | --- |
| 全屏尺子 | 顶部、底部、左侧三条刻度；厘米 / 毫米 / 英寸三种单位，1 mm 或 1/8 inch 一格 |
| 单点测量 | 一条可拖动的边，测量从屏幕边缘到该边的距离 |
| 两点测量 | 两条可拖动的边，测量两点之间的距离 |
| 矩形测量 | 可拖动矩形，实时显示宽、高与面积 |
| 量角器 | 半圆刻度盘，两根可拖动的针，双指可同时调整夹角 |
| 校准 | 用银行卡长边（85.60 mm）做参照，加减按钮或直接拖动微调，系数实时保存 |
| 设置 | 屏幕方向、屏幕常亮、深色主题、边缘留白、语言、测量单位 |

## 截图

| 尺子 | 矩形测量 | 量角器 | 校准 |
| --- | --- | --- | --- |
| ![main](docs/screenshots/main.png) | ![rect](docs/screenshots/measure-rect.png) | ![protractor](docs/screenshots/protractor.png) | ![calibration](docs/screenshots/calibration.png) |

| 设置（深色，默认） | 关于 |
| --- | --- |
| ![settings](docs/screenshots/settings.png) | ![about](docs/screenshots/about.png) |

## 语言

默认**跟随系统语言**；如果系统语言不在支持列表里，会回落到英文。设置页的「语言」一行可以在
应用内直接切换，列表里每一项都用该语言自己的写法（English、简体中文、日本語、Русский…），
不用先看懂当前界面也能找到自己的语言。

目前带完整翻译的语言：English、简体中文、繁體中文、日本語、한국어、Español、Português (Brasil)、
Français、Deutsch、Italiano、Русский、Türkçe、العربية、हिन्दी、Bahasa Indonesia、Tiếng Việt、ไทย。

![语言选择](docs/screenshots/language.png)

实现在 `core/Locales.kt`：语言选项存在 `SharedPreferences`，每个 Activity 在 `attachBaseContext()`
里用 `createConfigurationContext()` 套上对应语言；Android 13+ 另外通过 `android:localeConfig`
注册到系统的「应用语言」页面，两处设置保持同步。工具栏弹窗与所有读数统一使用拉丁数字与小数点，
和刻度上的数字保持一致。

想贡献新语言也很简单：把 `app/src/main/res/values/strings.xml` 复制成 `values-<语言代码>/strings.xml`
翻译一遍，再把语言加进 `core/Locales.kt` 的 `choices` 与 `res/xml/locales_config.xml` 就行，
不需要改其它代码。

## 配色与图标

配色以靛蓝 `#4F46E5` 为主、紫罗兰 `#7C3AED` 为辅：

| | 深色主题（默认） | 浅色主题 |
| --- | --- | --- |
| 画布 | `#0B0E14` 近黑 | `#F4F6FB` 低亮度灰白 |
| 工具栏 | `#151A24` | `#4F46E5` |
| 刻度高亮 | `#818CF8` | `#4F46E5` |
| 单点 / 两点 / 矩形 / 量角器 | `#60A5FA` / `#34D399` / `#FBBF24` / `#C084FC` | `#2563EB` / `#059669` / `#EA580C` / `#7C3AED` |

深色主题默认打开，因为纯白底在暗环境里太刺眼。工具栏、单位切换胶囊、设置页图标都跟随主题取色。

图标是矢量绘制的圆角方形尺身，顶部刻度 + 左侧短刻度 + 挂孔；Android 8+ 使用自适应图标，
包含前景、背景与单色三层，Android 7 及以下使用同一套图形的方角版本。

## 构建

需要 JDK 17 与 Android SDK（`compileSdk 35`）。项目自带 Gradle Wrapper：

```bash
# 调试包
./gradlew assembleDebug

# 发布包（默认用本地 debug keystore 签名，正式发布前请替换成自己的签名）
./gradlew assembleRelease
```

第一次执行 wrapper 会下载 Gradle 8.9（约 130 MB）；如果网络受限，也可以装好 Gradle 8.9 后
直接执行 `gradle assembleRelease`，或者用 Android Studio 打开仓库根目录构建。

产物在 `app/build/outputs/apk/` 下。安装到手机：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

release 构建默认开启 R8 代码压缩与资源压缩，并剔除 Kotlin 的反射元数据，本仓库构建出来的
`app-release.apk` 约 110 KB（其中约 40 KB 是 17 种语言的文案；debug 包约 1 MB，因为它不做任何压缩）。

「关于」页底部的源码按钮指向 <https://github.com/MaximeMET/ruler>；如果你 fork 了本项目，
把 `app/src/main/java/org/openruler/app/AboutActivity.kt` 里的 `SOURCE_URL` 换成自己的仓库地址即可。

也可以直接用 Android Studio 打开仓库根目录，同步后点运行。

## 项目结构

```
app/src/main/java/org/openruler/app/
├── MainActivity.kt              # 测量主界面（全屏尺子 + 工具）
├── CalibrationActivity.kt       # 校准界面
├── SettingsActivity.kt          # 设置界面（代码生成列表行）
├── AboutActivity.kt             # 关于 / 许可
├── ProtractorActivity.kt        # 可以单独作为快捷方式启动的量角器
├── BaseActivity.kt              # 主题（浅色/深色）与调色板
├── core/
│   ├── Units.kt                 # 单位、测量模式、像素与物理长度换算
│   ├── Prefs.kt                 # SharedPreferences 封装
│   ├── Locales.kt               # 语言列表与语言切换
│   └── Palette.kt               # 调色板与颜色工具
└── view/
    ├── RulerView.kt             # 三向刻度、测量区域内反色重绘
    ├── MeasureView.kt           # 单个 / 两个 / 矩形测量工具
    ├── ProtractorView.kt        # 量角器
    ├── CalibrationRulerView.kt  # 校准尺（银行卡参照）
    └── UnitToggleView.kt        # 单位切换胶囊控件
```

## 测量精度

刻度的换算关系是：

```
每格像素 = 屏幕 xdpi（或 ydpi）/ 25.4 × 校准系数     // 厘米 / 毫米模式
每格像素 = 屏幕 xdpi（或 ydpi）/ 8 × 校准系数        // 英寸模式
```

手机上报的 `xdpi` / `ydpi` 只是厂家标称值，往往和真实尺寸有偏差，所以第一次使用建议先校准：
在校准页把银行卡的长边贴到蓝色方框上，用 `+` / `−` 或直接拖动刻度，直到银行卡完全对齐方框，
再点右上角保存。校准系数会作用于所有模式，直到你再次修改。

## 与参考应用的关系

这是一个**功能对等的重写**，不是原 APK 的重打包。源码、图标与界面资源全部重新编写、绘制，
仓库里不含参考应用 `org.nixgame.ruler`（作者 Evgrafov Aleksei）的任何代码、图片、字体或 APK 文件。

相比参考应用，这里移除了广告（AdMob、Pangle、AppLovin、Yandex、IronSource）、
Firebase / AppMetrica 统计、评分弹窗、内购（Pro 版）与自家应用互推。

## 许可证

[MIT](LICENSE)。你可以自由使用、修改、再发布，包括商用。

## 免责声明

软件按「现状」提供，不附带任何担保。尺子精度取决于触摸屏的像素密度标称值与校准结果，
测量值仅供参考，不作为计量依据。
