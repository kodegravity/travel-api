package com.aitravel.companion.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

// Null and blank values are left to @NotBlank so each field reports one clear message.
public class CurrencyCodeValidator implements ConstraintValidator<ValidCurrencyCode, String> {

    private static final Set<String> ISO_CODES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || ISO_CODES.contains(value);
    }
}
