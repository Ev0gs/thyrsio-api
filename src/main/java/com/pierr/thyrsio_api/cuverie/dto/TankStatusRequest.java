package com.pierr.thyrsio_api.cuverie.dto;

import com.pierr.thyrsio_api.cuverie.TankStatus;
import jakarta.validation.constraints.NotNull;

public record TankStatusRequest(@NotNull TankStatus status) {
}
