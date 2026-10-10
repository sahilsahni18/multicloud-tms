variable "environment" {
  type    = string
  default = "dev"
}

variable "primary_region" {
  type    = string
  default = "us-east-1"
}

variable "standby_region" {
  type    = string
  default = "us-west-2"
}

variable "primary_cidr" {
  type    = string
  default = "10.10.0.0/16"
}

variable "standby_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "cluster_count" {
  description = "Clusters per region (Deployment Portal: 1-5). Each one costs US$0.10/h."
  type        = number
  default     = 1
}

variable "kubernetes_version" {
  type    = string
  default = "1.33"
}

# The AWS Free plan only launches Free-plan-eligible types
# (aws ec2 describe-instance-types --filters Name=free-tier-eligible,Values=true).
# c7i-flex.large matches t3.medium (2 vCPU / 4 GiB) and is eligible.
variable "node_instance_type" {
  type    = string
  default = "c7i-flex.large"
}

variable "admin_role_arns" {
  description = "Extra cluster admins: the GitHub Actions role from infra/bootstrap."
  type        = list(string)
  default     = []
}

variable "traffic_manager_dns_name" {
  description = "Same value as in the Azure stack; the API is <this>.trafficmanager.net."
  type        = string
}

variable "domain_name" {
  description = "Existing Route 53 zone for app./api. records. Empty = use the CloudFront and Traffic Manager hostnames."
  type        = string
  default     = ""
}

variable "enable_cloudfront" {
  description = "S3 + CloudFront for the SPA. Needs an AWS account verified for CloudFront (new accounts are not)."
  type        = bool
  default     = false
}

variable "enable_replicas" {
  description = "Cross-region RDS read replica. Blocked on the AWS Free plan; paid accounts only."
  type        = bool
  default     = false
}
