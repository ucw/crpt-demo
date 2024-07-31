package org.example.crpt;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Product(
        String certificateDocument,
        LocalDate certificateDocumentDate,
        String certificateDocumentNumber,
        String ownerInn,
        String producerInn,
        LocalDate productionDate,
        String tnvedCode,
        String uitCode,
        String uituCode
) {
}
