create extension if not exists pgcrypto;

create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    email text,
    display_name text,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    title text not null,
    theme text,
    status text not null default 'active',
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    session_id uuid not null references public.sessions (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    role text not null check (role in ('user', 'assistant', 'system')),
    content text not null,
    source_type text not null default 'text' check (source_type in ('text', 'stt', 'generated')),
    stt_confidence numeric,
    safety_mode boolean not null default false,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.memory_items (
    id uuid primary key default gen_random_uuid(),
    session_id uuid not null references public.sessions (id) on delete cascade,
    message_id uuid references public.messages (id) on delete set null,
    user_id uuid not null references auth.users (id) on delete cascade,
    period text,
    place text,
    person text,
    event text,
    emotions jsonb,
    meaning text,
    raw_text text,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.chapter_drafts (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    session_id uuid references public.sessions (id) on delete set null,
    chapter_type text,
    title text not null,
    content text not null,
    version_no integer not null default 1,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.autobiography_versions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    title text not null,
    content text not null,
    chapter_ids jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default timezone('utc', now())
);

create index if not exists sessions_user_id_created_at_idx
    on public.sessions (user_id, created_at desc);

create index if not exists messages_user_id_session_id_created_at_idx
    on public.messages (user_id, session_id, created_at asc);

create index if not exists memory_items_user_id_session_id_created_at_idx
    on public.memory_items (user_id, session_id, created_at asc);

create index if not exists chapter_drafts_user_id_session_id_created_at_idx
    on public.chapter_drafts (user_id, session_id, created_at asc);

create index if not exists autobiography_versions_user_id_created_at_idx
    on public.autobiography_versions (user_id, created_at desc);

alter table public.profiles enable row level security;
alter table public.sessions enable row level security;
alter table public.messages enable row level security;
alter table public.memory_items enable row level security;
alter table public.chapter_drafts enable row level security;
alter table public.autobiography_versions enable row level security;

drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own"
    on public.profiles for select
    using (id = auth.uid());

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own"
    on public.profiles for insert
    with check (id = auth.uid());

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own"
    on public.profiles for update
    using (id = auth.uid())
    with check (id = auth.uid());

drop policy if exists "sessions_all_own" on public.sessions;
create policy "sessions_all_own"
    on public.sessions for all
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "messages_all_own" on public.messages;
create policy "messages_all_own"
    on public.messages for all
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "memory_items_all_own" on public.memory_items;
create policy "memory_items_all_own"
    on public.memory_items for all
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "chapter_drafts_all_own" on public.chapter_drafts;
create policy "chapter_drafts_all_own"
    on public.chapter_drafts for all
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "autobiography_versions_all_own" on public.autobiography_versions;
create policy "autobiography_versions_all_own"
    on public.autobiography_versions for all
    using (user_id = auth.uid())
    with check (user_id = auth.uid());
