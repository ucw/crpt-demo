package org.example.crpt;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Document(
        DocumentDescription description,
        String docId,
        String docStatus,
        String docType,
        @JsonProperty("importRequest") boolean importRequest,
        String ownerInn,
        String participantInn,
        String producerInn,
        LocalDate productionDate,
        String productionType,
        List<Product> products,
        LocalDate regDate,
        String regNumber
) {
}
