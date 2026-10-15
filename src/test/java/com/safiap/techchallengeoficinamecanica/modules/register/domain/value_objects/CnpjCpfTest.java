package com.safiap.techchallengeoficinamecanica.modules.register.domain.value_objects;

import com.safiap.techchallengeoficinamecanica.modules.shared.exceptions.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CnpjCpfTest {

    @Test
    @DisplayName("accepts a valid CPF and normalizes to digits only")
    void acceptsValidCpf() {
        CnpjCpf document = new CnpjCpf("111.444.777-35");
        assertThat(document.value()).isEqualTo("11144477735");
    }

    @Test
    @DisplayName("accepts a valid unformatted CPF")
    void acceptsUnformattedCpf() {
        assertThat(new CnpjCpf("52998224725").value()).isEqualTo("52998224725");
    }

    @Test
    @DisplayName("accepts a valid CNPJ and normalizes to digits only")
    void acceptsValidCnpj() {
        CnpjCpf document = new CnpjCpf("11.222.333/0001-81");
        assertThat(document.value()).isEqualTo("11222333000181");
    }

    @Test
    @DisplayName("rejects a null document")
    void rejectsNull() {
        assertThatThrownBy(() -> new CnpjCpf(null)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("rejects empty values and alphabetic characters")
    void rejectsEmptyAndAlphabeticValues() {
        assertThatThrownBy(() -> new CnpjCpf("")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new CnpjCpf("529.982.247-25x")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("rejects a CPF with an invalid check digit")
    void rejectsCpfWithInvalidCheckDigit() {
        assertThatThrownBy(() -> new CnpjCpf("12345678900")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("rejects CPFs with either incorrect check digit")
    void rejectsCpfWithIncorrectCheckDigits() {
        assertThatThrownBy(() -> new CnpjCpf("52998224735")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new CnpjCpf("52998224724")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("rejects a CNPJ with an invalid check digit")
    void rejectsCnpjWithInvalidCheckDigit() {
        assertThatThrownBy(() -> new CnpjCpf("11222333000100")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("rejects a sequence of repeated digits")
    void rejectsRepeatedDigits() {
        assertThatThrownBy(() -> new CnpjCpf("00000000000")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new CnpjCpf("11111111111111")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("rejects a document with an invalid digit count")
    void rejectsWrongLength() {
        assertThatThrownBy(() -> new CnpjCpf("123")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new CnpjCpf("123456789012")).isInstanceOf(DomainException.class);
    }
}
