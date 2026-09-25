pluginManagement {
    repositories {
        maven { setUrl("https://mirrors.huaweicloud.com/repository/maven") }
        maven { setUrl("https://mirrors.cloud.tencent.com/nexus/repository/maven-public") }
        maven { setUrl("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { setUrl("https://maven.aliyun.com/repository/google") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
        maven { setUrl("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { setUrl("https://mirrors.huaweicloud.com/repository/maven") }
        maven { setUrl("https://mirrors.cloud.tencent.com/nexus/repository/maven-public") }
        maven { setUrl("https://maven.aliyun.com/repository/google") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
        maven { setUrl("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
    }
}

rootProject.name = "VisionWatchApp"
include(":app")
