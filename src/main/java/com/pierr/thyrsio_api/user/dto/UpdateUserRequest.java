package com.pierr.thyrsio_api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @Size(min = 1, max = 100) String name,
    @Email @Size(min = 1, max = 254) String email
) {
}
