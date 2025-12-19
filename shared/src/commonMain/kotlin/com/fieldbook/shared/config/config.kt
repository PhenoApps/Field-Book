package com.fieldbook.shared.config

import io.kamel.core.config.KamelConfig
import io.kamel.core.config.fileFetcher
import io.kamel.core.config.httpFetcher
import io.kamel.core.config.takeFrom
import io.kamel.image.config.Default

val customKamelConfig = KamelConfig {
    takeFrom(KamelConfig.Default)
    httpFetcher()
    fileFetcher()
}
