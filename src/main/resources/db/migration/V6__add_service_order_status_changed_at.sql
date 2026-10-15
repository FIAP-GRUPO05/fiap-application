-- Instante em que a OS entrou no status atual, usado para medir o tempo em cada status.
-- OSs existentes ficam nulas: a primeira transição delas sai sem duração.
ALTER TABLE service_orders
    ADD COLUMN status_changed_at timestamp(6) without time zone;
