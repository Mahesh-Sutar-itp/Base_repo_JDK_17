# Step 3: secrets for the app and the database.
#
# Always on: $0.40 per secret per month (about $0.80 for both), kept while the lab is off
# so the database password and JWT key stay the same between practice sessions.
#
# The generated values are also held in the Terraform state file. That file is in the
# private, encrypted, HTTPS-only S3 bucket from Step 0. (Future improvement: write-only
# arguments so the values never reach state, plus automatic rotation.)

############################
# Database credentials
############################
resource "random_password" "db" {
  length  = 32
  special = true
  # RDS MariaDB does not allow / @ " or spaces in the master password
  override_special = "!#$%^&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret" "db" {
  name        = "${var.project}/db-credentials"
  description = "MariaDB master user for the Trebol POC (used by RDS, RDS Proxy and the app)"

  # 0 = delete immediately on destroy, so the same name can be reused straight away
  recovery_window_in_days = 0

  tags = { Name = "${var.project}-db-credentials" }
}

# RDS Proxy expects a JSON secret with "username" and "password".
# ECS passes single keys to the app, e.g. <secret-arn>:password:: becomes DB_PASSWORD.
resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    engine   = "mariadb"
    username = var.db_username
    password = random_password.db.result
    port     = 3306
    dbname   = var.db_name
  })
}

############################
# JWT signing key
############################
# The app turns this text straight into an HMAC key (Keys.hmacShaKeyFor), so it must be
# at least 32 characters; 64 letters and digits gives a 512-bit key.
resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${var.project}/jwt-secret-key"
  description             = "JWT signing key for the Trebol POC app (JWT_SECRET_KEY)"
  recovery_window_in_days = 0

  tags = { Name = "${var.project}-jwt-secret-key" }
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_password.jwt.result
}
