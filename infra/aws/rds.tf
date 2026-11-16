# Step 5: database (RDS for MariaDB) and connection pooling (RDS Proxy).
#
# Lab only: the DB instance and the proxy exist only while lab_on = true.
#   Practice (default):   db.t3.micro, Single-AZ         ~ $0.017/h + storage
#   Load test and demo:   Multi-AZ (bigger class only if the cloud team allows it)
#   (The organization's SCP denies creating db.t4g.* ; db.t3.micro is allowed.)
#   RDS Proxy:            billed per vCPU of the DB, minimum 2 vCPU  ~ $0.030/h
# Nothing is kept when the lab goes off (no final snapshot, no backups): Flyway
# recreates the schema and demo data when the app starts in the next session.
#
# Always on (free): the DB subnet group and the IAM role the proxy uses.

locals {
  # Needs BOTH switches: lab_on (hourly resources) and db_enabled.
  # db_enabled (default true) lets the lab run without a database if ever needed.
  db_count = var.lab_on && var.db_enabled ? 1 : 0
}

############################
# Always on (free)
############################
resource "aws_db_subnet_group" "main" {
  name        = "${var.project}-db-subnets"
  description = "Private DB subnets in both zones (no internet route)"
  subnet_ids  = aws_subnet.db[*].id
  tags        = { Name = "${var.project}-db-subnets" }
}

# RDS Proxy logs in to MariaDB with the credentials in Secrets Manager (Step 3),
# so it needs permission to read that one secret, and nothing else.
data "aws_iam_policy_document" "proxy_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["rds.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "proxy" {
  name               = "${var.project}-rds-proxy-role"
  description        = "Lets RDS Proxy read the DB credentials secret"
  assume_role_policy = data.aws_iam_policy_document.proxy_assume.json
  tags               = { Name = "${var.project}-rds-proxy-role" }
}

data "aws_iam_policy_document" "proxy_secret" {
  statement {
    sid       = "ReadDbSecret"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.db.arn]
  }

  # The secret is encrypted with the AWS-managed key; allow decrypting only via Secrets Manager.
  statement {
    sid       = "DecryptViaSecretsManager"
    actions   = ["kms:Decrypt"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["secretsmanager.${var.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "proxy_secret" {
  name   = "read-db-secret"
  role   = aws_iam_role.proxy.id
  policy = data.aws_iam_policy_document.proxy_secret.json
}

############################
# Lab only: MariaDB
############################
resource "aws_db_instance" "main" {
  count = local.db_count

  identifier     = "${var.project}-db"
  engine         = "mariadb"
  engine_version = var.db_engine_version # same major version as docker-compose (mariadb:10.11)
  instance_class = var.db_instance_class
  multi_az       = var.db_multi_az

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result # same value as the Secrets Manager secret

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false

  # Lab data is throwaway: no backups, no snapshot on delete, quick to remove
  backup_retention_period  = 0
  skip_final_snapshot      = true
  delete_automated_backups = true
  deletion_protection      = false
  apply_immediately        = true

  auto_minor_version_upgrade   = true
  performance_insights_enabled = false
  monitoring_interval          = 0

  tags = { Name = "${var.project}-db" }
}

############################
# Lab only: RDS Proxy
############################
resource "aws_db_proxy" "main" {
  count = local.db_count

  name           = "${var.project}-proxy"
  engine_family  = "MYSQL" # MariaDB uses the MySQL engine family
  role_arn       = aws_iam_role.proxy.arn
  vpc_subnet_ids = aws_subnet.db[*].id

  vpc_security_group_ids = [aws_security_group.proxy.id]

  # Traffic stays inside the VPC. Future improvement: require TLS and turn on
  # TLS in the app's JDBC URL.
  require_tls         = false
  idle_client_timeout = 1800

  auth {
    auth_scheme = "SECRETS"
    iam_auth    = "DISABLED"
    secret_arn  = aws_secretsmanager_secret.db.arn
    # MariaDB supports only mysql_native_password; the proxy's newer default
    # (caching_sha2_password) is rejected when the MariaDB target is registered.
    client_password_auth_type = "MYSQL_NATIVE_PASSWORD"
  }

  tags = { Name = "${var.project}-proxy" }

  depends_on = [aws_iam_role_policy.proxy_secret]
}

resource "aws_db_proxy_default_target_group" "main" {
  count         = local.db_count
  db_proxy_name = aws_db_proxy.main[0].name

  connection_pool_config {
    max_connections_percent      = 90 # leave headroom for admin connections
    max_idle_connections_percent = 50
    connection_borrow_timeout    = 120 # seconds a request waits for a free connection
  }
}

resource "aws_db_proxy_target" "main" {
  count                  = local.db_count
  db_proxy_name          = aws_db_proxy.main[0].name
  target_group_name      = aws_db_proxy_default_target_group.main[0].name
  db_instance_identifier = aws_db_instance.main[0].identifier
}
