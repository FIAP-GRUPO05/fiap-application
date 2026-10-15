package com.safiap.techchallengeoficinamecanica.modules.serviceorder.presentation.dto;

import com.safiap.techchallengeoficinamecanica.modules.serviceorder.application.commands.BudgetItemInput;

import java.util.List;

public final class BudgetItemMapper {

    private BudgetItemMapper() {}

    public static List<BudgetItemInput> toInputs(List<BudgetItemDTO> items) {
        if (items == null) return List.of();
        return items.stream()
                .map(i -> new BudgetItemInput(i.type(), i.itemId(), i.description(), i.quantity()))
                .toList();
    }
}
