-- Regression smoke test for inherited parent-post RLS in the frozen legacy Supabase schema.
-- Run only against NOVA-STAGING or a disposable local clone with the historical schema.
-- All fixtures are inserted inside this transaction and rolled back at the end.
-- This script does not create/alter policies, tables, extensions, or persistent records.

begin;

insert into auth.users
  (id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
values
  ('11111111-1111-4111-8111-111111111111', 'authenticated', 'authenticated', 'nova-rls-a@test.invalid', '', now(), now(), now()),
  ('22222222-2222-4222-8222-222222222222', 'authenticated', 'authenticated', 'nova-rls-b@test.invalid', '', now(), now(), now()),
  ('33333333-3333-4333-8333-333333333333', 'authenticated', 'authenticated', 'nova-rls-c@test.invalid', '', now(), now(), now()),
  ('44444444-4444-4444-8444-444444444444', 'authenticated', 'authenticated', 'nova-rls-d@test.invalid', '', now(), now(), now()),
  ('55555555-5555-4555-8555-555555555555', 'authenticated', 'authenticated', 'nova-rls-e@test.invalid', '', now(), now(), now()),
  ('66666666-6666-4666-8666-666666666666', 'authenticated', 'authenticated', 'nova-rls-f@test.invalid', '', now(), now(), now());

insert into public.nova_social_profiles (user_id, username, display_name, is_private, discoverable)
values
  ('11111111-1111-4111-8111-111111111111', 'rls_private_a', 'RLS Private A', true, true),
  ('22222222-2222-4222-8222-222222222222', 'rls_reader_b', 'RLS Reader B', false, true),
  ('33333333-3333-4333-8333-333333333333', 'rls_public_c', 'RLS Public C', false, true),
  ('44444444-4444-4444-8444-444444444444', 'rls_follow_d', 'RLS Follow D', true, true),
  ('55555555-5555-4555-8555-555555555555', 'rls_block_e', 'RLS Block E', false, true),
  ('66666666-6666-4666-8666-666666666666', 'rls_block_f', 'RLS Block F', false, true);

insert into public.nova_social_follows (follower_id, followed_id, status)
values ('22222222-2222-4222-8222-222222222222', '44444444-4444-4444-8444-444444444444', 'accepted');

insert into public.nova_social_blocks (blocker_id, blocked_id)
values
  ('22222222-2222-4222-8222-222222222222', '55555555-5555-4555-8555-555555555555'),
  ('66666666-6666-4666-8666-666666666666', '22222222-2222-4222-8222-222222222222');

insert into public.nova_social_posts (id, author_id, body, visibility, status)
values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111', 'private profile post', 'public', 'published'),
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', '33333333-3333-4333-8333-333333333333', 'public profile post', 'public', 'published'),
  ('dddddddd-dddd-4ddd-8ddd-eeeeeeeeeeee', '44444444-4444-4444-8444-444444444444', 'followers-only post', 'followers', 'published'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', '55555555-5555-4555-8555-555555555555', 'blocked by reader', 'public', 'published'),
  ('ffffffff-ffff-4fff-8fff-ffffffffffff', '66666666-6666-4666-8666-666666666666', 'author blocks reader', 'public', 'published');

insert into public.nova_social_comments (id, post_id, author_id, body, status)
values
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111', 'private comment', 'published'),
  ('11111111-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', '33333333-3333-4333-8333-333333333333', 'public comment', 'published'),
  ('22222222-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'dddddddd-dddd-4ddd-8ddd-eeeeeeeeeeee', '44444444-4444-4444-8444-444444444444', 'follower comment', 'published'),
  ('33333333-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', '55555555-5555-4555-8555-555555555555', 'blocked comment', 'published'),
  ('44444444-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'ffffffff-ffff-4fff-8fff-ffffffffffff', '66666666-6666-4666-8666-666666666666', 'reverse-block comment', 'published');

insert into public.nova_social_reactions (post_id, user_id, reaction_type)
values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111', 'like'),
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', '33333333-3333-4333-8333-333333333333', 'like'),
  ('dddddddd-dddd-4ddd-8ddd-eeeeeeeeeeee', '44444444-4444-4444-8444-444444444444', 'like'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', '55555555-5555-4555-8555-555555555555', 'like'),
  ('ffffffff-ffff-4fff-8fff-ffffffffffff', '66666666-6666-4666-8666-666666666666', 'like');

set local role authenticated;
select set_config('request.jwt.claim.sub', '22222222-2222-4222-8222-222222222222', true);
select set_config('request.jwt.claims', '{"sub":"22222222-2222-4222-8222-222222222222","role":"authenticated"}', true);

do $$
declare
  row_count integer;
  denied boolean;
begin
  -- Private author profile: all parent and child rows remain hidden.
  select count(*) into row_count from public.nova_social_posts where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  if row_count <> 0 then raise exception 'FAIL: private-profile post visible'; end if;
  select count(*) into row_count from public.nova_social_comments where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  if row_count <> 0 then raise exception 'FAIL: private comment leaked'; end if;
  select count(*) into row_count from public.nova_social_reactions where post_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  if row_count <> 0 then raise exception 'FAIL: private reaction leaked'; end if;

  -- Public author profile: parent and child reads remain available.
  select count(*) into row_count from public.nova_social_posts where id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
  if row_count <> 1 then raise exception 'FAIL: public post unexpectedly hidden'; end if;
  select count(*) into row_count from public.nova_social_comments where id = '11111111-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  if row_count <> 1 then raise exception 'FAIL: public comment unexpectedly hidden'; end if;
  select count(*) into row_count from public.nova_social_reactions where post_id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
  if row_count <> 1 then raise exception 'FAIL: public reaction unexpectedly hidden'; end if;

  -- Accepted follower may see followers-only parent and its children.
  select count(*) into row_count from public.nova_social_posts where id = 'dddddddd-dddd-4ddd-8ddd-eeeeeeeeeeee';
  if row_count <> 1 then raise exception 'FAIL: accepted follower cannot see followers post'; end if;
  select count(*) into row_count from public.nova_social_comments where id = '22222222-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  if row_count <> 1 then raise exception 'FAIL: accepted follower cannot see comment'; end if;

  -- A block in either direction hides the parent and its children.
  select count(*) into row_count from public.nova_social_posts where id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee';
  if row_count <> 0 then raise exception 'FAIL: post by blocked user visible'; end if;
  select count(*) into row_count from public.nova_social_comments where id = '33333333-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  if row_count <> 0 then raise exception 'FAIL: comment by blocked user visible'; end if;
  select count(*) into row_count from public.nova_social_posts where id = 'ffffffff-ffff-4fff-8fff-ffffffffffff';
  if row_count <> 0 then raise exception 'FAIL: post by user who blocked reader visible'; end if;
  select count(*) into row_count from public.nova_social_comments where id = '44444444-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  if row_count <> 0 then raise exception 'FAIL: comment by user who blocked reader visible'; end if;

  -- Writes to an invisible parent are rejected by child-table RLS.
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

select 'PASS: private/public/follower/block parent visibility is inherited by child reads; invisible-parent child writes are denied' as result;

rollback;
