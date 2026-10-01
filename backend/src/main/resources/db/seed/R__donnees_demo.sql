-- Données de démonstration, chargées uniquement avec les profils dev et test.
INSERT INTO organization (id, name, legal_name, country, status, created_at, updated_at)
VALUES ('0190f2a0-0000-7000-8000-000000000001', 'Niirma Hôtels', 'Niirma Hôtels SARL', 'SN', 'ACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO hotel (id, organization_id, code, name, legal_name, address, phone, email, legal_ids,
                   currency_code, timezone, language, business_date, status, created_at, updated_at)
VALUES ('0190f2a0-0000-7000-8000-000000000101', '0190f2a0-0000-7000-8000-000000000001', 'DKR01',
        'Hôtel Démo Dakar', 'Niirma Hôtels SARL', 'Route de la Corniche Ouest, Dakar', '+221 33 000 00 00',
        'reception@demo.niirma.sn', '{"RCCM": "SN-DKR-0000-B-00000", "NINEA": "000000000"}',
        'XOF', 'Africa/Dakar', 'fr', (now() AT TIME ZONE 'Africa/Dakar')::date, 'ACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO hotel (id, organization_id, code, name, legal_name, address, phone, email, legal_ids,
                   currency_code, timezone, language, business_date, status, created_at, updated_at)
VALUES ('0190f2a0-0000-7000-8000-000000000102', '0190f2a0-0000-7000-8000-000000000001', 'SLY01',
        'Hôtel Démo Saly', 'Niirma Hôtels SARL', 'Saly Portudal, Mbour', '+221 33 000 00 01',
        'reception.saly@demo.niirma.sn', '{"RCCM": "SN-MBR-0000-B-00000", "NINEA": "000000001"}',
        'XOF', 'Africa/Dakar', 'fr', (now() AT TIME ZONE 'Africa/Dakar')::date, 'ACTIVE', now() + interval '1 second', now())
ON CONFLICT (id) DO NOTHING;
