// The two user-assigned managed identities: one for the pipeline (deploys), one for the running app (pulls images,
// reads secrets). Their role assignments are made by the subscription admin (see infra/roles.bicep).

@description('Identity used by GitHub Actions through OIDC.')
param pipelineIdentityName string

@description('Identity used by the container app.')
param appIdentityName string

@description('Azure region.')
param location string

@description('Tags for policy compliance and cost reporting.')
param tags object

resource pipelineIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: pipelineIdentityName
  location: location
  tags: tags
}

resource appIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: appIdentityName
  location: location
  tags: tags
}

output pipelineIdentityId string = pipelineIdentity.id
output pipelinePrincipalId string = pipelineIdentity.properties.principalId
output pipelineClientId string = pipelineIdentity.properties.clientId
output appIdentityId string = appIdentity.id
output appPrincipalId string = appIdentity.properties.principalId
output appClientId string = appIdentity.properties.clientId
