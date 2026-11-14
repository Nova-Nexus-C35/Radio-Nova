-- NOVA module registry foundation for a clean Supabase staging project.
-- Apply only to NOVA-STAGING. Never apply this bootstrap script to production.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
grant usage on schema private to service_role;

create table private.nova_module_publishers (
  publisher_id text not null check (publisher_id ~ '^[A-Za-z0-9_.-]{1,80}$'),
  publisher_key_id text not null check (publisher_key_id ~ '^[A-Za-z0-9_.-]{1,80}$'),
  owner_user_id uuid not null references auth.users(id) on delete restrict,
  created_by uuid not null references auth.users(id) on delete restrict,
  public_key_base64 text not null check (length(public_key_base64) between 40 and 512),
  algorithm text not null default 'Ed25519' check (algorithm = 'Ed25519'),
  status text not null default 'active' check (status in ('active','revoked')),
  created_at timestamptz not null default now(),
  revoked_at timestamptz,
  supersedes_key_id text,
  primary key (publisher_id, publisher_key_id),
  unique (publisher_key_id),
  constraint nova_module_publisher_revocation_check
    check ((status = 'active' and revoked_at is null) or (status = 'revoked' and revoked_at is not null)),
  constraint nova_module_publisher_supersedes_fk
    foreign key (publisher_id, supersedes_key_id)
    references private.nova_module_publishers(publisher_id, publisher_key_id) on delete restrict
);

create table private.nova_module_registry (
  module_id text not null check (module_id ~ '^nova\.[a-z0-9]+([.-][a-z0-9]+)*$'),
  version text not null check (version ~ '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9]*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$'),
  manifest jsonb not null check (jsonb_typeof(manifest) = 'object' and octet_length(manifest::text) <= 65536),
  execution_mode text not null check (execution_mode in ('local','remote','hybrid')),
  artifact_sha256 text not null check (artifact_sha256 ~ '^[a-fA-F0-9]{64}$'),
  signature_base64 text not null check (length(signature_base64) between 40 and 512),
  publisher_id text not null,
  publisher_key_id text not null,
  artifact_storage_path text not null check (length(artifact_storage_path) between 1 and 512 and artifact_storage_path !~ '(^/|\.\.|://)'),
  created_by uuid not null references auth.users(id) on delete restrict,
  reviewed_by uuid references auth.users(id) on delete restrict,
  reviewed_at timestamptz,
  published_at timestamptz,
  revoked_at timestamptz,
  review_reason text,
  status text not null default 'draft' check (status in ('draft','review','published','revoked')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (module_id, version),
  foreign key (publisher_id, publisher_key_id)
    references private.nova_module_publishers(publisher_id, publisher_key_id) on delete restrict,
  constraint nova_module_registry_published_check
    check (status <> 'published' or (published_at is not null and reviewed_by is not null and reviewed_at is not null and revoked_at is null)),
  constraint nova_module_registry_revoked_check check (status <> 'revoked' or revoked_at is not null)
);
create index nova_module_registry_catalog_idx on private.nova_module_registry (published_at desc) where status = 'published';
create index nova_module_registry_publisher_idx on private.nova_module_registry (publisher_id, publisher_key_id);

create table private.nova_module_user_grants (
  owner_user_id uuid not null references auth.users(id) on delete restrict,
  module_id text not null,
  version text not null,
  permissions text[] not null default '{}',
  status text not null default 'active' check (status in ('active','revoked','expired')),
  granted_by uuid not null references auth.users(id) on delete restrict,
  granted_at timestamptz not null default now(),
  expires_at timestamptz,
  revoked_at timestamptz,
  revocation_reason text,
  primary key (owner_user_id, module_id, version),
  foreign key (module_id, version) references private.nova_module_registry(module_id, version) on delete restrict,
  constraint nova_module_grant_expiry_check
    check ((status = 'active' and revoked_at is null) or (status = 'revoked' and revoked_at is not null) or status = 'expired')
);
create index nova_module_user_grants_lookup_idx on private.nova_module_user_grants (owner_user_id, status, expires_at);

create table private.nova_module_audit (
  id bigint generated always as identity primary key,
  actor_user_id uuid references auth.users(id) on delete set null,
  actor_role text not null check (length(actor_role) between 1 and 80),
  module_id text,
  module_version text,
  action text not null check (length(action) between 1 and 100),
  outcome text not null check (outcome in ('success','denied','failure')),
  request_id uuid,
  metadata jsonb not null default '{}'::jsonb check (jsonb_typeof(metadata) = 'object' and octet_length(metadata::text) <= 8192),
  created_at timestamptz not null default now()
);
create index nova_module_audit_created_idx on private.nova_module_audit (created_at desc);
create index nova_module_audit_module_idx on private.nova_module_audit (module_id, module_version);

alter table private.nova_module_publishers enable row level security;
alter table private.nova_module_publishers force row level security;
alter table private.nova_module_registry enable row level security;
alter table private.nova_module_registry force row level security;
alter table private.nova_module_user_grants enable row level security;
alter table private.nova_module_user_grants force row level security;
alter table private.nova_module_audit enable row level security;
alter table private.nova_module_audit force row level security;

revoke all on all tables in schema private from public, anon, authenticated;
grant select, insert, update, delete on all tables in schema private to service_role;
grant usage, select on all sequences in schema private to service_role;

create policy nova_module_publishers_server_only on private.nova_module_publishers
  for all to service_role using (true) with check (true);
create policy nova_module_registry_server_only on private.nova_module_registry
  for all to service_role using (true) with check (true);
create policy nova_module_user_grants_server_only on private.nova_module_user_grants
  for all to service_role using (true) with check (true);
create policy nova_module_audit_server_only on private.nova_module_audit
  for all to service_role using (true) with check (true);

create index nova_module_publishers_owner_idx on private.nova_module_publishers(owner_user_id);
create index nova_module_publishers_created_by_idx on private.nova_module_publishers(created_by);
create index nova_module_publishers_supersedes_idx on private.nova_module_publishers(publisher_id, supersedes_key_id);
create index nova_module_registry_created_by_idx on private.nova_module_registry(created_by);
create index nova_module_registry_reviewed_by_idx on private.nova_module_registry(reviewed_by);
create index nova_module_user_grants_granted_by_idx on private.nova_module_user_grants(granted_by);
create index nova_module_user_grants_module_version_idx on private.nova_module_user_grants(module_id, version);
create index nova_module_audit_actor_idx on private.nova_module_audit(actor_user_id);

create or replace function public.nova_registry_catalog_list(p_limit integer default 50)
returns jsonb language sql stable security invoker set search_path = ''
as $$
  select coalesce(jsonb_agg(item order by published_at desc), '[]'::jsonb)
  from (
    select jsonb_build_object(
      'module_id', r.module_id,
      'name', r.manifest ->> 'name',
      'version', r.version,
      'execution_mode', r.execution_mode,
      'capabilities', r.manifest -> 'capabilities',
      'platforms', r.manifest -> 'platforms',
      'core_min_version', r.manifest ->> 'coreMinVersion',
      'published_at', r.published_at
    ) as item, r.published_at
    from private.nova_module_registry r
    join private.nova_module_publishers p
      on p.publisher_id = r.publisher_id and p.publisher_key_id = r.publisher_key_id
    where r.status = 'published' and p.status = 'active'
    order by r.published_at desc
    limit greatest(1, least(coalesce(p_limit, 50), 100))
  ) catalog_items;
$$;
revoke all on function public.nova_registry_catalog_list(integer) from public, anon, authenticated;
grant execute on function public.nova_registry_catalog_list(integer) to service_role;

insert into storage.buckets (id, name, public, file_size_limit)
values ('nova-modules', 'nova-modules', false, 67108864)
on conflict (id) do update set public = false, file_size_limit = 67108864;
