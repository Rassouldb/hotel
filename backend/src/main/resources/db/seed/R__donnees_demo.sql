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

-- Taxes de démonstration (valeurs provisoires, à confirmer avec le comptable) : TVA 18 %,
-- taxe de séjour 1 000 XOF par adulte et par nuit, moins de 12 ans exonérés.
INSERT INTO tax (id, hotel_id, code, name, type, vat, applies_to, exempt_children_under_age, exempt_residents, active,
                 created_at, updated_at)
VALUES ('0190f2a0-0000-7000-8000-000000000201', '0190f2a0-0000-7000-8000-000000000101', 'TVA', 'TVA', 'PERCENTAGE', true,
        'ACCOMMODATION,BEVERAGE,FOOD,OTHER,SERVICE', NULL, false, true, now(), now()),
       ('0190f2a0-0000-7000-8000-000000000202', '0190f2a0-0000-7000-8000-000000000101', 'TS', 'Taxe de séjour',
        'FIXED_PER_PERSON_NIGHT', false, 'ACCOMMODATION', 12, false, true, now(), now()),
       ('0190f2a0-0000-7000-8000-000000000203', '0190f2a0-0000-7000-8000-000000000102', 'TVA', 'TVA', 'PERCENTAGE', true,
        'ACCOMMODATION,BEVERAGE,FOOD,OTHER,SERVICE', NULL, false, true, now(), now()),
       ('0190f2a0-0000-7000-8000-000000000204', '0190f2a0-0000-7000-8000-000000000102', 'TS', 'Taxe de séjour',
        'FIXED_PER_PERSON_NIGHT', false, 'ACCOMMODATION', 12, false, true, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO tax_rate_period (tax_id, valid_from, valid_to, rate, amount)
VALUES ('0190f2a0-0000-7000-8000-000000000201', '2026-01-01', NULL, 18, NULL),
       ('0190f2a0-0000-7000-8000-000000000202', '2026-01-01', NULL, NULL, 1000),
       ('0190f2a0-0000-7000-8000-000000000203', '2026-01-01', NULL, 18, NULL),
       ('0190f2a0-0000-7000-8000-000000000204', '2026-01-01', NULL, NULL, 1000)
ON CONFLICT DO NOTHING;

-- Types de chambres et chambres de démonstration.
INSERT INTO room_type (id, hotel_id, code, name, description, base_occupancy, max_occupancy, max_adults, max_children,
                       base_price, sort_order, active, created_at, updated_at)
VALUES ('0190f2a0-0000-7000-8000-000000000301', '0190f2a0-0000-7000-8000-000000000101', 'STD', 'Standard',
        'Chambre double climatisée, lit queen size.', 2, 3, 2, 1, 35000, 0, true, now(), now()),
       ('0190f2a0-0000-7000-8000-000000000302', '0190f2a0-0000-7000-8000-000000000101', 'SUP', 'Supérieure',
        'Chambre spacieuse avec coin salon.', 2, 3, 3, 1, 45000, 1, true, now(), now()),
       ('0190f2a0-0000-7000-8000-000000000303', '0190f2a0-0000-7000-8000-000000000101', 'DLX', 'Deluxe',
        'Vue sur l''océan, balcon.', 2, 3, 3, 1, 50000, 2, true, now(), now()),
       ('0190f2a0-0000-7000-8000-000000000304', '0190f2a0-0000-7000-8000-000000000101', 'STE', 'Suite',
        'Suite avec chambre séparée et salon.', 2, 5, 3, 2, 85000, 3, true, now(), now()),
       ('0190f2a0-0000-7000-8000-000000000311', '0190f2a0-0000-7000-8000-000000000102', 'STD', 'Standard',
        'Chambre double côté jardin.', 2, 3, 2, 1, 30000, 0, true, now(), now()),
       ('0190f2a0-0000-7000-8000-000000000312', '0190f2a0-0000-7000-8000-000000000102', 'BGL', 'Bungalow',
        'Bungalow familial face à la mer.', 2, 5, 4, 2, 65000, 1, true, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO room (id, hotel_id, room_type_id, number, floor, created_at, updated_at)
SELECT gen_random_uuid(), '0190f2a0-0000-7000-8000-000000000101', t.type_id, n::text, n / 100, now(), now()
FROM (VALUES ('0190f2a0-0000-7000-8000-000000000301'::uuid, 101, 110),
             ('0190f2a0-0000-7000-8000-000000000302'::uuid, 201, 208),
             ('0190f2a0-0000-7000-8000-000000000303'::uuid, 301, 306),
             ('0190f2a0-0000-7000-8000-000000000304'::uuid, 401, 402)) AS t(type_id, first_number, last_number),
     generate_series(t.first_number, t.last_number) AS n
ON CONFLICT DO NOTHING;

INSERT INTO room (id, hotel_id, room_type_id, number, floor, created_at, updated_at)
SELECT gen_random_uuid(), '0190f2a0-0000-7000-8000-000000000102', t.type_id, n::text, n / 100, now(), now()
FROM (VALUES ('0190f2a0-0000-7000-8000-000000000311'::uuid, 101, 108),
             ('0190f2a0-0000-7000-8000-000000000312'::uuid, 201, 204)) AS t(type_id, first_number, last_number),
     generate_series(t.first_number, t.last_number) AS n
ON CONFLICT DO NOTHING;
