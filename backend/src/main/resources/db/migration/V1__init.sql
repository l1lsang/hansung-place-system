--
-- PostgreSQL database dump
--

-- V1 snapshot of the existing public schema (DDL only).
-- Existing databases are baselined at version 1 and skip this migration.
-- Empty databases execute it to create the same schema.

-- Dumped from database version 17.11 (Debian 17.11-1.pgdg13+2)
-- Dumped by pg_dump version 17.11 (Debian 17.11-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: reservation_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservation_members (
    id bigint NOT NULL,
    reservation_id bigint NOT NULL,
    student_id character varying(20) NOT NULL,
    name character varying(50) NOT NULL
);


--
-- Name: reservation_members_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.reservation_members ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.reservation_members_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: reservations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservations (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    space_id bigint NOT NULL,
    start_time timestamp with time zone NOT NULL,
    end_time timestamp with time zone NOT NULL,
    purpose character varying(100),
    kind character varying(20) DEFAULT 'BOOKING'::character varying NOT NULL,
    status character varying(20) DEFAULT 'UPCOMING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cancelled_at timestamp with time zone,
    seat_id bigint,
    CONSTRAINT chk_reservation_kind CHECK (((kind)::text = ANY ((ARRAY['BOOKING'::character varying, 'SEAT_USE'::character varying])::text[]))),
    CONSTRAINT chk_reservation_status CHECK (((status)::text = ANY ((ARRAY['UPCOMING'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT chk_reservation_time CHECK ((end_time > start_time))
);


--
-- Name: reservations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.reservations ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.reservations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: seats; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seats (
    id bigint NOT NULL,
    space_id bigint NOT NULL,
    seat_number character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'AVAILABLE'::character varying NOT NULL,
    CONSTRAINT chk_seat_status CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'DISABLED'::character varying])::text[])))
);


--
-- Name: seats_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.seats ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.seats_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: space_blocks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.space_blocks (
    id bigint NOT NULL,
    space_id bigint NOT NULL,
    created_by bigint NOT NULL,
    start_time timestamp with time zone NOT NULL,
    end_time timestamp with time zone NOT NULL,
    reason character varying(100),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_block_time CHECK ((end_time > start_time))
);


--
-- Name: space_blocks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.space_blocks ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.space_blocks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: spaces; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spaces (
    id bigint NOT NULL,
    space_code character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    type character varying(30) NOT NULL,
    location character varying(100),
    min_capacity integer,
    max_capacity integer,
    venue character varying(30) NOT NULL,
    facilities character varying(255),
    booking_enabled boolean DEFAULT true NOT NULL
);


--
-- Name: spaces_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.spaces ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.spaces_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: system_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_settings (
    id bigint NOT NULL,
    academic_period character varying(20) NOT NULL,
    exam_period boolean DEFAULT false NOT NULL,
    updated_by bigint,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_academic_period CHECK (((academic_period)::text = ANY ((ARRAY['SEMESTER'::character varying, 'VACATION'::character varying])::text[])))
);


--
-- Name: system_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.system_settings ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.system_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    student_id character varying(20) NOT NULL,
    email character varying(100) NOT NULL,
    name character varying(50) NOT NULL,
    role character varying(20) NOT NULL
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.users ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: reservation_members reservation_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_members
    ADD CONSTRAINT reservation_members_pkey PRIMARY KEY (id);


--
-- Name: reservations reservations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_pkey PRIMARY KEY (id);


--
-- Name: seats seats_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seats
    ADD CONSTRAINT seats_pkey PRIMARY KEY (id);


--
-- Name: space_blocks space_blocks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.space_blocks
    ADD CONSTRAINT space_blocks_pkey PRIMARY KEY (id);


--
-- Name: spaces spaces_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spaces
    ADD CONSTRAINT spaces_pkey PRIMARY KEY (id);


--
-- Name: spaces spaces_space_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spaces
    ADD CONSTRAINT spaces_space_code_key UNIQUE (space_code);


--
-- Name: system_settings system_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_settings
    ADD CONSTRAINT system_settings_pkey PRIMARY KEY (id);


--
-- Name: reservation_members uq_member_in_reservation; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_members
    ADD CONSTRAINT uq_member_in_reservation UNIQUE (reservation_id, student_id);


--
-- Name: seats uq_seat_in_space; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seats
    ADD CONSTRAINT uq_seat_in_space UNIQUE (space_id, seat_number);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_student_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_student_id_key UNIQUE (student_id);


--
-- Name: idx_reservations_space_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_space_time ON public.reservations USING btree (space_id, start_time, end_time);


--
-- Name: idx_reservations_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_user ON public.reservations USING btree (user_id);


--
-- Name: idx_space_blocks_space_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_space_blocks_space_time ON public.space_blocks USING btree (space_id, start_time, end_time);


--
-- Name: space_blocks fk_block_creator; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.space_blocks
    ADD CONSTRAINT fk_block_creator FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: space_blocks fk_block_space; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.space_blocks
    ADD CONSTRAINT fk_block_space FOREIGN KEY (space_id) REFERENCES public.spaces(id);


--
-- Name: reservation_members fk_member_reservation; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_members
    ADD CONSTRAINT fk_member_reservation FOREIGN KEY (reservation_id) REFERENCES public.reservations(id) ON DELETE CASCADE;


--
-- Name: reservations fk_reservation_seat; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT fk_reservation_seat FOREIGN KEY (seat_id) REFERENCES public.seats(id);


--
-- Name: reservations fk_reservation_space; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT fk_reservation_space FOREIGN KEY (space_id) REFERENCES public.spaces(id);


--
-- Name: reservations fk_reservation_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: seats fk_seat_space; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seats
    ADD CONSTRAINT fk_seat_space FOREIGN KEY (space_id) REFERENCES public.spaces(id);


--
-- Name: system_settings fk_settings_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_settings
    ADD CONSTRAINT fk_settings_user FOREIGN KEY (updated_by) REFERENCES public.users(id);


--
-- PostgreSQL database dump complete
--


