variable "github_repository" {
  description = "owner/name of the GitHub repository allowed to assume the cloud roles."
  type        = string

  validation {
    condition     = can(regex("^[^/]+/[^/]+$", var.github_repository))
    error_message = "Use the form owner/name, e.g. octocat/multicloud-tms."
  }
}

variable "aws_region" {
  description = "Region for the S3 state bucket."
  type        = string
  default     = "us-east-1"
}

variable "azure_subscription_id" {
  description = "Azure subscription (az account show --query id -o tsv)."
  type        = string
}

variable "azure_location" {
  description = "Region for the state storage account."
  type        = string
  default     = "eastus"
}
