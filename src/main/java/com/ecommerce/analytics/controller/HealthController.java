package com.ecommerce.analytics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(
        name = "Health",
        description = "API health and availability endpoints"
)
public class HealthController {

    @GetMapping("/api/v1/health")
    @Operation(
            summary = "Check API health",
            description = "Checks whether the Ecommerce Analytics API is running."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "API is running successfully"
            )
    })
    public String health() {
        return "Ecommerce Analytics API is running";
    }
}