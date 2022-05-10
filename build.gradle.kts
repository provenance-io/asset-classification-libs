configurations.all {
    exclude(group = "log4j") // ancient versions of log4j use this group
}

plugins {
    id("publish-kotlin-library")
}
