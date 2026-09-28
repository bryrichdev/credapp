# The default VPC is free and already has public subnets. The instance takes no inbound
# traffic at all: Cloudflare's tunnel and SSM both connect outward, so the security group has
# no ingress rules and there's no SSH key.
#
# The public IPv4 address ($3.65 a month) is the only way out without a NAT gateway ($32 a
# month). GitHub, where the bootstrap downloads Compose and age, doesn't serve IPv6 yet.

data "aws_vpc" "default" {
  default = true
}

# Not every zone offers every Graviton type, so only look at zones that do.
data "aws_ec2_instance_type_offerings" "here" {
  location_type = "availability-zone"

  filter {
    name   = "instance-type"
    values = [var.instance_type]
  }
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }

  filter {
    name   = "default-for-az"
    values = ["true"]
  }

  filter {
    name   = "availability-zone"
    values = data.aws_ec2_instance_type_offerings.here.locations
  }
}

resource "aws_security_group" "prod" {
  name        = "credcloud-prod"
  description = "CredCloud production: outbound only"
  vpc_id      = data.aws_vpc.default.id

  egress {
    description      = "Cloudflare tunnel, SSM, ECR, S3, GitHub, SMTP"
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}
