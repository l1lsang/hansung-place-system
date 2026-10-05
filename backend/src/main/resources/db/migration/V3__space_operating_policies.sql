-- Additive opt-in policy. Existing spaces and system_settings remain unchanged.
CREATE TABLE public.space_operating_policies (
    space_id bigint PRIMARY KEY REFERENCES public.spaces(id),
    enabled boolean NOT NULL,
    academic_period varchar(20) NOT NULL CHECK (academic_period IN ('SEMESTER', 'VACATION')),
    exam_start_date date,
    exam_end_date date,
    updated_by bigint NOT NULL REFERENCES public.users(id),
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_exam_dates CHECK (
        (exam_start_date IS NULL AND exam_end_date IS NULL) OR
        (exam_start_date IS NOT NULL AND exam_end_date IS NOT NULL AND exam_start_date <= exam_end_date)
    )
);

CREATE TABLE public.space_operating_hours (
    space_id bigint NOT NULL REFERENCES public.space_operating_policies(space_id),
    period varchar(20) NOT NULL CHECK (period IN ('SEMESTER', 'VACATION', 'EXAM')),
    day_of_week integer NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    closed boolean NOT NULL,
    open_minute integer,
    close_minute integer,
    PRIMARY KEY (space_id, period, day_of_week),
    CONSTRAINT chk_operating_minutes CHECK (
        (closed AND open_minute IS NULL AND close_minute IS NULL) OR
        (NOT closed AND open_minute IS NOT NULL AND close_minute IS NOT NULL
         AND open_minute >= 0 AND open_minute < close_minute AND close_minute <= 1440)
    )
);
