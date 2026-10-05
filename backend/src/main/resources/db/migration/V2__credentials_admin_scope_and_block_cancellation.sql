-- Additive only: existing users have no passwords and no implied admin scope.
-- Provision credentials and grants explicitly; never seed a default password.
CREATE TABLE public.user_credentials (
    user_id bigint PRIMARY KEY REFERENCES public.users(id),
    password_hash varchar(60) NOT NULL,
    CONSTRAINT chk_password_hash_bcrypt CHECK
        (password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$')
);

CREATE TABLE public.admin_space_permissions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES public.users(id),
    space_id bigint NOT NULL REFERENCES public.spaces(id),
    CONSTRAINT uq_admin_space UNIQUE (user_id, space_id)
);

-- A block is released without deleting its original reason, creator or dates.
ALTER TABLE public.space_blocks ADD COLUMN cancelled_at timestamp with time zone;
