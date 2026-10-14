-- Trebol Identity service - seed data.
--
-- Extracted VERBATIM from the monolith's data.sql: the 4 roles, the 54 authority codes and the 100
-- role-permission grants, with the identity-generator restarts that MUST accompany them. Omit a
-- restart and the first runtime insert collides with a seeded row.
--
-- The 54 permission codes are the source of truth for the generated AuthorityCodes catalogue in
-- trebol-shared-contracts (CATALOGUE_SIZE 54). They are client-observable contract: renaming one
-- silently disables an access check, which is why Gate B exists. Do not edit them here.
--
-- Grant distribution across the 100 rows: Administrator 54 (every authority), Manager 31,
-- Salesperson 14, Customer 1 (just checkout - the role self-registration assigns).
-- There is no runtime API to grant or revoke (BR-USER-038); this file IS the authorization policy.
--
-- app_user_role_permissions needs no restart statement: its rows do not specify the primary key.
--
-- ============================================================================================
-- DELIBERATELY ABSENT: the four bootstrap accounts.
--
-- The monolith seeds admin / manager / salesperson / customer into app_users with, in its own
-- words, "passwords equal usernames". That is register item BR-USER-055, and copying it here is
-- exactly what invariant G7 and exit criterion X10 forbid - it would put working credentials for
-- the all-54-authority Administrator role into committed configuration in a second place.
--
-- Decision, approved by the project owner for Phase 2:
--   * this file seeds roles, permissions and grants ONLY - no app_users row, and no user_id restart;
--   * the four accounts exist as a TEST FIXTURE in src/test/resources, so every ported test keeps
--     working unchanged (G3, G8);
--   * the operator step for creating the first administrator from the environment is recorded in
--     microservices-conversion/baseline/PHASE-2-EXIT.md;
--   * no new runtime code - there is no bootstrap component and no conditional seeding.
--
-- Consequence to be aware of when deploying: a freshly migrated database has roles and authorities
-- but NO accounts, so nothing can authenticate until that operator step is performed.
-- ============================================================================================

INSERT INTO `app_user_roles`
(`user_role_id`, `user_role_name`)
VALUES
(01, 'Administrator'),
(02, 'Manager'),
(03, 'Salesperson'),
(04, 'Customer');

INSERT INTO `app_permissions`
(`permission_id`, `permission_code`)
VALUES
(01, 'product_categories:delete'),
(02, 'product_categories:create'),
(03, 'product_categories:update'),
(04, 'product_categories:read'),
(05, 'order_statuses:delete'),
(06, 'order_statuses:create'),
(07, 'order_statuses:update'),
(08, 'order_statuses:read'),
(09, 'order_types:delete'),
(10, 'order_types:create'),
(11, 'order_types:update'),
(12, 'order_types:read'),
(13, 'customers:delete'),
(14, 'customers:create'),
(15, 'customers:update'),
(16, 'customers:read'),
(17, 'products:delete'),
(18, 'products:create'),
(19, 'products:update'),
(20, 'products:read'),
(21, 'orders:delete'),
(22, 'orders:create'),
(23, 'orders:update'),
(24, 'orders:read'),
(25, 'salespeople:delete'),
(26, 'salespeople:create'),
(27, 'salespeople:update'),
(28, 'salespeople:read'),
(29, 'users:delete'),
(30, 'users:create'),
(31, 'users:update'),
(32, 'users:read'),
(33, 'user_roles:delete'),
(34, 'user_roles:create'),
(35, 'user_roles:update'),
(36, 'user_roles:read'),
(37, 'people:delete'),
(38, 'people:create'),
(39, 'people:update'),
(40, 'people:read'),
(41, 'images:delete'),
(42, 'images:create'),
(43, 'images:update'),
(44, 'images:read'),
(45, 'checkout'),
(46, 'shippers:delete'),
(47, 'shippers:create'),
(48, 'shippers:update'),
(49, 'shippers:read'),
(50, 'product_lists:delete'),
(51, 'product_lists:create'),
(52, 'product_lists:update'),
(53, 'product_lists:read'),
(54, 'product_lists:contents');

INSERT INTO `app_user_role_permissions`
(`permission_id`, `user_role_id`)
VALUES
(01, 01),
(02, 01),
(03, 01),
(04, 01),
(05, 01),
(06, 01),
(07, 01),
(08, 01),
(09, 01),
(10, 01),
(11, 01),
(12, 01),
(13, 01),
(14, 01),
(15, 01),
(16, 01),
(17, 01),
(18, 01),
(19, 01),
(20, 01),
(21, 01),
(22, 01),
(23, 01),
(24, 01),
(25, 01),
(26, 01),
(27, 01),
(28, 01),
(29, 01),
(30, 01),
(31, 01),
(32, 01),
(33, 01),
(34, 01),
(35, 01),
(36, 01),
(37, 01),
(38, 01),
(39, 01),
(40, 01),
(41, 01),
(42, 01),
(43, 01),
(44, 01),
(45, 01),
(46, 01),
(47, 01),
(48, 01),
(49, 01),
(50, 01),
(51, 01),
(52, 01),
(53, 01),
(54, 01);

INSERT INTO `app_user_role_permissions`
(`permission_id`, `user_role_id`)
VALUES
(01, 02),
(02, 02),
(03, 02),
(04, 02),
(12, 02),
(16, 02),
(17, 02),
(18, 02),
(19, 02),
(20, 02),
(23, 02),
(24, 02),
(26, 02),
(27, 02),
(28, 02),
(36, 02),
(40, 02),
(41, 02),
(42, 02),
(43, 02),
(44, 02),
(45, 02),
(46, 02),
(47, 02),
(48, 02),
(49, 02),
(50, 02),
(51, 02),
(52, 02),
(53, 02),
(54, 02);

INSERT INTO `app_user_role_permissions`
(`permission_id`, `user_role_id`)
VALUES
(03, 03),
(04, 03),
(08, 03),
(12, 03),
(16, 03),
(17, 03),
(18, 03),
(19, 03),
(20, 03),
(22, 03),
(24, 03),
(28, 03),
(40, 03),
(45, 03);

INSERT INTO `app_user_role_permissions`
(`permission_id`, `user_role_id`)
VALUES
(45, 04);

ALTER TABLE `app_user_roles` ALTER COLUMN `user_role_id` RESTART WITH 5;

ALTER TABLE `app_permissions` ALTER COLUMN `permission_id` RESTART WITH 55;
