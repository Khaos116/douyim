import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

plugins {
    alias(libs.plugins.android.application)
}

// 应用版本集中定义：defaultConfig 与 APK 文件名共用，改一处即可
val appVersionName = "1.5.1"

layout.buildDirectory.set(
    File(System.getProperty("java.io.tmpdir"), "douyin-immersive-gradle/app")
)

// Release 签名（机制对齐 Sesame-M）。
// 本仓库密钥公开：xqe.jks 与 keystore.properties 直接进 Git（与 Sesame-M 同一签名，直接复制）。
// 根目录存在 keystore.properties 时使用其中的 release 密钥（v1+v2）；
// 不存在时回退到 debug 签名，构建仍可完成。
// 注意：此密钥与 1.5.1 及之前 debug 签名发布件不是同一签名身份，
// 老用户覆盖安装会报签名不一致，需卸载重装。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasKeystoreConfig = keystorePropertiesFile.exists()
if (hasKeystoreConfig) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

android {
    namespace = "com.zz.douyin"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zz.douyin"
        minSdk = 28
        targetSdk = 35
        versionCode = 17
        versionName = appVersionName
    }

    if (hasKeystoreConfig) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasKeystoreConfig) {
                signingConfigs.getByName("release")
            } else {
                logger.lifecycle("未找到 keystore.properties，release 回退到 debug 签名")
                signingConfigs.getByName("debug")
            }
        }
    }

    // 纯 Java 模块无 native 库，不做 Sesame-M 式的 ABI 拆分，只产出 universal 包

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

// Release 产物固定命名，与 dist/ 历史发布件同约定
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("douxianren-lsp-api102-v$appVersionName.apk")
        }
    }
}

// Release 打包成功后，把产物 APK 按北京时间归档到项目根目录 APK/Release（对齐 Sesame-M）。
// 本仓库 buildDirectory 重定向到了系统临时目录，归档能让产物落到固定位置；
// APK/ 已加入 .gitignore，不进 Git；对外发布件仍走 dist/（人工验证后拷贝）。
afterEvaluate {
    tasks.matching { it.name == "assembleRelease" }.forEach { task ->
        val outputDir = layout.buildDirectory.dir("outputs/apk/release")
        val archiveDir = rootProject.layout.projectDirectory.dir("APK/Release").asFile.toPath()
        task.doLast {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.format(Date())
        Files.createDirectories(archiveDir)
        outputDir.get().asFile.listFiles { file: File -> file.name.endsWith(".apk") }?.forEach { apk ->
            val archivedApk = archiveDir.resolve("${apk.name.removeSuffix(".apk")}_${timestamp}.apk")
            Files.copy(apk.toPath(), archivedApk, StandardCopyOption.REPLACE_EXISTING)
            logger.lifecycle("Release APK 已归档: $archivedApk")
            }
        }
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation("de.sciss:jump3r:1.0.5")
    testImplementation(libs.libxposed.api)
    testImplementation("junit:junit:4.13.2")
}
