package es.uclm.esibuy.configuration.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GeneralConfigurationDTO(
        @NotNull(message = "El IVA es obligatorio")
        @DecimalMin(value = "0.00", message = "El IVA no puede ser negativo")
        @DecimalMax(value = "100.00", message = "El IVA no puede superar el 100 %")
        @Digits(integer = 3, fraction = 2, message = "El IVA admite hasta dos decimales")
        BigDecimal ivaPorcentaje,

        @NotNull(message = "Los gastos de envío son obligatorios")
        @DecimalMin(value = "0.00", message = "Los gastos de envío no pueden ser negativos")
        @Digits(integer = 8, fraction = 2, message = "Los gastos de envío admiten hasta dos decimales")
        BigDecimal gastosEnvio,

        @NotNull(message = "El importe para el envío gratuito es obligatorio")
        @DecimalMin(value = "0.00", message = "El importe para el envío gratuito no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El importe admite hasta dos decimales")
        BigDecimal envioGratuitoDesde,

        @NotNull(message = "La opción de registro es obligatoria")
        Boolean registroHabilitado,

        // Opcional: quien no lo envía deja el valor que ya había
        @Min(value = 1, message = "El mínimo de valoraciones debe ser al menos 1")
        @Max(value = 1000, message = "El mínimo de valoraciones no puede superar 1000")
        Integer minimoValoracionesRanking) {
}
