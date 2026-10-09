variable "name" {
  type = string
}

variable "api_origin_domain" {
  description = "Global API hostname (the Traffic Manager FQDN). Empty = SPA only."
  type        = string
  default     = ""
}

variable "api_origin_protocol" {
  description = "http-only while the cluster ingresses serve plain HTTP."
  type        = string
  default     = "http-only"
}
