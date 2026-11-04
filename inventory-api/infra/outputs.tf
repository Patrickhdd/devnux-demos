output "service_url" {
  description = "Base URL of the deployed service."
  value       = google_cloud_run_v2_service.api.uri
}

output "image_repository" {
  description = "Push images here, tagged with the value you pass as image_tag."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}/${local.name}"
}

output "database_connection_name" {
  value = google_sql_database_instance.db.connection_name
}
