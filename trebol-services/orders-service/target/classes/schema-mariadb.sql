-- Trebol Orders service - owned schema.
--
-- Phase 3 of the microservices conversion. Nine tables, taken VERBATIM from the monolith's
-- schema-mariadb.sql: same column types, same index names, same unique-key names, same engine,
-- charset and collation. A port, not a redesign.
--
-- THREE FOREIGN KEYS ARE CUT, because their parents belong to other services:
--
--   FK_ORDER_CUSTOM_ID        orders(customer_id)       -> customers(customer_id)      [Identity]
--   FK_ORDER_SPERSON          orders(salesperson_id)    -> salespeople(salesperson_id) [Identity]
--   FK_ORDER_DETAIL_PROD_ID   order_details(product_id) -> products(product_id)        [Catalogue]
--
-- The COLUMNS are unchanged in name, type and nullability, so no data migrates - only the
-- constraints go. Referential integrity across those three edges becomes an application concern,
-- which is the cost the approved target accepts.
--
-- Note FK_ORDER_DETAIL_PROD_ID was already ON DELETE SET NULL and already
-- ConstraintMode.NO_CONSTRAINT at the ORM level, so that edge was a soft reference in practice
-- before this phase. The other two were real.
--
-- Also note FK_ORDER_DETAIL_PARENT_ID referenced a table named `sales` in the monolith's DDL - a
-- legacy name the entity never used. It is corrected to `orders` here, because leaving it would
-- reference a table this schema does not create. That is the ONE deviation from verbatim, and it is
-- recorded in PHASE-3-EXIT.md.

DROP TABLE IF EXISTS `addresses`;
CREATE TABLE `addresses` (
  `address_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `address_city` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `address_first_line` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `address_municipality` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `address_notes` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_postal_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_second_line` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`address_id`),
  UNIQUE KEY `UK_ADDR` (`address_city`,`address_municipality`,`address_first_line`,`address_second_line`,`address_postal_code`,`address_notes`),
  KEY `IDX_ADDR_FIRST_LINE` (`address_first_line`),
  KEY `IDX_ADDR_SECOND_LINE` (`address_second_line`),
  KEY `IDX_ADDR_POSTAL_CODE` (`address_postal_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `billing_companies`;
CREATE TABLE `billing_companies` (
  `billing_company_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `billing_company_id_number` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `billing_company_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`billing_company_id`),
  UNIQUE KEY `UK_BILL_CY_ID_NO` (`billing_company_id_number`),
  UNIQUE KEY `UK_BILL_CY_NAME` (`billing_company_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `billing_types`;
CREATE TABLE `billing_types` (
  `billing_type_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `billing_type_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`billing_type_id`),
  UNIQUE KEY `UK_BILL_TYP_NAME` (`billing_type_name`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `payment_types`;
CREATE TABLE `payment_types` (
  `payment_type_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `payment_type_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`payment_type_id`),
  UNIQUE KEY `UK_PAYMT_NAME` (`payment_type_name`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `order_statuses`;
CREATE TABLE `order_statuses` (
  `order_status_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_status_code` int(11) NOT NULL,
  `order_status_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`order_status_id`),
  UNIQUE KEY `UK_ORDER_STATUS_CODE` (`order_status_code`),
  UNIQUE KEY `UK_ORDER_STATUS_NAME` (`order_status_name`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `shippers`;
CREATE TABLE `shippers` (
  `shipper_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `shipper_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`shipper_id`),
  UNIQUE KEY `UK_SHIPPER_NAME` (`shipper_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Register item D11: the order-status notification outbox.
--
-- Written in the SAME transaction as a status transition, which is what makes it an outbox rather
-- than a queue: either the order moved and the notification is recorded, or neither happened. A
-- scheduled dispatcher drains it with bounded retry. No broker is involved - ADR-003 accepted that
-- no event infrastructure exists anywhere in this codebase and that this would be built from
-- nothing.
--
-- order_id is a plain identifier, NOT a foreign key: this table is infrastructure, and an FK here
-- would drag the outbox into the order aggregate's delete semantics for no benefit. It is also one
-- fewer foreign key for Phase 3's extraction to reason about.
DROP TABLE IF EXISTS `order_mail_outbox`;
CREATE TABLE `order_mail_outbox` (
  `outbox_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) NOT NULL,
  `outbox_recipient_kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `outbox_order_status_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `outbox_attempts` int(11) NOT NULL DEFAULT 0,
  `outbox_created_at` datetime NOT NULL,
  `outbox_dispatched_at` datetime DEFAULT NULL,
  `outbox_last_error` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`outbox_id`),
  KEY `IDX_OUTBOX_DISPATCHED_AT` (`outbox_dispatched_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `order_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_date` datetime(6) NOT NULL,
  `order_net_value` int(11) NOT NULL,
  `order_taxes_value` int(11) NOT NULL,
  `order_total_items` int(11) NOT NULL,
  `order_total_value` int(11) NOT NULL,
  `order_transaction_token` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  -- Register item D4: per-order payment-callback nonce. Additive and nullable; no existing
  -- column is touched. Generated at Payment Started, embedded in the returnUrl handed to
  -- Webpay, and required on the callback.
  `order_callback_nonce` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `order_transport_value` int(11) NOT NULL,
  `billing_address_id` bigint(20) DEFAULT NULL,
  `billing_company_id` bigint(20) DEFAULT NULL,
  `billing_type_id` bigint(20) NOT NULL,
  `customer_id` bigint(20) NOT NULL,
  `payment_type_id` bigint(20) NOT NULL,
  `salesperson_id` bigint(20) DEFAULT NULL,
  `shipper_id` bigint(20) DEFAULT NULL,
  `shipping_address_id` bigint(20) DEFAULT NULL,
  `order_status_id` bigint(20) NOT NULL,
  PRIMARY KEY (`order_id`),
  KEY `IDX_ORDER_DATE` (`order_date`),
  KEY `IDX_ORDER_TOKEN` (`order_transaction_token`),
  CONSTRAINT `FK_ORDER_BILL_TYP_ID` FOREIGN KEY (`billing_type_id`) REFERENCES `billing_types` (`billing_type_id`),
  CONSTRAINT `FK_ORDER_PAYMT_TYP_ID` FOREIGN KEY (`payment_type_id`) REFERENCES `payment_types` (`payment_type_id`),
  CONSTRAINT `FK_ORDER_STATUS_ID` FOREIGN KEY (`order_status_id`) REFERENCES `order_statuses` (`order_status_id`),
  CONSTRAINT `FK_ORDER_BILL_CY_ID` FOREIGN KEY (`billing_company_id`) REFERENCES `billing_companies` (`billing_company_id`),
  CONSTRAINT `FK_ORDER_ADDR_BILL` FOREIGN KEY (`billing_address_id`) REFERENCES `addresses` (`address_id`),
  CONSTRAINT `FK_ORDER_SHIPPER_ID` FOREIGN KEY (`shipper_id`) REFERENCES `shippers` (`shipper_id`),
  CONSTRAINT `FK_ORDER_ADDR_SHIP` FOREIGN KEY (`shipping_address_id`) REFERENCES `addresses` (`address_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `order_details`;
CREATE TABLE `order_details` (
  `order_detail_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_detail_unit_value` int(11) NOT NULL,
  `order_detail_units` int(11) NOT NULL,
  `order_detail_description` varchar(260) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_id` bigint(20) NULL,
  `order_id` bigint(20) NOT NULL,
  PRIMARY KEY (`order_detail_id`),
  CONSTRAINT `FK_ORDER_DETAIL_PARENT_ID` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

