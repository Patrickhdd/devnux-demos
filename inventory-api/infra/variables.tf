variable "project_id" {
  description = "Google Cloud project to deploy into."
  type        = string
}

variable "region" {
  description = "Region for Cloud Run, Cloud SQL and Artifact Registry."
  type        = string
  default     = "us-central1"
}

variable "image_tag" {
  description = "Tag of the inventory-service image in Artifact Registry, usually the git commit SHA."
  type        = string
  default     = "latest"
}

variable "api_keys" {
  description = "Value for INVENTORY_API_KEYS: comma-separated name:key:read+write entries. Stored in Secret Manager."
  type        = string
  sensitive   = true
}

variable "db_tier" {
  description = "Cloud SQL machine tier."
  type        = string
  default     = "db-f1-micro"
}

variable "max_instances" {
  description = "Upper limit for Cloud Run instances."
  type        = number
  default     = 5
}
