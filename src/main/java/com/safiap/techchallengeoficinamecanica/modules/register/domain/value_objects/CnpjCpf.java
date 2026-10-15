package com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects;

import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.DomainException;

public record CnpjCpf(String value) {

    public CnpjCpf {
        value = normalizeAndValidate(value);
    }

    private static String normalizeAndValidate(String raw) {
        if (raw == null) {
            throw new DomainException("Invalid document: must not be null");
        }
        if (!raw.matches("[0-9./\\-\\s]+")) {
            throw new DomainException("Invalid document: unsupported characters");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 11) {
            if (!isValidCpf(digits)) {
                throw new DomainException("Invalid CPF");
            }
        } else if (digits.length() == 14) {
            if (!isValidCnpj(digits)) {
                throw new DomainException("Invalid CNPJ");
            }
        } else {
            throw new DomainException("Invalid document: must be a valid CPF or CNPJ");
        }
        return digits;
    }

    private static boolean isValidCpf(String cpf) {
        if (allDigitsEqual(cpf)) {
            return false;
        }
        int firstCheck = checkDigit(cpf, 9, 10);
        int secondCheck = checkDigit(cpf, 10, 11);
        return firstCheck == digitAt(cpf, 9) && secondCheck == digitAt(cpf, 10);
    }

    private static boolean isValidCnpj(String cnpj) {
        if (allDigitsEqual(cnpj)) {
            return false;
        }
        int[] firstWeights = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] secondWeights = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int firstCheck = checkDigit(cnpj, firstWeights);
        int secondCheck = checkDigit(cnpj, secondWeights);
        return firstCheck == digitAt(cnpj, 12) && secondCheck == digitAt(cnpj, 13);
    }

    private static int checkDigit(String value, int length, int startWeight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digitAt(value, i) * (startWeight - i);
        }
        return remainderToDigit(sum);
    }

    private static int checkDigit(String value, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += digitAt(value, i) * weights[i];
        }
        return remainderToDigit(sum);
    }

    private static int remainderToDigit(int sum) {
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static int digitAt(String value, int index) {
        return value.charAt(index) - '0';
    }

    private static boolean allDigitsEqual(String value) {
        return value.chars().distinct().count() == 1;
    }
}
