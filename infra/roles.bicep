// Role assignments for the project, all at resource-group scope. NOT part of main.bicep and NOT deployed by the
// engineer or the pipeline: neither is allowed to assign roles. The subscription admin runs it once when the
// project is set up in a new subscription, for example (PowerShell, ids in variables):
//   az deployment group create --subscription $sub --resource-group rg-trebol-poc --template-file infra/roles.bicep `
//     --parameters pipelinePrincipalId=$pipelinePid appPrincipalId=$appPid engineerPrincipalId=$engineerOid
// The principal ids are deployment outputs of main.bicep (identities) and the engineer's Entra object id.
targetScope = 'resourceGroup'

@description('Principal id of the pipeline identity (id-trebol-github).')
param pipelinePrincipalId string

@description('Principal id of the app identity (id-trebol-app).')
param appPrincipalId string

@description('Object id of the engineer who manages the secrets.')
param engineerPrincipalId string

@description('Principal type of the engineer.')
@allowed([
  'User'
  'Group'
])
param engineerPrincipalType string = 'User'

// Built-in role definition ids (the same in every Azure tenant)
var roleIds = {
  contributor: 'b24988ac-6180-42a0-ab88-20f7382dd24c'
  acrPull: '7f951dda-4ed3-4680-a7ca-43fe172d538d'
  keyVaultSecretsUser: '4633458b-17de-408a-b874-0445c86b69e6'
  keyVaultSecretsOfficer: 'b86a8fe4-44ce-4948-aee5-eccb2c155cd7'
}

var assignments = [
  {
    // Pipeline: push images (included in Contributor) and deploy new revisions; never assigns roles
    principalId: pipelinePrincipalId
    principalType: 'ServicePrincipal'
    roleId: roleIds.contributor
  }
  {
    // App: pull its image from the registry
    principalId: appPrincipalId
    principalType: 'ServicePrincipal'
    roleId: roleIds.acrPull
  }
  {
    // App: read secrets (read-only)
    principalId: appPrincipalId
    principalType: 'ServicePrincipal'
    roleId: roleIds.keyVaultSecretsUser
  }
  {
    // Engineer: create and rotate secrets
    principalId: engineerPrincipalId
    principalType: engineerPrincipalType
    roleId: roleIds.keyVaultSecretsOfficer
  }
]

resource roleAssignments 'Microsoft.Authorization/roleAssignments@2022-04-01' = [for a in assignments: {
  name: guid(resourceGroup().id, a.principalId, a.roleId)
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', a.roleId)
    principalId: a.principalId
    principalType: a.principalType
  }
}]
