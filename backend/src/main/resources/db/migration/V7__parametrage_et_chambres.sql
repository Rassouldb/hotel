-- Itération 2 : paramétrage de l'hôtel (§6), taxes (§8.3), moyens de paiement (§20.1), canaux et motifs,
-- types de chambres, chambres, statuts, historique et blocages (§9).

-- --- Hôtel : paramètres d'exploitation et logo -------------------------------------------------------------
ALTER TABLE hotel
    ADD COLUMN settings          jsonb NOT NULL DEFAULT '{}',
    ADD COLUMN logo              bytea,
    ADD COLUMN logo_content_type varchar(50);

-- --- Taxes ---------------------------------------------------------------------------------------------------
CREATE TABLE tax (
    id          uuid PRIMARY KEY,
    hotel_id    uuid         NOT NULL REFERENCES hotel (id),
    code        varchar(20)  NOT NULL,
    name        varchar(100) NOT NULL,
    type        varchar(30)  NOT NULL CHECK (type IN ('PERCENTAGE', 'FIXED_PER_PERSON_NIGHT', 'FIXED_PER_UNIT')),
    vat         boolean      NOT NULL DEFAULT false,
    applies_to  varchar(200) NOT NULL,
    exempt_children_under_age integer,
    exempt_residents          boolean NOT NULL DEFAULT false,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    created_by  uuid,
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_tax_code UNIQUE (hotel_id, code)
);

-- Historique des taux : une période [valid_from, valid_to[ par valeur ; la période ouverte a valid_to nul.
CREATE TABLE tax_rate_period (
    tax_id     uuid          NOT NULL REFERENCES tax (id) ON DELETE CASCADE,
    valid_from date          NOT NULL,
    valid_to   date,
    rate       numeric(7, 4),
    amount     numeric(18, 2),
    PRIMARY KEY (tax_id, valid_from),
    CONSTRAINT ck_tax_rate_period_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_tax_rate_period_value CHECK ((rate IS NULL) <> (amount IS NULL)),
    CONSTRAINT ck_tax_rate_period_positive CHECK (coalesce(rate, amount) >= 0),
    CONSTRAINT no_tax_period_overlap EXCLUDE USING gist (
        tax_id WITH =,
        daterange(valid_from, valid_to, '[)') WITH &&
    )
);

-- --- Moyens de paiement, canaux, motifs --------------------------------------------------------------------
CREATE TABLE payment_method (
    id                    uuid PRIMARY KEY,
    hotel_id              uuid         NOT NULL REFERENCES hotel (id),
    code                  varchar(30)  NOT NULL,
    name                  varchar(100) NOT NULL,
    type                  varchar(20)  NOT NULL CHECK (type IN ('CASH', 'CARD', 'TRANSFER', 'CHEQUE', 'MOBILE_MONEY', 'OTHER')),
    requires_cash_session boolean      NOT NULL,
    requires_reference    boolean      NOT NULL,
    active                boolean      NOT NULL DEFAULT true,
    sort_order            integer      NOT NULL DEFAULT 0,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    created_by            uuid,
    updated_by            uuid,
    version               bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_method_code UNIQUE (hotel_id, code),
    CONSTRAINT ck_cash_uses_cash_session CHECK (type <> 'CASH' OR requires_cash_session)
);

CREATE TABLE reservation_channel (
    id              uuid PRIMARY KEY,
    hotel_id        uuid         NOT NULL REFERENCES hotel (id),
    code            varchar(30)  NOT NULL,
    name            varchar(100) NOT NULL,
    commission_rate numeric(5, 2) CHECK (commission_rate BETWEEN 0 AND 100),
    active          boolean      NOT NULL DEFAULT true,
    sort_order      integer      NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_reservation_channel_code UNIQUE (hotel_id, code)
);

CREATE TABLE reason_code (
    id         uuid PRIMARY KEY,
    hotel_id   uuid         NOT NULL REFERENCES hotel (id),
    domain     varchar(30)  NOT NULL CHECK (domain IN ('CANCELLATION', 'DISCOUNT', 'ROOM_CHANGE', 'ROOM_BLOCK',
                                                       'ADJUSTMENT', 'HOUSEKEEPING')),
    code       varchar(30)  NOT NULL,
    label      varchar(150) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    sort_order integer      NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    created_by uuid,
    updated_by uuid,
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_reason_code UNIQUE (hotel_id, domain, code)
);

-- --- Chambres --------------------------------------------------------------------------------------------------
CREATE TABLE amenity (
    id         uuid PRIMARY KEY,
    hotel_id   uuid         NOT NULL REFERENCES hotel (id),
    code       varchar(30)  NOT NULL,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    created_by uuid,
    updated_by uuid,
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_amenity_code UNIQUE (hotel_id, code)
);

CREATE TABLE room_type (
    id             uuid PRIMARY KEY,
    hotel_id       uuid           NOT NULL REFERENCES hotel (id),
    code           varchar(10)    NOT NULL,
    name           varchar(100)   NOT NULL,
    description    varchar(1000),
    base_occupancy integer        NOT NULL CHECK (base_occupancy >= 1),
    max_occupancy  integer        NOT NULL,
    max_adults     integer        NOT NULL CHECK (max_adults >= 1),
    max_children   integer        NOT NULL CHECK (max_children >= 0),
    base_price     numeric(18, 2) NOT NULL CHECK (base_price >= 0),
    sort_order     integer        NOT NULL DEFAULT 0,
    active         boolean        NOT NULL DEFAULT true,
    created_at     timestamptz    NOT NULL,
    updated_at     timestamptz    NOT NULL,
    created_by     uuid,
    updated_by     uuid,
    version        bigint         NOT NULL DEFAULT 0,
    CONSTRAINT uq_room_type_code UNIQUE (hotel_id, code),
    CONSTRAINT ck_room_type_capacity CHECK (base_occupancy <= max_occupancy AND max_occupancy <= max_adults + max_children)
);

CREATE TABLE room_type_amenity (
    room_type_id uuid NOT NULL REFERENCES room_type (id) ON DELETE CASCADE,
    amenity_id   uuid NOT NULL REFERENCES amenity (id),
    PRIMARY KEY (room_type_id, amenity_id)
);

CREATE TABLE room_type_photo (
    id           uuid PRIMARY KEY,
    room_type_id uuid        NOT NULL REFERENCES room_type (id) ON DELETE CASCADE,
    content      bytea       NOT NULL,
    content_type varchar(50) NOT NULL,
    sort_order   integer     NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL
);

CREATE TABLE room (
    id                  uuid PRIMARY KEY,
    hotel_id            uuid         NOT NULL REFERENCES hotel (id),
    room_type_id        uuid         NOT NULL REFERENCES room_type (id),
    number              varchar(10)  NOT NULL,
    floor               integer,
    building            varchar(50),
    accessible          boolean      NOT NULL DEFAULT false,
    features            varchar(500),
    notes               varchar(1000),
    occupancy_status    varchar(20)  NOT NULL DEFAULT 'VACANT' CHECK (occupancy_status IN ('VACANT', 'OCCUPIED')),
    housekeeping_status varchar(20)  NOT NULL DEFAULT 'CLEAN'
        CHECK (housekeeping_status IN ('DIRTY', 'CLEANING', 'CLEAN', 'INSPECTED')),
    active              boolean      NOT NULL DEFAULT true,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    created_by          uuid,
    updated_by          uuid,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_room_number UNIQUE (hotel_id, number)
);

CREATE INDEX idx_room_type ON room (room_type_id);

CREATE TABLE room_status_history (
    id            uuid PRIMARY KEY,
    hotel_id      uuid        NOT NULL,
    room_id       uuid        NOT NULL REFERENCES room (id),
    dimension     varchar(20) NOT NULL CHECK (dimension IN ('OCCUPANCY', 'HOUSEKEEPING', 'TECHNICAL')),
    old_value     varchar(60),
    new_value     varchar(60),
    reason        varchar(255),
    business_date date        NOT NULL,
    occurred_at   timestamptz NOT NULL,
    user_id       uuid,
    user_name     varchar(50)
);

CREATE INDEX idx_room_status_history_room ON room_status_history (room_id, occurred_at DESC);

-- Blocages datés [start_date, end_date[ ; deux blocages d'une même chambre ne se chevauchent jamais.
CREATE TABLE room_block (
    id          uuid PRIMARY KEY,
    hotel_id    uuid         NOT NULL REFERENCES hotel (id),
    room_id     uuid         NOT NULL REFERENCES room (id),
    type        varchar(20)  NOT NULL CHECK (type IN ('MAINTENANCE', 'OUT_OF_ORDER', 'HOUSE_USE')),
    start_date  date         NOT NULL,
    end_date    date         NOT NULL,
    reason      varchar(255) NOT NULL,
    released_at timestamptz,
    released_by uuid,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    created_by  uuid,
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_room_block_dates CHECK (end_date > start_date),
    CONSTRAINT no_room_block_overlap EXCLUDE USING gist (
        room_id WITH =,
        daterange(start_date, end_date, '[)') WITH &&
    )
);

CREATE INDEX idx_room_block_hotel_dates ON room_block (hotel_id, start_date, end_date);
