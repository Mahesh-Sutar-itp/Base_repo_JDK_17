-- Catalogue service seed data. The four `company` rows of app_params, extracted verbatim from
-- the monolith's data.sql, plus the identity-generator restart that MUST accompany them:
-- without it the first inserted parameter collides on param_id (BR-COMPANY-012).
--
-- These four names are the only ones CompanyServiceImpl recognises (BR-COMPANY-003); the other
-- seeded tables in the monolith's data.sql - order statuses, billing types, payment types,
-- roles, permissions and users - belong to Identity and Orders and are deliberately absent.

INSERT INTO `app_params`
(`param_id`, `param_category`, `param_name`, `param_value`)
VALUES
(01, 'company', 'name', 'Trébol Demo'),
(02, 'company', 'description', 'This is a demostration of integrated eCommerce system Trébol'),
(03, 'company', 'bannerImageURL', 'https://fakeimg.pl/400x150'),
(04, 'company', 'logoImageURL', 'https://fakeimg.pl/250');

ALTER TABLE `app_params` ALTER COLUMN `param_id` RESTART WITH 5;
