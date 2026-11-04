# Deploying to Google Cloud

Terraform for the service on Cloud Run, with PostgreSQL on Cloud SQL, the image in Artifact Registry and secrets in Secret Manager (research.md R9). The app connects through a Cloud SQL Auth Proxy sidecar, so it needs no Google-specific code and runs the same on AlloyDB or anywhere else.

```bash
cd infra
terraform init
terraform apply -var project_id=YOUR_PROJECT -target=google_artifact_registry_repository.images

# Build and push the image
REPO=$(terraform output -raw image_repository)
gcloud auth configure-docker us-central1-docker.pkg.dev
docker build -t $REPO:v1 .. && docker push $REPO:v1

# Deploy everything. api_keys is sensitive: pass it from your shell, never commit it.
terraform apply -var project_id=YOUR_PROJECT -var image_tag=v1 -var "api_keys=storefront:$(openssl rand -hex 24):read+write"
curl "$(terraform output -raw service_url)/actuator/health"
```

Cost at rest is mostly the Cloud SQL instance (db-f1-micro, about $10 a month). Cloud Run scales to zero.

CI runs `terraform fmt -check` and `terraform validate` on every push. It does not deploy.
