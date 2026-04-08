package com.loyalty.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request payload to earn loyalty points")
public class EarnPointsRequest {

    @NotBlank(message = "userId must not be blank")
    @Size(max = 64, message = "userId must be at most 64 characters")
    @Schema(description = "Unique identifier of the wallet owner", example = "user-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    @Min(value = 1, message = "points must be at least 1")
    @Schema(description = "Number of points to earn", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private long points;

    @Size(max = 255, message = "description must be at most 255 characters")
    @Schema(description = "Human-readable reason for earning points", example = "Purchase #INV-20240401")
    private String description;
}
