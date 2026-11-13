// The Trebol API as a Container App: image pulled from the registry and secrets read from Key Vault by the app's
// managed identity, configuration through environment variables (the same ones used locally), health probes on the
// Actuator endpoints, HTTPS-only public ingress and HTTP-based autoscaling.

@description('Container App name (lowercase letters, digits and hyphens).')
param name string

@description('Azure region (the region of the Container Apps environment).')
param location string

@description('Tags for policy compliance and cost reporting.')
param tags object

@description('Resource id of the Container Apps environment.')
param environmentId string

@description('Full image reference, for example myregistry.azurecr.io/trebol-api:abc1234.')
param image string

@description('Registry login server, for example myregistry.azurecr.io.')
param registryServer string

@description('Resource id of the user-assigned identity that pulls the image and reads Key Vault.')
param identityId string

@description('Key Vault URI ending with a slash, for example https://myvault.vault.azure.net/.')
param keyVaultUri string

@description('Key Vault secret names for the database password and the JWT signing key.')
param dbPasswordSecretName string
param jwtSecretName string

@description('MySQL host name, database name and user.')
param dbHost string
param dbName string
param dbUser string

@description('CPU (string, for example "1.0") and memory (for example "2Gi") per replica; memory must be 2 x CPU.')
param cpu string
param memory string

@description('Replica limits.')
param minReplicas int
param maxReplicas int

@description('Concurrent HTTP requests per replica before another replica is added.')
param concurrentRequestsPerReplica int = 100

resource app 'Microsoft.App/containerApps@2024-03-01' = {
  name: name
  location: location
  tags: tags
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identityId}': {}
    }
  }
  properties: {
    environmentId: environmentId
    workloadProfileName: 'Consumption'
    configuration: {
      // Single: each deployment replaces the running revision. Multiple (canary releases) comes with the pipeline
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
        // HTTP requests are redirected to HTTPS
        allowInsecure: false
        traffic: [
          {
            latestRevision: true
            weight: 100
          }
        ]
      }
      registries: [
        {
          server: registryServer
          identity: identityId
        }
      ]
      // References only: Container Apps fetches the values from Key Vault with the identity (versionless URLs pick up
      // a new secret version automatically, within about 30 minutes)
      secrets: [
        {
          name: 'db-password'
          keyVaultUrl: '${keyVaultUri}secrets/${dbPasswordSecretName}'
          identity: identityId
        }
        {
          name: 'jwt-secret-key'
          keyVaultUrl: '${keyVaultUri}secrets/${jwtSecretName}'
          identity: identityId
        }
      ]
    }
    template: {
      // Longer than the application's graceful shutdown (30 s), so in-flight requests can finish
      terminationGracePeriodSeconds: 40
      containers: [
        {
          name: 'trebol-api'
          image: image
          resources: {
            cpu: json(cpu)
            memory: memory
          }
          env: [
            {
              name: 'SPRING_PROFILES_ACTIVE'
              value: 'azure'
            }
            {
              // Azure Database for MySQL accepts encrypted connections only
              name: 'SPRING_DATASOURCE_URL'
              value: 'jdbc:mysql://${dbHost}:3306/${dbName}?sslMode=REQUIRED'
            }
            {
              name: 'SPRING_DATASOURCE_USERNAME'
              value: dbUser
            }
            {
              name: 'SPRING_DATASOURCE_PASSWORD'
              secretRef: 'db-password'
            }
            {
              name: 'TREBOL_SECURITY_JWTSECRETKEY'
              secretRef: 'jwt-secret-key'
            }
          ]
          probes: [
            {
              // Startup takes about 42 s on 1 vCPU: allow up to 30 + 10 x 10 = 130 s before giving up
              type: 'Startup'
              httpGet: {
                path: '/actuator/health/liveness'
                port: 8080
              }
              initialDelaySeconds: 30
              periodSeconds: 10
              timeoutSeconds: 3
              failureThreshold: 10
            }
            {
              // No database check here: a database outage must not restart the containers
              type: 'Liveness'
              httpGet: {
                path: '/actuator/health/liveness'
                port: 8080
              }
              periodSeconds: 10
              timeoutSeconds: 3
              failureThreshold: 3
            }
            {
              // Includes the database: a replica that cannot reach MySQL gets no traffic
              type: 'Readiness'
              httpGet: {
                path: '/actuator/health/readiness'
                port: 8080
              }
              periodSeconds: 5
              timeoutSeconds: 3
              failureThreshold: 3
            }
          ]
        }
      ]
      scale: {
        minReplicas: minReplicas
        maxReplicas: maxReplicas
        rules: [
          {
            name: 'http-concurrency'
            http: {
              metadata: {
                concurrentRequests: string(concurrentRequestsPerReplica)
              }
            }
          }
        ]
      }
    }
  }
}

output id string = app.id
output fqdn string = app.properties.configuration.ingress.fqdn
output latestRevisionName string = app.properties.latestRevisionName
