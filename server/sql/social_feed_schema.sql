create table if not exists public.feed_posts (
    id uuid primary key default gen_random_uuid(),
    book_id uuid not null references public.autobiography_versions (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    author_name text not null,
    title text not null,
    excerpt text not null,
    content text not null,
    summary text,
    topics jsonb not null default '[]'::jsonb,
    emotions jsonb not null default '[]'::jsonb,
    experiences jsonb not null default '[]'::jsonb,
    visibility text not null default 'public' check (visibility in ('public')),
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    unique (book_id)
);

create table if not exists public.feed_comments (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.feed_posts (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    author_name text not null,
    content text not null,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.feed_read_events (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.feed_posts (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    dwell_seconds integer not null default 0,
    completed boolean not null default false,
    query_text text,
    created_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.chat_rooms (
    id uuid primary key default gen_random_uuid(),
    room_key text not null unique,
    member_a_id uuid not null references auth.users (id) on delete cascade,
    member_b_id uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default timezone('utc', now()),
    check (member_a_id <> member_b_id)
);

create table if not exists public.chat_messages (
    id uuid primary key default gen_random_uuid(),
    room_id uuid not null references public.chat_rooms (id) on delete cascade,
    sender_id uuid not null references auth.users (id) on delete cascade,
    sender_name text not null,
    content text not null,
    created_at timestamptz not null default timezone('utc', now())
);

create index if not exists feed_posts_user_id_created_at_idx
    on public.feed_posts (user_id, created_at desc);

create index if not exists feed_comments_post_id_created_at_idx
    on public.feed_comments (post_id, created_at asc);

create index if not exists feed_read_events_user_id_created_at_idx
    on public.feed_read_events (user_id, created_at desc);

create index if not exists feed_read_events_post_id_created_at_idx
    on public.feed_read_events (post_id, created_at desc);

create index if not exists chat_rooms_member_a_id_created_at_idx
    on public.chat_rooms (member_a_id, created_at desc);

create index if not exists chat_rooms_member_b_id_created_at_idx
    on public.chat_rooms (member_b_id, created_at desc);

create index if not exists chat_messages_room_id_created_at_idx
    on public.chat_messages (room_id, created_at asc);

alter table public.feed_posts enable row level security;
alter table public.feed_comments enable row level security;
alter table public.feed_read_events enable row level security;
alter table public.chat_rooms enable row level security;
alter table public.chat_messages enable row level security;

drop policy if exists "feed_posts_select_authenticated" on public.feed_posts;
create policy "feed_posts_select_authenticated"
    on public.feed_posts for select
    using (auth.uid() is not null and visibility = 'public');

drop policy if exists "feed_posts_insert_own" on public.feed_posts;
create policy "feed_posts_insert_own"
    on public.feed_posts for insert
    with check (user_id = auth.uid());

drop policy if exists "feed_posts_update_own" on public.feed_posts;
create policy "feed_posts_update_own"
    on public.feed_posts for update
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "feed_comments_select_authenticated" on public.feed_comments;
create policy "feed_comments_select_authenticated"
    on public.feed_comments for select
    using (
        auth.uid() is not null
        and exists (
            select 1
            from public.feed_posts
            where feed_posts.id = feed_comments.post_id
              and feed_posts.visibility = 'public'
        )
    );

drop policy if exists "feed_comments_insert_own" on public.feed_comments;
create policy "feed_comments_insert_own"
    on public.feed_comments for insert
    with check (user_id = auth.uid());

drop policy if exists "feed_read_events_all_own" on public.feed_read_events;
create policy "feed_read_events_all_own"
    on public.feed_read_events for all
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "chat_rooms_member_select" on public.chat_rooms;
create policy "chat_rooms_member_select"
    on public.chat_rooms for select
    using (auth.uid() = member_a_id or auth.uid() = member_b_id);

drop policy if exists "chat_rooms_member_insert" on public.chat_rooms;
create policy "chat_rooms_member_insert"
    on public.chat_rooms for insert
    with check (auth.uid() = member_a_id or auth.uid() = member_b_id);

drop policy if exists "chat_messages_member_select" on public.chat_messages;
create policy "chat_messages_member_select"
    on public.chat_messages for select
    using (
        exists (
            select 1
            from public.chat_rooms
            where chat_rooms.id = chat_messages.room_id
              and (chat_rooms.member_a_id = auth.uid() or chat_rooms.member_b_id = auth.uid())
        )
    );

drop policy if exists "chat_messages_sender_insert" on public.chat_messages;
create policy "chat_messages_sender_insert"
    on public.chat_messages for insert
    with check (
        sender_id = auth.uid()
        and exists (
            select 1
            from public.chat_rooms
            where chat_rooms.id = chat_messages.room_id
              and (chat_rooms.member_a_id = auth.uid() or chat_rooms.member_b_id = auth.uid())
        )
    );
