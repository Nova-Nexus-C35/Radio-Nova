-- Atomic publisher-key mutations and their audit records.
-- Apply only to NOVA-STAGING after reviewing the matching Edge Function change.
begin;

create or replace function public.nova_register_publisher_key(
  p_publisher_id text,
  p_publisher_key_id text,
  p_owner_user_id uuid,
  p_public_key_base64 text
)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
begin
  -- Serialize first-time registrations for the same publisher ID, including races.
  perform pg_advisory_xact_lock(hashtextextended(p_publisher_id, 0));

  if exists (
    select 1 from private.nova_module_publishers p
    where p.publisher_id = p_publisher_id
  ) then
    raise exception using errcode = 'P0001', message = 'publisher_id_already_registered';
  end if;

  insert into private.nova_module_publishers (
    publisher_id, publisher_key_id, owner_user_id, created_by,
    public_key_base64, algorithm, status
  ) values (
    p_publisher_id, p_publisher_key_id, p_owner_user_id, p_owner_user_id,
    p_public_key_base64, 'Ed25519', 'active'
  );

  insert into private.nova_module_audit (
    actor_user_id, actor_role, module_id, module_version,
    action, outcome, metadata
  ) values (
    p_owner_user_id, 'publisher_owner', null, null,
    'publisher.register', 'success',
    jsonb_build_object('publisherId', p_publisher_id, 'publisherKeyId', p_publisher_key_id)
  );

  return jsonb_build_object(
    'ok', true,
    'action', 'publisher.register',
    'publisherId', p_publisher_id,
    'publisherKeyId', p_publisher_key_id
  );
end;
$$;

create or replace function public.nova_rotate_publisher_key(
  p_publisher_id text,
  p_old_publisher_key_id text,
  p_new_publisher_key_id text,
  p_owner_user_id uuid,
  p_public_key_base64 text
)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_old private.nova_module_publishers%rowtype;
  v_revoked_at timestamptz := clock_timestamp();
begin
  select p.* into v_old
  from private.nova_module_publishers p
  where p.publisher_id = p_publisher_id
    and p.publisher_key_id = p_old_publisher_key_id
    and p.owner_user_id = p_owner_user_id
    and p.status = 'active'
  for update;

  if not found then
    raise exception using errcode = 'P0002', message = 'active_owned_key_not_found';
  end if;

  insert into private.nova_module_publishers (
    publisher_id, publisher_key_id, owner_user_id, created_by,
    public_key_base64, algorithm, status, supersedes_key_id
  ) values (
    p_publisher_id, p_new_publisher_key_id, p_owner_user_id, p_owner_user_id,
    p_public_key_base64, 'Ed25519', 'active', p_old_publisher_key_id
  );

  update private.nova_module_publishers p
  set status = 'revoked', revoked_at = v_revoked_at
  where p.publisher_id = p_publisher_id
    and p.publisher_key_id = p_old_publisher_key_id
    and p.owner_user_id = p_owner_user_id
    and p.status = 'active';

  if not found then
    raise exception using errcode = 'P0002', message = 'active_owned_key_not_found';
  end if;

  insert into private.nova_module_audit (
    actor_user_id, actor_role, module_id, module_version,
    action, outcome, metadata
  ) values (
    p_owner_user_id, 'publisher_owner', null, null,
    'publisher.rotate', 'success',
    jsonb_build_object(
      'publisherId', p_publisher_id,
      'oldPublisherKeyId', p_old_publisher_key_id,
      'newPublisherKeyId', p_new_publisher_key_id
    )
  );

  return jsonb_build_object(
    'ok', true,
    'action', 'publisher.rotate',
    'publisherId', p_publisher_id,
    'activePublisherKeyId', p_new_publisher_key_id,
    'revokedPublisherKeyId', p_old_publisher_key_id
  );
end;
$$;

create or replace function public.nova_revoke_publisher_key(
  p_publisher_id text,
  p_publisher_key_id text,
  p_owner_user_id uuid
)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_key private.nova_module_publishers%rowtype;
  v_revoked_at timestamptz := clock_timestamp();
begin
  select p.* into v_key
  from private.nova_module_publishers p
  where p.publisher_id = p_publisher_id
    and p.publisher_key_id = p_publisher_key_id
    and p.owner_user_id = p_owner_user_id
    and p.status = 'active'
  for update;

  if not found then
    raise exception using errcode = 'P0002', message = 'active_owned_key_not_found';
  end if;

  update private.nova_module_publishers p
  set status = 'revoked', revoked_at = v_revoked_at
  where p.publisher_id = p_publisher_id
    and p.publisher_key_id = p_publisher_key_id
    and p.owner_user_id = p_owner_user_id
    and p.status = 'active';

  if not found then
    raise exception using errcode = 'P0002', message = 'active_owned_key_not_found';
  end if;

  insert into private.nova_module_audit (
    actor_user_id, actor_role, module_id, module_version,
    action, outcome, metadata
  ) values (
    p_owner_user_id, 'publisher_owner', null, null,
    'publisher.revoke', 'success',
    jsonb_build_object('publisherId', p_publisher_id, 'publisherKeyId', p_publisher_key_id)
  );

  return jsonb_build_object(
    'ok', true,
    'action', 'publisher.revoke',
    'publisherId', p_publisher_id,
    'revokedPublisherKeyId', p_publisher_key_id
  );
end;
$$;

revoke all on function public.nova_register_publisher_key(text, text, uuid, text) from public, anon, authenticated;
revoke all on function public.nova_rotate_publisher_key(text, text, text, uuid, text) from public, anon, authenticated;
revoke all on function public.nova_revoke_publisher_key(text, text, uuid) from public, anon, authenticated;
grant execute on function public.nova_register_publisher_key(text, text, uuid, text) to service_role;
grant execute on function public.nova_rotate_publisher_key(text, text, text, uuid, text) to service_role;
grant execute on function public.nova_revoke_publisher_key(text, text, uuid) to service_role;

commit;
