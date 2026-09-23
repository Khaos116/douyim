# 混淆策略对齐 Sesame-M（同为 libxposed 102 模块，已验证做法）：
# hook 包整体保留——Xposed 回调/反射/动态代理在 ART 懒加载期才验证，
# 类改名不同步会导致概率性 NoClassDefFoundError；业务逻辑正常混淆。

# 保留异常堆栈的源文件名与行号，方便排错
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Xposed 模块入口按字符串加载，同步适配资源内容
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep class * implements io.github.libxposed.api.XposedModule { *; }
-keep class com.zz.douyin.hook.** { *; }

# compileOnly 的 libxposed API 由宿主框架提供，混淆期不在场，只消警告
-dontwarn io.github.libxposed.api.**
