-- Regression smoke test for inherited parent-post RLS in the frozen legacy Supabase schema.
-- Run only against NOVA-STAGING or a disposable local clone with the historical schema.
-- All fixtures are inserted inside this transaction and rolled back at the end.
-- This script does not create/alter policies, tables, extensions, or persistent records.

begin;

insert into auth.users
  (id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
values
  ('11111111-1111-4111-8111-111111111111', 'authenticated', 'authenticated',
   'nova-rls-a@test.invalid', '', now(), now(), now()),
  ('22222222-2222-4222-8222-222222222222', 'authenticated', 'authenticated',
   'nova-rls-b@test.invalid', '', now(), now(), now()),
  ('33333333-3333-4333-8333-333333333333', 'authenticated', 'authenticated',
   'nova-rls-c@test.invalid', '', now(), now(), now());

insert into public.nova_social_profiles
  (user_id, username, display_name, is_private, discoverable)
values
  ('11111111-1111-4111-8111-111111111111', 'rls_private_a', 'RLS Private A', true, true),
  ('22222222-2222-4222-8222-222222222222', 'rls_reader_b', 'RLS Reader B', false, true),
  ('33333333-3333-4333-8333-333333333333', 'rls_public_c', 'RLS Public C', false, true);

insert into public.nova_social_posts (id, author_id, body, visibility, status)
values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
   '11111111-1111-4111-8111-111111111111', 'private profile post', 'public', 'published'),
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc',
   '33333333-3333-4333-8333-333333333333', 'public profile post', 'public', 'published');

insert into public.nova_social_comments (id, post_id, author_id, body, status)
values
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
   'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
   '11111111-1111-4111-8111-111111111111', 'private comment', 'published'),
  ('dddddddd-dddd-4ddd-8ddd-dddddddddddd',
   'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
   '33333333-3333-4333-8333-333333333333', 'public comment', 'published');

insert into public.nova_social_reactions (post_id, user_id, reaction_type)
values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
   '11111111-1111-4111-8111-111111111111', 'like'),
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc',
   '33333333-3333-4333-8333-333333333333', 'like');

set local role authenticated;
select set_config('request.jwt.claim.sub',
  '22222222-2222-4222-8222-222222222222', true);
select set_config('request.jwt.claims',
  '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);

do $$
declare
  row_count integer;
  denied boolean;
begin
  select count(*) into row_count
  from public.nova_social_posts
  where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  if row_count <> 0 then raise exception 'FAIL: private-profile post visible'; end if;

  select count(*) into row_count
  from public.nova_social_comments
  where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  if row_count <> 0 then raise exception 'FAIL: private comment leaked'; end if;

  select count(*) into row_count
  from public.nova_social_reactions
  where post_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  if row_count <> 0 then raise exception 'FAIL: private reaction leaked'; end if;

  select count(*) into row_count
  from public.nova_social_posts
  where id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
  if row_count <> 1 then raise exception 'FAIL: public post unexpectedly hidden'; end if;

  select count(*) into row_count
  from public.nova_social_comments
  where id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
  if row_count <> 1 then raise exception 'FAIL: public comment unexpectedly hidden'; end if;

  select count(*) into row_count
  from public.nova_social_reactions
  where post_id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
  if row_count <> 1 then raise exception 'FAIL: public reaction unexpectedly hidden'; end if;

  denied := false;
  begin
    insert into public.nova_social_comments (post_id, author_id, body)
    values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', auth.uid(), 'unauthorized comment');
  exception when insufficient_privilege then denied := true;
  end;
  if not denied then raise exception 'FAIL: comment insert allowed on invisible post'; end if;

  denied := false;
  begin
    insert into public.nova_social_reactions (post_id, user_id, reaction_type)
    values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', auth.uid(), 'like');
  exception when insufficient_privilege then denied := true;
  end;
  if not denied then raise exception 'FAIL: reaction insert allowed on invisible post'; end if;

  denied := false;
  begin
    insert into public.nova_social_bookmarks (post_id, user_id)
    values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', auth.uid());
  exception when insufficient_privilege then denied := true;
  end;
  if not denied then raise exception 'FAIL: bookmark insert allowed on invisible post'; end if;
end $$;

select 'PASS: parent RLS blocks private child reads/writes and permits public child reads' as result;

rollback;
