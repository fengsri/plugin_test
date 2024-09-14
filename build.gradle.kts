// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "org.intellij.sdk"
version = "2.0.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

// See https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
//  version.set("2021.3") // 与 GoLand 223.x 兼容的版本
//  type.set("GO")
//  plugins.set(listOf("org.jetbrains.plugins.go"))

    type.set("GO")
    plugins.set(listOf("org.jetbrains.plugins.go"))
    localPath.set("/Applications/GoLand.app/Contents")  // Mac版设置本地Goland的安装目录
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        version.set("${project.version}")
        sinceBuild.set("213")
        untilBuild.set("242.*")
    }
}
