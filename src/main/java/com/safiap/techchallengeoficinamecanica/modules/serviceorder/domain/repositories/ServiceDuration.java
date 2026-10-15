package com.safiap.techchallengeoficinamecanica.modules.serviceorder.domain.repositories;

import java.util.UUID;

/**
 * Quanto tempo um servico levou em uma OS. E parte do contrato do repositorio, nao um DTO de
 * entrada/saida da API — por isso mora no dominio: a camada mais interna nao pode depender da
 * apresentacao para declarar o que sabe devolver.
 */
public record ServiceDuration(UUID serviceId, long durationSeconds) {
}
