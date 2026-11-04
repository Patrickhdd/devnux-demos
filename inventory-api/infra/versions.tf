terraform {
  required_version = ">= 1.6"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Keep state in a GCS bucket you own, for example:
  # backend "gcs" { bucket = "my-terraform-state" prefix = "inventory-service" }
}

provider "google" {
  project = var.project_id
  region  = var.region
}
