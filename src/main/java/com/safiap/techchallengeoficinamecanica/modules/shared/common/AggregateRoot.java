package com.safiap.techchallengeoficinamecanica.modules.shared.common;

import java.util.ArrayList;
import java.util.List;

public abstract class AggregateRoot {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected void registerDomainEvent(DomainEvent domainEvent) {
        this.domainEvents.add(domainEvent);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }
}
