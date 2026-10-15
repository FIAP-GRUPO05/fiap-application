DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM customers
        WHERE cnpj_cpf !~ '^[0-9./ -]+$'
           OR length(regexp_replace(cnpj_cpf, '[^0-9]', '', 'g')) NOT IN (11, 14)
    ) THEN
        RAISE EXCEPTION 'Invalid customer documents must be resolved before normalization';
    END IF;

    IF EXISTS (
        SELECT regexp_replace(cnpj_cpf, '[^0-9]', '', 'g')
        FROM customers
        GROUP BY regexp_replace(cnpj_cpf, '[^0-9]', '', 'g')
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate normalized customer documents must be resolved before adding document uniqueness';
    END IF;
END
$$;

UPDATE customers
SET cnpj_cpf = regexp_replace(cnpj_cpf, '[^0-9]', '', 'g')
WHERE cnpj_cpf ~ '[^0-9]';

ALTER TABLE customers
    ADD COLUMN status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

CREATE UNIQUE INDEX uk_customers_cnpj_cpf ON customers (cnpj_cpf);
