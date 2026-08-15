// repo.maven.apache.org 在国内网络下 TLS 握手不稳定（本机验证：PowerShell/.NET 直连 200，JVM 客户端握手中断）；
// 阿里云镜像放在前面优先命中，官方源留作兜底，不影响其他网络环境下的构建。
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        google()
        mavenCentral()
    }
}

rootProject.name = "master-launcher"
include(":engine", ":app")
