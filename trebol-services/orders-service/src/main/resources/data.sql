-- Trebol Orders service - seed data.
--
-- Phase 3. The three seeded reference tables this service owns, taken VERBATIM from the monolith's
-- data.sql, including the identity-restart statements.
--
-- TWELVE order statuses, not eleven. The transformation definition and the defect register both say
-- eleven; this file seeds twelve and restarts the sequence at 13. The correction is recorded in
-- defect-register-rulings.md and asserted by OrderStatusTransitionsTest.
--
-- FOUR of the twelve are unreachable by any code path - Returned (-6), Delivery Failed (-5),
-- Delivery Cancelled (-4) and Delivery On Route (05). Register item D10 ruled that the ROWS are
-- preserved exactly while the TRANSITIONS into them are rejected, which is what
-- OrderStatusTransitions enforces. The data stays; the transitions do not.
--
-- Nothing else is seeded here. In particular NO permission rows: the 54-code authority catalogue
-- and its grant matrix belong to the Identity service (Phase 2), and this service only consumes the
-- codes through the published AuthorityCodes artefact. The eight order_statuses:* and orders:*
-- permissions are seeded THERE, not here.

INSERT INTO `order_statuses`
(`order_status_id`, `order_status_code`, `order_status_name`)
VALUES
(01, -6, 'Returned'),
(02, -5, 'Delivery Failed'),
(03, -4, 'Delivery Cancelled'),
(04, -3, 'Rejected'),
(05, -2, 'Payment Failed'),
(06, -1, 'Payment Cancelled'),
(07, 01, 'Pending'),
(08, 02, 'Payment Started'),
(09, 03, 'Paid, Unconfirmed'),
(10, 04, 'Paid, Confirmed'),
(11, 05, 'Delivery On Route'),
(12, 06, 'Delivery Complete');

INSERT INTO `billing_types`
(`billing_type_id`, `billing_type_name`)
VALUES
(01, 'Bill'),
(02, 'Enterprise Invoice');

INSERT INTO `payment_types`
(`payment_type_id`, `payment_type_name`)
VALUES
(01, 'WebPay Plus');

ALTER TABLE `order_statuses` ALTER COLUMN `order_status_id` RESTART WITH 13;
ALTER TABLE `billing_types` ALTER COLUMN `billing_type_id` RESTART WITH 3;
ALTER TABLE `payment_types` ALTER COLUMN `payment_type_id` RESTART WITH 2;
