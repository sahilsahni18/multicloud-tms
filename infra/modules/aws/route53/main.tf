# DNS records in an existing hosted zone (needs a domain you own; the zone costs
# US$0.50/month). Failover routing itself is done by Traffic Manager, because
# the AWS Free plan blocks Route 53 failover records.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

data "aws_route53_zone" "this" {
  name = var.zone_name
}

resource "aws_route53_record" "app" {
  zone_id = data.aws_route53_zone.this.zone_id
  name    = "${var.app_subdomain}.${var.zone_name}"
  type    = "A"

  alias {
    name                   = var.cloudfront_domain_name
    zone_id                = var.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "api" {
  zone_id = data.aws_route53_zone.this.zone_id
  name    = "${var.api_subdomain}.${var.zone_name}"
  type    = "CNAME"
  ttl     = 60
  records = [var.api_target_fqdn]
}
