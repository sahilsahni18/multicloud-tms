variable "name" {
  type = string
}

variable "dns_name" {
  description = "Globally unique; the API becomes <dns_name>.trafficmanager.net."
  type        = string
}

variable "resource_group_name" {
  type = string
}

variable "endpoints" {
  description = "Cluster ingress hostnames/IPs in failover order (1 = primary)."
  type = list(object({
    name     = string
    target   = string
    priority = number
    location = optional(string)
    enabled  = optional(bool, true)
  }))
  default = []
}

variable "tags" {
  type    = map(string)
  default = {}
}
