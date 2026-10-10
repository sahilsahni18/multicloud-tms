variable "environment" {
  type    = string
  default = "dev"
}

variable "subscription_id" {
  description = "null = take it from ARM_SUBSCRIPTION_ID or the az CLI login."
  type        = string
  default     = null
}

# Azure for Students limits regions by policy (here: uaenorth, eastasia,
# indiasouthcentral, koreacentral, malaysiawest). East Asia is the only one of
# those with Static Web Apps; Korea Central is the nearest second region.
variable "primary_location" {
  description = "Must be allowed by your subscription's region policy."
  type        = string
  default     = "eastasia"
}

variable "standby_location" {
  type    = string
  default = "koreacentral"
}

variable "static_web_app_location" {
  type    = string
  default = "eastasia"
}

variable "cluster_count" {
  description = "Clusters per region (Deployment Portal: 1-5)."
  type        = number
  default     = 1
}

variable "node_vm_size" {
  type    = string
  default = "Standard_B2als_v2"
}

variable "traffic_manager_dns_name" {
  description = "Globally unique; same value as in the AWS stack."
  type        = string
}

variable "traffic_manager_endpoints" {
  description = "Cluster ingress hostnames/IPs, added after the Kubernetes deploy (Step 8)."
  type = list(object({
    name     = string
    target   = string
    priority = number
    location = optional(string)
    enabled  = optional(bool, true)
  }))
  default = []
}
