package com.stockit.backend.feature.inventorysync.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.inventory-sync.schedule", name = "enabled", havingValue = "true")
public class InventorySyncSchedulingConfiguration {
}
