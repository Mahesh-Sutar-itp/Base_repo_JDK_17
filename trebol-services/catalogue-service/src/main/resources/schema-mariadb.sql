-- Catalogue service schema. Extracted VERBATIM from the monolith's schema-mariadb.sql:
-- the DDL, column types, collations, index names and constraint names are unchanged, because
-- they are the contract this port must preserve.
--
-- Seven tables, owned exclusively by this service: app_params, images, product_lists,
-- product_categories, products, product_images, product_list_items.
--
-- Every foreign key below points at a table in this same schema. No catalogue table
-- references people, customers, orders, billing or any other service's table. The only
-- cross-boundary foreign key in the original schema points INWARD - order_details.product_id
-- -> products(product_id) ON DELETE SET NULL - and is cut in Phase 3, when Orders is
-- extracted and takes ownership of order_details.
--
-- Load order matters: product_categories before products (FK), images and products before
-- product_images, product_lists and products before product_list_items.

SET NAMES utf8;
SET time_zone = '+00:00';
SET foreign_key_checks = 0;
SET sql_mode = 'NO_AUTO_VALUE_ON_ZERO';

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `app_params`;
CREATE TABLE `app_params` (
  `param_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `param_category` varchar(25) COLLATE utf8mb4_unicode_ci NOT NULL,
  `param_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `param_value` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`param_id`),
  UNIQUE KEY `UK_PARAM` (`param_category`,`param_name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `images`;
CREATE TABLE `images` (
  `image_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `image_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_filename` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`image_id`),
  UNIQUE KEY `UK_IMG_CODE` (`image_code`),
  UNIQUE KEY `UK_IMG_FILENAME` (`image_filename`),
  UNIQUE KEY `UK_IMG_URL` (`image_url`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `product_lists`;
CREATE TABLE `product_lists` (
  `product_list_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_list_code` varchar(25) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_list_disabled` bit(1) NOT NULL,
  `product_list_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`product_list_id`),
  UNIQUE KEY `UK_PLIST_NAME` (`product_list_name`),
  UNIQUE KEY `UK_PLIST_CODE` (`product_list_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `product_categories`;
CREATE TABLE `product_categories` (
  `product_category_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_category_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_category_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_product_category_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`product_category_id`),
  UNIQUE KEY `UK_PROD_CAT_CODE` (`product_category_code`),
  UNIQUE KEY `UK_PROD_CAT_IF_CHILD` (`parent_product_category_id`,`product_category_name`),
  CONSTRAINT `FK_PROD_CAT_PARENT` FOREIGN KEY (`parent_product_category_id`) REFERENCES `product_categories` (`product_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `products`;
CREATE TABLE `products` (
  `product_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_description` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_price` int(11) NOT NULL,
  `product_stock_critical` int(11) NOT NULL,
  `product_stock_current` int(11) NOT NULL,
  `product_category_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `UK_PRODUCT_CODE` (`product_code`),
  UNIQUE KEY `UK_PRODUCT_NAME` (`product_name`),
  KEY `IDX_PRODUCT_NAME` (`product_name`),
  CONSTRAINT `FK_PRODUCT_CAT_ID` FOREIGN KEY (`product_category_id`) REFERENCES `product_categories` (`product_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `product_images`;
CREATE TABLE `product_images` (
  `product_image_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `image_id` bigint(20) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  PRIMARY KEY (`product_image_id`),
  CONSTRAINT `FK_PIMGS_IMAGE_ID` FOREIGN KEY (`image_id`) REFERENCES `images` (`image_id`),
  CONSTRAINT `FK_PIMGS_PROD_ID` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


DROP TABLE IF EXISTS `product_list_items`;
CREATE TABLE `product_list_items` (
  `product_list_item_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_list_id` bigint(20) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  PRIMARY KEY (`product_list_item_id`),
  CONSTRAINT `FK_PLIST_ITEM_PARENT_ID` FOREIGN KEY (`product_list_id`) REFERENCES `product_lists` (`product_list_id`),
  CONSTRAINT `FK_PLIST_ITEM_PROD_ID` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

