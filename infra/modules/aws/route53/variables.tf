variable "zone_name" {
  description = "Existing hosted zone, e.g. example.com."
  type        = string
}

variable "app_subdomain" {
  type    = string
  default = "app"
}

variable "api_subdomain" {
  type    = string
  default = "api"
}

variable "cloudfront_domain_name" {
  type = string
}

variable "cloudfront_hosted_zone_id" {
  type = string
}

variable "api_target_fqdn" {
  description = "Traffic Manager FQDN, e.g. trackflow-dev.trafficmanager.net."
  type        = string
}
