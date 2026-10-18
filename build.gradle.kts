
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.test {
    useJUnitPlatform()
}

configurations.named("testCompileOnly") {
    extendsFrom(configurations.getByName("compileOnly"))
}
