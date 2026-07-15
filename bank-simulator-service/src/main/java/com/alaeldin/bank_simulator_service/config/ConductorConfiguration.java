package com.alaeldin.bank_simulator_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Conductor Configuration
 * Initializes Conductor clients and registers workflows/tasks
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class ConductorConfiguration {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    @Value("${conductor.server.url}")
    private String conductorServerUrl;

    @Bean
    public TaskClient taskClient() {
        log.info("Creating Conductor TaskClient for URL: {}", conductorServerUrl);
        return new TaskClient();
    }

    @Bean
    public WorkflowClient workflowClient() {
        log.info("Creating Conductor WorkflowClient for URL: {}", conductorServerUrl);
        return new WorkflowClient();
    }

    @Bean
    public MetadataClient metadataClient() {
        log.info("Creating Conductor MetadataClient for URL: {}", conductorServerUrl);
        return new MetadataClient();
    }

    /**
     * Registers workflows and task definitions with Conductor on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeConductorWorkflows() {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("Initializing Conductor Workflows and Task Definitions");
        log.info("═══════════════════════════════════════════════════════════");

        try {
            // Register task definitions
            registerTaskDefinitions();

            // Register workflow definitions
            registerWorkflowDefinitions();

            log.info("═══════════════════════════════════════════════════════════");
            log.info("Conductor initialization completed successfully!");
            log.info("Access Conductor UI at: http://localhost:5001");
            log.info("═══════════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("Failed to initialize Conductor workflows", e);
            log.warn("Continuing application startup despite Conductor initialization failure");
        }
    }

    /**
     * Registers task definitions from JSON file
     */
    private void registerTaskDefinitions() {
        try {
            log.info("Registering task definitions...");
            Resource resource = resourceLoader.getResource("classpath:workflows/task-definitions.json");

            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    List<TaskDef> taskDefs = objectMapper.readValue(is,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, TaskDef.class));

                    MetadataClient client = metadataClient();
                    for (TaskDef taskDef : taskDefs) {
                        try {
                            client.registerTaskDef(taskDef);
                            log.info("  ✓ Registered task: {}", taskDef.getName());
                        } catch (Exception e) {
                            log.warn("  ⚠ Failed to register task {} (may already exist): {}",
                                taskDef.getName(), e.getMessage());
                        }
                    }
                    log.info("Task definitions registration completed. Total: {}", taskDefs.size());
                } catch (IOException e) {
                    log.error("Failed to read task definitions file", e);
                }
            } else {
                log.warn("Task definitions file not found at: classpath:workflows/task-definitions.json");
            }

        } catch (Exception e) {
            log.error("Failed to register task definitions", e);
        }
    }

    /**
     * Registers workflow definitions from JSON files
     */
    private void registerWorkflowDefinitions() {
        try {
            log.info("Registering workflow definitions...");

            // Register workflows
            registerWorkflow("classpath:workflows/p2p-transfer-workflow.json");
            registerWorkflow("classpath:workflows/p2p-transfer-compensation-workflow.json");
            registerWorkflow("classpath:workflows/p2p-transfer-fraud-check-workflow.json");

            log.info("Workflow definitions registration completed");

        } catch (Exception e) {
            log.error("Failed to register workflow definitions", e);
        }
    }

    /**
     * Registers a single workflow from JSON file
     */
    private void registerWorkflow(String resourcePath) {
        try {
            Resource resource = resourceLoader.getResource(resourcePath);

            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    WorkflowDef workflowDef = objectMapper.readValue(is, WorkflowDef.class);

                    MetadataClient client = metadataClient();
                    try {
                        client.registerWorkflowDef(workflowDef);
                        log.info("  ✓ Registered workflow: {} (v{})",
                            workflowDef.getName(), workflowDef.getVersion());
                    } catch (Exception e) {
                        log.warn("  ⚠ Failed to register workflow {} (may already exist): {}",
                            workflowDef.getName(), e.getMessage());
                    }
                }
            } else {
                log.warn("Workflow file not found: {}", resourcePath);
            }

        } catch (Exception e) {
            log.error("Failed to register workflow from: {}", resourcePath, e);
        }
    }
}

