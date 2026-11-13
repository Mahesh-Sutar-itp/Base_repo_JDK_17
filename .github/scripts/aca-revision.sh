#!/usr/bin/env bash
# Helpers for the Container App's revisions (multiple-revision mode). Sourced by the deploy and schedule workflows.
# Needs an Azure CLI login and the variables RESOURCE_GROUP and APP_NAME.
#
# A revision is an immutable version of the app (image + settings). Changing the image or the scale settings creates
# a new revision. Traffic is then moved to it explicitly, and the old revision is deactivated (no replicas, no cost).
# Traffic is always written as an exact list, so no stale entries for old revisions are left behind.

ACA_API_VERSION=2024-03-01

# Revision that serves the traffic now (highest weight); if the traffic follows "latest", the latest revision
aca_current_revision() {
  local name
  # The backticks are a JMESPath number literal, not shell command substitution
  # shellcheck disable=SC2016
  name=$(az containerapp ingress traffic show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
    --query 'max_by([?weight > `0`], &weight).revisionName' --output tsv)
  if [ -z "$name" ] || [ "$name" = "None" ]; then
    name=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
      --query properties.latestRevisionName --output tsv)
  fi
  printf '%s\n' "$name"
}

# One field of a revision, for example: aca_revision_field trebol-api--abc properties.fqdn
aca_revision_field() {
  az containerapp revision show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" --revision "$1" \
    --query "$2" --output tsv
}

# Waits (bounded) until the app's last change has finished
aca_wait_app() {
  local deadline state
  deadline=$(( $(date +%s) + ${1:-300} ))
  sleep 5
  while :; do
    state=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" \
      --query properties.provisioningState --output tsv)
    case "$state" in
      Succeeded) return 0 ;;
      Failed|Canceled) echo "app provisioning state: $state" >&2; return 1 ;;
    esac
    if [ "$(date +%s)" -ge "$deadline" ]; then echo "timeout waiting for the app (state: $state)" >&2; return 1; fi
    sleep 10
  done
}

# Exact traffic split, replacing the whole list. Example: aca_set_traffic trebol-api--a=90 trebol-api--b=10
aca_set_traffic() {
  local id pair entries=""
  id=$(az containerapp show --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" --query id --output tsv)
  for pair in "$@"; do
    entries="${entries:+$entries,}{\"revisionName\":\"${pair%%=*}\",\"weight\":${pair##*=}}"
  done
  az rest --method patch --url "https://management.azure.com${id}?api-version=${ACA_API_VERSION}" \
    --body "{\"properties\":{\"configuration\":{\"ingress\":{\"traffic\":[${entries}]}}}}" --output none
  aca_wait_app 300
  echo "traffic: $*"
}

# New revision from the app's current template with another image and/or minimum replica count
aca_create_revision() {  # image suffix [min-replicas]
  local args=(--resource-group "$RESOURCE_GROUP" --name "$APP_NAME" --image "$1" --revision-suffix "$2" --output none)
  if [ -n "${3:-}" ]; then args+=(--min-replicas "$3"); fi
  az containerapp update "${args[@]}"
  echo "created: ${APP_NAME}--$2"
}

# Waits (bounded) until a revision is provisioned and, when it should run replicas, healthy (readiness passed)
aca_wait_revision() {  # name [timeout-seconds] [healthy|provisioned]
  local rev=$1 expect=${3:-healthy} deadline state prov health running
  deadline=$(( $(date +%s) + ${2:-600} ))
  while :; do
    state=$(aca_revision_field "$rev" \
      "join(' ', [to_string(properties.provisioningState), to_string(properties.healthState), to_string(properties.runningState)])")
    read -r prov health running <<<"$state"
    echo "  $rev: provisioning=$prov health=$health running=$running"
    if [ "$prov" = "Failed" ] || [ "$running" = "Failed" ]; then return 1; fi
    if [ "$prov" = "Provisioned" ]; then
      if [ "$expect" = "provisioned" ] || [ "$health" = "Healthy" ]; then return 0; fi
    fi
    if [ "$(date +%s)" -ge "$deadline" ]; then echo "timeout waiting for $rev" >&2; return 1; fi
    sleep 15
  done
}

aca_deactivate() {
  az containerapp revision deactivate --resource-group "$RESOURCE_GROUP" --name "$APP_NAME" --revision "$1" --output none
  echo "deactivated: $1"
}

# Moves the app to a new revision with the same image and another minimum replica count (used by the schedule).
# Nothing happens when the serving revision already has that minimum.
aca_set_min_replicas() {  # min-replicas suffix
  local old image min new
  old=$(aca_current_revision)
  min=$(aca_revision_field "$old" properties.template.scale.minReplicas)
  if [ -z "$min" ] || [ "$min" = "None" ]; then min=0; fi
  if [ "$min" = "$1" ]; then echo "$old already has min replicas $1"; return 0; fi
  image=$(aca_revision_field "$old" 'properties.template.containers[0].image')
  new="${APP_NAME}--$2"
  aca_set_traffic "$old=100"
  aca_create_revision "$image" "$2" "$1"
  local expect=healthy
  if [ "$1" -eq 0 ]; then expect=provisioned; fi
  if ! aca_wait_revision "$new" 600 "$expect"; then
    aca_deactivate "$new" || true
    echo "the new revision did not become ready; traffic stays on $old" >&2
    return 1
  fi
  aca_set_traffic "$new=100"
  aca_deactivate "$old"
}
