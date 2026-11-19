-- DD-7X Vocal Delay Pedal Commercial License Schema
-- Security Model: Default-Deny RLS on ALL tables. APK has zero direct table access.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Buyers (CRM of paying customers)
CREATE TABLE IF NOT EXISTS public.buyers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name TEXT NOT NULL,
    whatsapp_phone TEXT UNIQUE NOT NULL,
    city TEXT,
    acquisition_source TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Licenses
CREATE TABLE IF NOT EXISTS public.licenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    license_key TEXT UNIQUE NOT NULL,
    buyer_id UUID NOT NULL REFERENCES public.buyers(id) ON DELETE RESTRICT,
    plan_tier TEXT NOT NULL DEFAULT 'PRO_LIFETIME',
    max_devices INTEGER NOT NULL DEFAULT 1 CHECK (max_devices >= 1),
    status TEXT NOT NULL DEFAULT 'UNUSED' CHECK (status IN ('UNUSED', 'ACTIVE', 'REVOKED', 'TRANSFERRED')),
    payment_ref TEXT,
    amount_paid_inr INTEGER NOT NULL CHECK (amount_paid_inr >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Device Activations (1:N table supporting clean phone-upgrade transfers)
CREATE TABLE IF NOT EXISTS public.device_activations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    license_id UUID NOT NULL REFERENCES public.licenses(id) ON DELETE CASCADE,
    device_fingerprint_hash TEXT NOT NULL,
    device_model TEXT,
    android_version TEXT,
    issued_ed25519_token TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    activated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_device_activations_license_active
    ON public.device_activations (license_id, is_active);

-- 4. Tamper-evident License Audit Logs
CREATE TABLE IF NOT EXISTS public.license_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    license_id UUID REFERENCES public.licenses(id) ON DELETE SET NULL,
    event_type TEXT NOT NULL CHECK (
        event_type IN ('KEY_MINTED', 'ACTIVATION_SUCCESS', 'ACTIVATION_BLOCKED_LIMIT', 'DEVICE_TRANSFER', 'KEY_REVOKED')
    ),
    metadata_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- MANDATORY ROW-LEVEL SECURITY (Default-Deny for anon & authenticated clients)
ALTER TABLE public.buyers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.licenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_activations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.license_audit_logs ENABLE ROW LEVEL SECURITY;

-- Explicitly revoke direct access from public/anon roles; Edge Functions use service_role
REVOKE ALL ON public.buyers FROM anon, authenticated;
REVOKE ALL ON public.licenses FROM anon, authenticated;
REVOKE ALL ON public.device_activations FROM anon, authenticated;
REVOKE ALL ON public.license_audit_logs FROM anon, authenticated;
