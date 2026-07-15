package com.alaeldin.bank_simulator_service.controller;

import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for triggering Conductor workflows
 * Provides convenient endpoints for demo and testing
 */
@RestController
@RequestMapping("/api/conductor")
@Slf4j
@RequiredArgsConstructor
public class ConductorWorkflowController {

    private final WorkflowClient workflowClient;

    /**
     * Triggers a P2P transfer workflow
     *
     * @param request Transfer request
     * @return Workflow ID
     */
    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> triggerP2PTransfer(@RequestBody TransferRequest request) {
        log.info("Triggering P2P transfer workflow: {} -> {}, amount: {}",
            request.getSourceAccountId(), request.getDestinationAccountId(), request.getAmount());

        try {
            // Prepare workflow input
            Map<String, Object> input = new HashMap<>();
            input.put("sourceAccountId", request.getSourceAccountId());
            input.put("destinationAccountId", request.getDestinationAccountId());
            input.put("amount", request.getAmount());
            input.put("currency", request.getCurrency() != null ? request.getCurrency() : "USD");

            // Create workflow start request
            StartWorkflowRequest workflowRequest = new StartWorkflowRequest();
            workflowRequest.setName("p2p_transfer_workflow");
            workflowRequest.setVersion(1);
            workflowRequest.setInput(input);

            // Start workflow
            String workflowId = workflowClient.startWorkflow(workflowRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("workflowId", workflowId);
            response.put("message", "P2P transfer workflow started successfully");
            response.put("conductorUI", "http://localhost:5001/execution/" + workflowId);

            log.info("P2P transfer workflow started: {}", workflowId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to start P2P transfer workflow", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Triggers a P2P transfer with fraud checks
     *
     * @param request Transfer request
     * @return Workflow ID
     */
    @PostMapping("/transfer-with-fraud-check")
    public ResponseEntity<Map<String, Object>> triggerP2PTransferWithFraudCheck(
            @RequestBody TransferRequest request) {

        log.info("Triggering P2P transfer with fraud check workflow: {} -> {}, amount: {}",
            request.getSourceAccountId(), request.getDestinationAccountId(), request.getAmount());

        try {
            Map<String, Object> input = new HashMap<>();
            input.put("sourceAccountId", request.getSourceAccountId());
            input.put("destinationAccountId", request.getDestinationAccountId());
            input.put("amount", request.getAmount());
            input.put("currency", request.getCurrency() != null ? request.getCurrency() : "USD");

            StartWorkflowRequest workflowRequest = new StartWorkflowRequest();
            workflowRequest.setName("p2p_transfer_with_fraud_check_workflow");
            workflowRequest.setVersion(1);
            workflowRequest.setInput(input);

            String workflowId = workflowClient.startWorkflow(workflowRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("workflowId", workflowId);
            response.put("message", "P2P transfer with fraud check workflow started successfully");
            response.put("conductorUI", "http://localhost:5001/execution/" + workflowId);

            log.info("P2P transfer with fraud check workflow started: {}", workflowId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to start P2P transfer with fraud check workflow", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Gets workflow execution status
     *
     * @param workflowId Workflow ID
     * @return Workflow status
     */
    @GetMapping("/workflow/{workflowId}")
    public ResponseEntity<Map<String, Object>> getWorkflowStatus(@PathVariable String workflowId) {
        log.info("Getting workflow status: {}", workflowId);

        try {
            var workflow = workflowClient.getWorkflow(workflowId, true);

            Map<String, Object> response = new HashMap<>();
            response.put("workflowId", workflow.getWorkflowId());
            response.put("workflowName", workflow.getWorkflowName());
            response.put("status", workflow.getStatus().toString());
            response.put("startTime", workflow.getStartTime());
            response.put("endTime", workflow.getEndTime());
            response.put("input", workflow.getInput());
            response.put("output", workflow.getOutput());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get workflow status", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Transfer Request DTO
     */
    @lombok.Data
    public static class TransferRequest {
        private String sourceAccountId;
        private String destinationAccountId;
        private Double amount;
        private String currency;
    }
}

