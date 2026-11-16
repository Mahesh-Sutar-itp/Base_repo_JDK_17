# Step 1: network.
#
# Three tiers of subnets in each of two Availability Zones:
#   public  10.20.0.x / 10.20.1.x    NAT gateway only (nothing else is reachable from the internet)
#   app     10.20.10.x / 10.20.11.x  internal load balancer and ECS tasks; outbound internet via NAT
#   db      10.20.20.x / 10.20.21.x  RDS and RDS Proxy; no internet route at all
# Users reach the app only through CloudFront, which connects privately to the internal load balancer.

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true # needed for RDS and VPC endpoints to resolve inside the VPC

  tags = { Name = "${var.project}-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.project}-igw" }
}

############################
# Subnets
############################
resource "aws_subnet" "public" {
  count             = var.az_count
  vpc_id            = aws_vpc.main.id
  availability_zone = local.azs[count.index]
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index) # 10.20.0.0/24, 10.20.1.0/24

  tags = { Name = "${var.project}-public-${local.azs[count.index]}", Tier = "public" }
}

resource "aws_subnet" "app" {
  count             = var.az_count
  vpc_id            = aws_vpc.main.id
  availability_zone = local.azs[count.index]
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, 10 + count.index) # 10.20.10.0/24, 10.20.11.0/24

  tags = { Name = "${var.project}-app-${local.azs[count.index]}", Tier = "app" }
}

resource "aws_subnet" "db" {
  count             = var.az_count
  vpc_id            = aws_vpc.main.id
  availability_zone = local.azs[count.index]
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, 20 + count.index) # 10.20.20.0/24, 10.20.21.0/24

  tags = { Name = "${var.project}-db-${local.azs[count.index]}", Tier = "db" }
}

############################
# NAT gateway (outbound internet for ECS tasks: pulling images, calling AWS APIs)
############################
locals {
  # No NAT gateway at all while the lab is off: it is billed every hour it exists, even with no traffic
  nat_count = var.lab_on ? (var.single_nat_gateway ? 1 : var.az_count) : 0
}

resource "aws_eip" "nat" {
  count  = local.nat_count
  domain = "vpc"
  tags   = { Name = "${var.project}-nat-eip-${count.index}" }
}

resource "aws_nat_gateway" "main" {
  count         = local.nat_count
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id
  tags          = { Name = "${var.project}-nat-${local.azs[count.index]}" }

  depends_on = [aws_internet_gateway.main]
}

############################
# Route tables
############################
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }
  tags = { Name = "${var.project}-public-rt" }
}

resource "aws_route_table_association" "public" {
  count          = var.az_count
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# One app route table per zone, each pointing at its zone's NAT (or the single shared NAT)
resource "aws_route_table" "app" {
  count  = var.az_count
  vpc_id = aws_vpc.main.id

  # The internet route is a separate aws_route below: an inline route block that
  # disappears is NOT removed from AWS by Terraform, which left a dead route behind.
  tags = { Name = "${var.project}-app-rt-${local.azs[count.index]}" }
}

# Internet route for the app subnets, only while the lab is on (and therefore a NAT gateway exists)
resource "aws_route" "app_nat" {
  count                  = local.nat_count > 0 ? var.az_count : 0
  route_table_id         = aws_route_table.app[count.index].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.main[var.single_nat_gateway ? 0 : count.index].id
}

resource "aws_route_table_association" "app" {
  count          = var.az_count
  subnet_id      = aws_subnet.app[count.index].id
  route_table_id = aws_route_table.app[count.index].id
}

# Database subnets: local VPC traffic only, no route to the internet
resource "aws_route_table" "db" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${var.project}-db-rt" }
}

resource "aws_route_table_association" "db" {
  count          = var.az_count
  subnet_id      = aws_subnet.db[count.index].id
  route_table_id = aws_route_table.db.id
}

############################
# S3 gateway endpoint (free): ECR image layers are stored in S3, so image pulls
# skip the NAT gateway and its per-GB data charge
############################
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = aws_route_table.app[*].id
  tags              = { Name = "${var.project}-s3-endpoint" }
}
