package com.rallit.global.config;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles(profiles = "temp", inheritProfiles = false)
class TempDocsIntegrationTest extends LocalDocsIntegrationTest {}
