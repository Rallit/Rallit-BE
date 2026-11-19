package com.rallit.global.config;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles(profiles = "prod", inheritProfiles = false)
class ProdDocsIntegrationTest extends LocalDocsIntegrationTest {}
