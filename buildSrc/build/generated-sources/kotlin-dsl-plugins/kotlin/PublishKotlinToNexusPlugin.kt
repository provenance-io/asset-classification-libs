/**
 * Precompiled [publish-kotlin-to-nexus.gradle.kts][Publish_kotlin_to_nexus_gradle] script plugin.
 *
 * @see Publish_kotlin_to_nexus_gradle
 */
class PublishKotlinToNexusPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Publish_kotlin_to_nexus_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
