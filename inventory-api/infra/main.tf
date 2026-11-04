locals {
  name  = "inventory-service"
  image = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}/${local.name}:${var.image_tag}"
}

resource "google_project_service" "apis" {
  for_each = toset([
    "run.googleapis.com",
    "sqladmin.googleapis.com",
    "artifactregistry.googleapis.com",
    "secretmanager.googleapis.com",
  ])
  service            = each.value
  disable_on_destroy = false
}

# ---- Container images ----

resource "google_artifact_registry_repository" "images" {
  repository_id = "inventory"
  location      = var.region
  format        = "DOCKER"
  depends_on    = [google_project_service.apis]
}

# ---- Database ----

resource "google_sql_database_instance" "db" {
  name             = "${local.name}-db"
  database_version = "POSTGRES_16"
  region           = var.region

  settings {
    tier              = var.db_tier
    availability_type = "ZONAL"
    disk_autoresize   = true

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
    }

    ip_configuration {
      ipv4_enabled = true # reached only through the Cloud SQL Auth Proxy sidecar, which uses IAM, not open networks
      ssl_mode     = "ENCRYPTED_ONLY"
    }
  }

  deletion_protection = true
  depends_on          = [google_project_service.apis]
}

resource "google_sql_database" "inventory" {
  name     = "inventory"
  instance = google_sql_database_instance.db.name
}

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "google_sql_user" "app" {
  name     = "inventory"
  instance = google_sql_database_instance.db.name
  password = random_password.db.result
}

# ---- Secrets ----

resource "google_secret_manager_secret" "db_password" {
  secret_id = "${local.name}-db-password"
  replication {
    auto {}
  }
  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "db_password" {
  secret      = google_secret_manager_secret.db_password.id
  secret_data = random_password.db.result
}

resource "google_secret_manager_secret" "api_keys" {
  secret_id = "${local.name}-api-keys"
  replication {
    auto {}
  }
  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "api_keys" {
  secret      = google_secret_manager_secret.api_keys.id
  secret_data = var.api_keys
}

# ---- Runtime identity: only what the service needs ----

resource "google_service_account" "run" {
  account_id   = "inventory-service"
  display_name = "Inventory service (Cloud Run)"
}

resource "google_project_iam_member" "sql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.run.email}"
}

resource "google_secret_manager_secret_iam_member" "read_db_password" {
  secret_id = google_secret_manager_secret.db_password.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.run.email}"
}

resource "google_secret_manager_secret_iam_member" "read_api_keys" {
  secret_id = google_secret_manager_secret.api_keys.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.run.email}"
}

# ---- Service ----

resource "google_cloud_run_v2_service" "api" {
  name     = local.name
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  template {
    service_account = google_service_account.run.email

    scaling {
      min_instance_count = 0
      max_instance_count = var.max_instances
    }

    containers {
      name  = "api"
      image = local.image

      ports {
        container_port = 8080
      }

      env {
        name  = "DATABASE_URL"
        value = "jdbc:postgresql://127.0.0.1:5432/${google_sql_database.inventory.name}"
      }
      env {
        name  = "DATABASE_USER"
        value = google_sql_user.app.name
      }
      env {
        name = "DATABASE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "INVENTORY_API_KEYS"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.api_keys.secret_id
            version = "latest"
          }
        }
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
      }

      startup_probe {
        http_get {
          path = "/actuator/health/readiness"
        }
        period_seconds    = 3
        failure_threshold = 40
      }

      liveness_probe {
        http_get {
          path = "/actuator/health/liveness"
        }
      }

      depends_on = ["cloud-sql-proxy"]
    }

    # Sidecar: the Cloud SQL Auth Proxy gives the app a local, IAM-authorized, encrypted connection.
    containers {
      name  = "cloud-sql-proxy"
      image = "gcr.io/cloud-sql-connectors/cloud-sql-proxy:2.14.1"
      args  = ["--port=5432", "--health-check", "--http-address=0.0.0.0", google_sql_database_instance.db.connection_name]

      startup_probe {
        http_get {
          path = "/startup"
          port = 9090
        }
        period_seconds    = 1
        failure_threshold = 30
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "256Mi"
        }
      }
    }
  }

  depends_on = [
    google_project_iam_member.sql_client,
    google_secret_manager_secret_iam_member.read_db_password,
    google_secret_manager_secret_iam_member.read_api_keys,
    google_secret_manager_secret_version.db_password,
    google_secret_manager_secret_version.api_keys,
  ]
}

# The API does its own authentication with API keys, so Cloud Run lets requests through.
resource "google_cloud_run_v2_service_iam_member" "public" {
  name     = google_cloud_run_v2_service.api.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "allUsers"
}
