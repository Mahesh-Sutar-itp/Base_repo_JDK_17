-- Trebol Identity service - owned schema.
--
-- Extracted VERBATIM from the monolith's schema-mariadb.sql. Column types, index names,
-- unique-key names, foreign-key names, engine, charset and collation are unchanged, because the
-- schema is as much a contract as the HTTP surface is.
--
-- This service owns these seven tables EXCLUSIVELY and reads no other service's schema:
--     people, app_user_roles, app_permissions, app_users, app_user_role_permissions,
--     customers, salespeople
--
-- FOREIGN KEY DIRECTION - every key below points INSIDE this schema:
--     FK_USER_PERSON_ID        app_users                 -> people
--     FK_USER_UROLE_ID         app_users                 -> app_user_roles
--     FK_UROLEPERMS_PERM_ID    app_user_role_permissions -> app_permissions
--     FK_UROLEPERMS_UROLE_ID   app_user_role_permissions -> app_user_roles
--     FK_CUSTOM_PERSON_ID      customers                 -> people
--     FK_SPERSON_PERSON_ID     salespeople               -> people
--
-- The only keys crossing the boundary point INWARD, from the order domain, and are cut in Phase 3:
--     FK_ORDER_CUSTOM_ID       orders -> customers
--     FK_ORDER_SPERSON         orders -> salespeople
-- Nothing in this file references them; they remain in the monolith until Phase 4.
--
-- THREE DELIBERATE ASYMMETRIES. Do not harmonise them - each is current observable behaviour and
-- changing any one of them is a behavioural change (G1, register items D24 and D25):
--     customers.person_id    IS unique      (UK_CUSTOM_PERSON_ID)   -> one customer per person
--     salespeople.person_id  is NOT unique                          -> many salespeople per person
--     app_users.person_id    is NOT unique, and is NULLABLE         -> an account needs no profile,
--                                                                     and BR-USER-004 is enforced
--                                                                     only in application code
--
-- UQ_UROLEPERMS_UROLE_PERM is the Phase 0 fix for register item D17 and must keep its name.
--
-- LOAD ORDER matters: app_user_roles and app_permissions before app_users and
-- app_user_role_permissions; people before app_users, customers and salespeople.

SET NAMES utf8;
SET time_zone = '+00:00';
SET foreign_key_checks = 0;
SET sql_mode = 'NO_AUTO_VALUE_ON_ZERO';

SET NAMES utf8mb4;


DROP TABLE IF EXISTS `people`;
CREATE TABLE `people` (
  `person_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `person_email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `person_first_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `person_id_number` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `person_last_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `person_phone1` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `person_phone2` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`person_id`),
  UNIQUE KEY `UK_PERSON_ID_NO` (`person_id_number`),
  KEY `IDX_PERSON_ID_NO` (`person_id_number`),
  KEY `IDX_PERSON_FIRSTNAME` (`person_first_name`),
  KEY `IDX_PERSON_LASTNAME` (`person_last_name`),
  KEY `IDX_PERSON_EMAIL` (`person_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `app_user_roles`;
CREATE TABLE `app_user_roles` (
  `user_role_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_role_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`user_role_id`),
  UNIQUE KEY `UK_UROLE_NAME` (`user_role_name`),
  KEY `IDX_UROLE_NAME` (`user_role_name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `app_permissions`;
CREATE TABLE `app_permissions` (
  `permission_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `permission_code` varchar(25) COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission_description` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`permission_id`),
  UNIQUE KEY `UK_PERM_CODE` (`permission_code`)
) ENGINE=InnoDB AUTO_INCREMENT=54 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `app_users`;
CREATE TABLE `app_users` (
  `user_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_password` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `person_id` bigint(20) DEFAULT NULL,
  `user_role_id` bigint(20) NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK_USER_NAME` (`user_name`),
  KEY `IDX_USER_NAME` (`user_name`),
  CONSTRAINT `FK_USER_PERSON_ID` FOREIGN KEY (`person_id`) REFERENCES `people` (`person_id`),
  CONSTRAINT `FK_USER_UROLE_ID` FOREIGN KEY (`user_role_id`) REFERENCES `app_user_roles` (`user_role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `app_user_role_permissions`;
CREATE TABLE `app_user_role_permissions` (
  `user_role_permission_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `permission_id` bigint(20) NOT NULL,
  `user_role_id` bigint(20) NOT NULL,
  PRIMARY KEY (`user_role_permission_id`),
  UNIQUE KEY `UQ_UROLEPERMS_UROLE_PERM` (`user_role_id`,`permission_id`),
  CONSTRAINT `FK_UROLEPERMS_PERM_ID` FOREIGN KEY (`permission_id`) REFERENCES `app_permissions` (`permission_id`),
  CONSTRAINT `FK_UROLEPERMS_UROLE_ID` FOREIGN KEY (`user_role_id`) REFERENCES `app_user_roles` (`user_role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=99 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `customers`;
CREATE TABLE `customers` (
  `customer_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `person_id` bigint(20) NOT NULL,
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `UK_CUSTOM_PERSON_ID` (`person_id`),
  CONSTRAINT `FK_CUSTOM_PERSON_ID` FOREIGN KEY (`person_id`) REFERENCES `people` (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `salespeople`;
CREATE TABLE `salespeople` (
  `salesperson_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `person_id` bigint(20) NOT NULL,
  PRIMARY KEY (`salesperson_id`),
  CONSTRAINT `FK_SPERSON_PERSON_ID` FOREIGN KEY (`person_id`) REFERENCES `people` (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
