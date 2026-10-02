plugins{
    `java-gradle-plugin`
    `maven-publish`
}

val mindustryVersion = providers.gradleProperty("mindustryVersion").get()

fun commonPom(pom: MavenPom){
    pom.apply{
        url = "https://github.com/GglLfr/MindustryClient"
        inceptionYear = "2026"

        licenses{
            license{
                name = "GPL-3.0-or-later"
                url = "https://www.gnu.org/licenses/gpl-3.0.en.html"
                distribution = "repo"
            }
        }

        issueManagement{
            system = "GitHub Issue Tracker"
            url = "https://github.com/GglLfr/MindustryClient/issues"
        }
    }
}

allprojects{
    apply(plugin = "java")

    sourceSets["main"].java.setSrcDirs(listOf(layout.projectDirectory.dir("src")))
    repositories{
        google()
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/releases/")

        ivy{
            url = uri("https://github.com")
            patternLayout{
                artifact("Anuken/Mindustry/releases/download/[revision]/dependencies.jar")
                metadataSources{artifact()}
            }
            content{
                includeVersion("Anuken", "Mindustry", mindustryVersion)
            }
        }
    }

    java{
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach{
        options.apply{
            isIncremental = true
            isFork = false
            encoding = "UTF-8"

            compilerArgs.add("-Xlint:-options")
            compilerArgs.add("-implicit:none")
            compilerArgs.addAll(providers.gradleProperty("org.gradle.jvmargs").get()
                .split(Regex("\\s+"))
                .filter{it.startsWith("--add-opens")}
                .map{"--add-exports=${it.substring("--add-opens=".length)}"}
            )
        }

        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    tasks.withType<Javadoc>().configureEach{
        options{
            encoding = "UTF-8"

            val exports = providers.gradleProperty("org.gradle.jvmargs").get()
                .split(Regex("\\s+"))
                .filter{it.startsWith("--add-opens")}
                .map{"--add-exports ${it.substring("--add-opens=".length)}"}
                .reduce{accum, arg -> "$accum $arg"}

            val opts = File(temporaryDir, "exports.options")
            opts.writeText("-Xdoclint:none $exports", Charsets.UTF_8)
            optionFiles(opts)
        }
    }
}

project(":info"){
    apply(plugin = "maven-publish")
    group = "com.github.GglLfr.MindustryClient"

    publishing.publications.register<MavenPublication>("maven"){
        from(components["java"])
        pom{
            name = "Mindustry Client Detector"
            description = "Detects Mindustry client installation in your system."
            commonPom(this)
        }
    }
}

project(":"){
    apply(plugin = "java-gradle-plugin")
    group = "com.github.GglLfr"

    lateinit var plugin: Provider<PluginDeclaration>
    gradlePlugin{
        isAutomatedPublishing = false

        plugin = plugins.register("mindustryClient"){
            id = "com.github.GglLfr.MindustryClient"
            displayName = "MindustryClient"
            implementationClass = "mindustry.client.MindustryClientPlugin"
        }
    }

    publishing.publications{
        fun applyPom(pom: MavenPom){
            pom.apply{
                name = plugin.map{it.displayName}
                description = plugin.map{it.description}
                commonPom(this)
            }
        }

        val maven = register<MavenPublication>("maven"){
            from(components["java"])
            pom{applyPom(this)}
        }

        val mavenGroupId = maven.map{it.groupId}
        val mavenArtifactId = maven.map{it.artifactId}
        val mavenVersion = maven.map{it.version}

        register<MavenPublication>("plugin"){
            groupId = plugin.map{it.id}.get()
            artifactId = "$groupId.gradle.plugin"

            pom{
                packaging = "pom"

                applyPom(this)
                withXml{
                    val root = asElement()
                    val doc = root.ownerDocument
                    val dependencies = root.appendChild(doc.createElement("dependencies"))
                    val dependency = dependencies.appendChild(doc.createElement("dependency"))

                    val groupId = dependency.appendChild(doc.createElement("groupId"))
                    groupId.textContent = mavenGroupId.get()

                    val artifactId = dependency.appendChild(doc.createElement("artifactId"))
                    artifactId.textContent = mavenArtifactId.get()

                    val version = dependency.appendChild(doc.createElement("version"))
                    version.textContent = mavenVersion.get()
                }
            }
        }
    }

    dependencies{
        implementation(project(":info"))
        implementation("Anuken:Mindustry:${mindustryVersion}")
    }
}
