import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.Properties
import java.net.URI
import java.util.zip.ZipFile

plugins { java }
group = "jp.feato"
val repositoryRoot = projectDir.parentFile
val compatibility = Properties().apply { repositoryRoot.resolve("bridge.properties").inputStream().use { load(it) } }
version = compatibility.getProperty("bridge.version")
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}
val valhallaJar = layout.buildDirectory.file("dependencies/ValhallaMMO_1.10.3.jar")
val valhallaSha512 = "e04a1e8f39e009e141fe8f07dd1eea85ad06c5850e63e5518618c316e3b4822179ab5bab571f1a5fc6ccf35c17de4baaae07c86e7fd9b374b6eb1f6cedd528a6"
val fetchValhalla by tasks.registering {
    outputs.file(valhallaJar)
    // Verify cached files as well; never silently compile against a replaced API.
    outputs.upToDateWhen { false }
    doLast {
        val destination = valhallaJar.get().asFile
        if (!destination.exists()) {
            destination.parentFile.mkdirs()
            val temporary = destination.resolveSibling(destination.name + ".part")
            try {
                URI("https://cdn.modrinth.com/data/rxrgsoud/versions/GkeSDJSq/ValhallaMMO_1.10.3.jar")
                    .toURL().openConnection().apply { connectTimeout = 15000; readTimeout = 60000 }
                    .getInputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
                temporary.copyTo(destination, overwrite = true)
            } finally { temporary.delete() }
        }
        val actual = MessageDigest.getInstance("SHA-512").digest(destination.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == valhallaSha512) { "ValhallaMMO 1.10.3 checksum mismatch; remove the cached JAR and retry" }
    }
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.126-stable")
    compileOnly(files(valhallaJar))
    testImplementation("io.papermc.paper:paper-api:26.2.build.126-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.compileJava { dependsOn(fetchValhalla) }
tasks.processResources {
    inputs.file(repositoryRoot.resolve("bridge.properties"))
    from(repositoryRoot.resolve("bridge.properties"))
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
tasks.test { useJUnitPlatform() }
tasks.jar { archiveFileName.set("feato-gun-valhalla-bridge-plugin-${project.version}.jar") }
val verifyDatapack by tasks.registering {
    inputs.dir(repositoryRoot.resolve("datapack"))
    inputs.file(repositoryRoot.resolve("bridge.properties"))
    doLast {
        val root = repositoryRoot.resolve("datapack")
        check(root.resolve("pack.mcmeta").isFile)
        root.walkTopDown().filter { it.isFile && (it.extension == "json" || it.name == "pack.mcmeta") }
            .forEach { JsonSlurper().parse(it) }
        for (name in listOf("load", "tick")) {
            val function = "feato_gun_valhalla:bridge/$name"
            check(root.resolve("data/feato_gun_valhalla/function/bridge/$name.mcfunction").isFile)
            val tag = JsonSlurper().parse(root.resolve("data/minecraft/tags/function/$name.json")) as Map<*, *>
            check(tag["values"] == listOf(function)) { "Missing $name function tag" }
        }
        val marker = root.resolve("data/feato_gun_valhalla/function/bridge/marker.mcfunction").readText()
        val keys = mapOf("release" to "bridge.release", "protocol" to "bridge.protocol", "gun_core" to "gun-core.marker", "modern_guns" to "modern-guns.marker", "valhalla" to "valhalla.marker")
        keys.forEach { (score, property) ->
            check(marker.lineSequence().count { it == "scoreboard players set #$score fgv_bridge ${compatibility.getProperty(property)}" } == 1) { "Datapack marker mismatch: $score" }
        }
        for ((tagName, bridgeFunction) in mapOf("custom_raycast" to "shot/raycast", "damage_type_entity" to "shot/entity_hit", "damage_type_block" to "shot/block_hit")) {
            val tag = JsonSlurper().parse(root.resolve("data/gbg/tags/function/$tagName.json")) as Map<*, *>
            check(tag["replace"] == false && tag["values"] == listOf("feato_gun_valhalla:$bridgeFunction"))
        }
        root.resolve("data/feato_gun_valhalla/function").walkTopDown().filter { it.extension == "mcfunction" }.forEach { file ->
            Regex("function (feato_gun_valhalla:[a-z0-9_/]+)").findAll(file.readText()).forEach { match ->
                check(root.resolve("data/feato_gun_valhalla/function/${match.groupValues[1].substringAfter(':')}.mcfunction").isFile) { "Missing Bridge function: $match" }
            }
        }
        check(!root.resolve("data/gbg/function").exists()) { "Gun Core function overrides are forbidden" }
    }
}
val verifyGunCoreContract by tasks.registering {
    // Verify fixed upstream bytes and generated files on every build; do not infer installed version.
    doLast {
        val dependencies = layout.buildDirectory.dir("dependencies").get().asFile.apply { mkdirs() }
        val artifacts = listOf(
            Triple("gun-core.zip", "https://cdn.modrinth.com/data/Ti7LgRXJ/versions/X7knYm9t/Gun%20Core%20-%20Data%20V1.0.15.zip", "43b4241835b15fddf0f85fa333ecef1bcac5e5598aefabbf3bd32c1506b6196f6db4e751b96a0026e49fc79670afbc44ec626d7b77b6f5cb2e692db8ec7e434c"),
            Triple("modern-guns.zip", "https://cdn.modrinth.com/data/ufgOyMFr/versions/bcKCNJp2/Modern%20Guns%20-%20Data%20V1.9.3.zip", "008e481cf01aea693e30a9458cde060cda76961f3766f90415bd2503025758d289a97fde2e5a411b4a238f3811ca79114a2a8c523893aea135c750f52dcb8155")
        )
        artifacts.forEach { (name, url, hash) ->
            val destination = dependencies.resolve(name)
            if (!destination.exists()) {
                val temporary = dependencies.resolve("$name.part")
                try {
                    URI(url).toURL().openConnection().apply { connectTimeout = 15000; readTimeout = 60000 }
                        .getInputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
                    temporary.copyTo(destination, overwrite = true)
                } finally { temporary.delete() }
            }
            check(MessageDigest.getInstance("SHA-512").digest(destination.readBytes()).joinToString("") { "%02x".format(it) } == hash) { "Fixed upstream checksum mismatch: $name" }
        }
        val process = ProcessBuilder("python3", repositoryRoot.resolve("scripts/generate_shot_contract.py").absolutePath,
            dependencies.resolve("gun-core.zip").absolutePath, dependencies.resolve("modern-guns.zip").absolutePath, "--check")
            .directory(repositoryRoot).inheritIO().start()
        check(process.waitFor() == 0) { "Stale generated weapon contract" }
        ZipFile(dependencies.resolve("gun-core.zip")).use { upstream ->
            repositoryRoot.resolve("datapack/data/feato_gun_valhalla/function").walkTopDown()
                .filter { it.extension == "mcfunction" }.forEach { file ->
                    Regex("function (gbg:[a-z0-9_/]+)").findAll(file.readText()).forEach { match ->
                        check(upstream.getEntry("data/gbg/function/${match.groupValues[1].substringAfter(':')}.mcfunction") != null) { "Missing fixed Gun Core function: $match" }
                    }
                }
        }
    }
}
verifyDatapack { dependsOn(verifyGunCoreContract) }
val datapackZip by tasks.registering(Zip::class) {
    dependsOn(verifyDatapack)
    from(repositoryRoot.resolve("datapack"))
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("feato-gun-valhalla-bridge-datapack-${project.version}.zip")
}
tasks.check { dependsOn(verifyDatapack) }
tasks.build { dependsOn(datapackZip) }
