package com.pierr.thyrsio_api.gs1.dto;

import jakarta.validation.constraints.Size;

/** Le libellé et les notes : ce qui reste modifiable d'un gencode. */
public record DescribeGtinRequest(@Size(max = 200) String description, @Size(max = 2000) String notes) {
}
