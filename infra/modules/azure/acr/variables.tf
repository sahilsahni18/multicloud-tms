variable "name" {
  description = "Globally unique, 5-50 lowercase letters and digits."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]{5,50}$", var.name))
    error_message = "ACR names are 5-50 lowercase letters and digits."
  }
}

variable "location" {
  type = string
}

variable "resource_group_name" {
  type = string
}

variable "sku" {
  description = "Azure for Students includes one Standard registry."
  type        = string
  default     = "Standard"
}

variable "tags" {
  type    = map(string)
  default = {}
}
