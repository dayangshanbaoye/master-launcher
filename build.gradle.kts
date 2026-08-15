plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    id("com.android.application") version "8.9.2" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

// 仓库放在 OneDrive 同步目录下时，build/ 目录高频重写会跟 OneDrive 的文件锁打架，偶发
// "Unable to delete directory ...test-results" 构建失败。只在本机显式设了这个环境变量时才
// 把构建输出挪到同步目录之外；不设就是原来的行为，不影响 CI 或其他人的机器。
System.getenv("GRADLE_BUILD_DIR_OVERRIDE")?.let { override ->
    allprojects {
        layout.buildDirectory.set(file("$override/${project.name}"))
    }
}
