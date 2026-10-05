-- Additive only. Existing bookings, policies and credentials remain unchanged.
CREATE TABLE public.space_booking_rules (
    space_id bigint PRIMARY KEY REFERENCES public.spaces(id),
    enabled boolean NOT NULL DEFAULT false,
    slot_minutes integer NOT NULL DEFAULT 30 CHECK (slot_minutes IN (15, 30, 60)),
    min_duration_minutes integer NOT NULL DEFAULT 30 CHECK (min_duration_minutes BETWEEN 1 AND 1440),
    max_duration_minutes integer NOT NULL DEFAULT 180 CHECK (max_duration_minutes BETWEEN 1 AND 1440),
    advance_days integer NOT NULL DEFAULT 7 CHECK (advance_days BETWEEN 0 AND 365),
    daily_max_minutes integer CHECK (daily_max_minutes BETWEEN 1 AND 1440),
    usage_scope varchar(10) NOT NULL DEFAULT 'SPACE' CHECK (usage_scope IN ('SPACE', 'VENUE')),
    prevent_adjacent boolean NOT NULL DEFAULT false,
    purpose_required boolean NOT NULL DEFAULT false,
    instant_use_minutes integer NOT NULL DEFAULT 180 CHECK (instant_use_minutes BETWEEN 1 AND 1440),
    updated_by bigint NOT NULL REFERENCES public.users(id),
    updated_at timestamptz NOT NULL,
    CHECK (min_duration_minutes <= max_duration_minutes),
    CHECK (min_duration_minutes % slot_minutes = 0 AND max_duration_minutes % slot_minutes = 0),
    CHECK (daily_max_minutes IS NULL OR daily_max_minutes >= min_duration_minutes)
);

ALTER TABLE public.reservations
    ADD COLUMN ended_at timestamptz;

CREATE TABLE public.reservation_actions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id bigint NOT NULL REFERENCES public.reservations(id),
    actor_id bigint NOT NULL REFERENCES public.users(id),
    action varchar(30) NOT NULL CHECK (action IN ('CANCEL', 'RETURN_SEAT', 'ADMIN_CANCEL', 'ADMIN_END')),
    reason varchar(200),
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_reservation_actions_reservation ON public.reservation_actions(reservation_id, id);
